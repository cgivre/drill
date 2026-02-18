/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.drill.exec.store.dfs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.apache.drill.common.exceptions.UserException;
import org.apache.drill.common.logical.security.CredentialsProvider;
import org.apache.drill.exec.oauth.PersistentTokenTable;
import org.apache.drill.exec.store.security.oauth.OAuthTokenCredentials;
import org.apache.drill.exec.vector.complex.fn.SeekableBAIS;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.util.Progressable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SharePointFileSystem extends OAuthEnabledFileSystem {
  private static final Logger logger = LoggerFactory.getLogger(SharePointFileSystem.class);

  private static final String ERROR_MSG = "SharePoint is read only.";
  private static final String GRAPH_API_URL = "https://graph.microsoft.com/v1.0";

  private Path workingDirectory;
  private OkHttpClient httpClient;
  private String accessToken;
  private final Map<String, String> siteNameToIdCache = new HashMap<>();
  private final Map<String, FileStatus> fileStatusCache = new HashMap<>();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public URI getUri() {
    try {
      return new URI("sharepoint:///");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public FSDataInputStream open(Path path, int bufferSize) throws IOException {
    String pathStr = Path.getPathWithoutSchemeAndAuthority(path).toString();
    ensureClient();

    // Check if this is a SharePoint List path
    if (isListPath(pathStr)) {
      byte[] listDataJson = fetchSharePointListAsJson(pathStr);
      return new FSDataInputStream(new SeekableBAIS(listDataJson));
    }

    // Otherwise, fetch as a file
    byte[] fileContent = downloadFileContent(pathStr);
    updateTokens();
    return new FSDataInputStream(new SeekableBAIS(fileContent));
  }

  @Override
  public FSDataOutputStream create(Path f,
                                   FsPermission permission,
                                   boolean overwrite,
                                   int bufferSize,
                                   short replication,
                                   long blockSize,
                                   Progressable progress) throws IOException {
    throw new IOException(ERROR_MSG);
  }

  @Override
  public FSDataOutputStream append(Path f, int bufferSize, Progressable progress) throws IOException {
    throw new IOException(ERROR_MSG);
  }

  @Override
  public boolean rename(Path src, Path dst) throws IOException {
    throw new IOException(ERROR_MSG);
  }

  @Override
  public boolean delete(Path f, boolean recursive) throws IOException {
    throw new IOException(ERROR_MSG);
  }

  @Override
  public FileStatus[] listStatus(Path path) throws IOException {
    String pathStr = Path.getPathWithoutSchemeAndAuthority(path).toString();
    if (pathStr == null || pathStr.isEmpty() || pathStr.equals("/")) {
      pathStr = "/";
    }

    ensureClient();
    List<FileStatus> fileStatusList = new ArrayList<>();

    try {
      // For now, return OneDrive root directory listing
      String url = GRAPH_API_URL + "/me/drive/root/children";
      JsonNode response = makeGraphApiRequest(url);

      if (response.has("value")) {
        for (JsonNode item : response.get("value")) {
          fileStatusList.add(jsonNodeToFileStatus(item, pathStr));
        }
      }

      updateTokens();
    } catch (Exception e) {
      throw new IOException("Error listing files in " + pathStr + ": " + e.getMessage(), e);
    }

    FileStatus[] result = new FileStatus[fileStatusList.size()];
    for (int i = 0; i < fileStatusList.size(); i++) {
      result[i] = fileStatusList.get(i);
    }
    return result;
  }

  @Override
  public void setWorkingDirectory(Path new_dir) {
    logger.debug("Setting working directory to: " + new_dir.getName());
    workingDirectory = new_dir;
  }

  @Override
  public Path getWorkingDirectory() {
    return workingDirectory;
  }

  @Override
  public boolean mkdirs(Path f, FsPermission permission) throws IOException {
    throw new IOException(ERROR_MSG);
  }

  @Override
  public FileStatus getFileStatus(Path path) throws IOException {
    String pathStr = Path.getPathWithoutSchemeAndAuthority(path).toString();

    if (pathStr.equals("/")) {
      return new FileStatus(0, true, 1, 0, 0, new Path("/"));
    }

    ensureClient();

    try {
      // Check if this is a SharePoint List path (virtual file)
      if (isListPath(pathStr)) {
        return createVirtualListFileStatus(pathStr);
      }

      // Get file metadata from OneDrive
      String url = GRAPH_API_URL + "/me/drive/root:" + pathStr + ":/";
      JsonNode item = makeGraphApiRequest(url);
      updateTokens();
      return jsonNodeToFileStatus(item, pathStr);
    } catch (Exception e) {
      throw new IOException("Error accessing file " + pathStr + ": " + e.getMessage(), e);
    }
  }

  private void ensureClient() {
    if (httpClient == null) {
      httpClient = new OkHttpClient();
      accessToken = getAccessToken();
    }
  }

  private String getAccessToken() {
    // First, try to read static access token from config
    String staticToken = this.getConf().get("sharepointAccessToken", "");
    if (StringUtils.isNotEmpty(staticToken)) {
      logger.info("Using static access token from configuration");
      return staticToken;
    }

    // Otherwise, use OAuth 2.0
    logger.info("Using OAuth 2.0 for authentication");
    CredentialsProvider credentialsProvider = getCredentialsProvider();
    PersistentTokenTable tokenTable = getTokenTable();
    OAuthTokenCredentials credentials = new OAuthTokenCredentials.Builder()
      .setCredentialsProvider(credentialsProvider)
      .setTokenTable(tokenTable)
      .build()
      .get();

    if (credentials == null || StringUtils.isEmpty(credentials.getAccessToken())) {
      throw UserException.connectionError()
        .message("No access token found in configuration or credentials provider")
        .build(logger);
    }

    return credentials.getAccessToken();
  }

  private void updateTokens() {
    if (StringUtils.isEmpty(accessToken)) {
      return;
    }

    try {
      PersistentTokenTable tokenTable = getTokenTable();
      if (tokenTable != null) {
        tokenTable.setAccessToken(accessToken);
      }
    } catch (Exception e) {
      logger.warn("Error updating tokens: " + e.getMessage());
    }
  }

  private JsonNode makeGraphApiRequest(String url) throws IOException {
    Request request = new Request.Builder()
      .url(url)
      .addHeader("Authorization", "Bearer " + accessToken)
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Graph API request failed: " + response.code() + " " + response.message());
      }

      String responseBody = response.body().string();
      return objectMapper.readTree(responseBody);
    }
  }

  private byte[] downloadFileContent(String pathStr) throws IOException {
    String url = GRAPH_API_URL + "/me/drive/root:" + pathStr + ":/content";

    Request request = new Request.Builder()
      .url(url)
      .addHeader("Authorization", "Bearer " + accessToken)
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        throw new IOException("Download failed: " + response.code() + " " + response.message());
      }

      return response.body().bytes();
    }
  }

  private byte[] fetchSharePointListAsJson(String pathStr) throws IOException {
    try {
      // Parse path like "/sites/MySite/lists/MyList"
      String[] parts = pathStr.split("/");
      if (parts.length < 5 || !"sites".equals(parts[1]) || !"lists".equals(parts[3])) {
        throw new IOException("Invalid SharePoint list path: " + pathStr);
      }

      String siteName = parts[2];
      String listName = parts[4];

      String siteId = resolveSiteNameToId(siteName);

      // Get list by display name - search through lists
      String listsUrl = GRAPH_API_URL + "/sites/" + siteId + "/lists?$search=\"" + listName + "\"";
      JsonNode listsResponse = makeGraphApiRequest(listsUrl);

      String listId = null;
      if (listsResponse.has("value")) {
        for (JsonNode list : listsResponse.get("value")) {
          if (listName.equals(list.get("displayName").asText())) {
            listId = list.get("id").asText();
            break;
          }
        }
      }

      if (listId == null) {
        throw new IOException("SharePoint list not found: " + listName);
      }

      // Get all list items
      String itemsUrl = GRAPH_API_URL + "/sites/" + siteId + "/lists/" + listId + "/items?$expand=fields";
      JsonNode itemsResponse = makeGraphApiRequest(itemsUrl);

      // Serialize items to JSON
      List<Map<String, Object>> itemsData = new ArrayList<>();
      if (itemsResponse.has("value")) {
        for (JsonNode item : itemsResponse.get("value")) {
          Map<String, Object> itemMap = new HashMap<>();
          itemMap.put("id", item.get("id").asText());
          if (item.has("fields")) {
            // Convert fields to map for JSON serialization
            JsonNode fields = item.get("fields");
            Map<String, Object> fieldsMap = objectMapper.convertValue(fields,
              com.fasterxml.jackson.databind.type.TypeFactory.defaultInstance()
                .constructMapType(HashMap.class, String.class, Object.class));
            itemMap.put("fields", fieldsMap);
          }
          itemsData.add(itemMap);
        }
      }

      return objectMapper.writeValueAsBytes(itemsData);
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException("Error fetching SharePoint list as JSON: " + e.getMessage(), e);
    }
  }

  /**
   * Resolve a SharePoint site name to its site ID.
   * Supports:
   * - Display name (e.g., "Sales Team")
   * - Site ID with tenant (e.g., "tenantId,siteId")
   * - URL-encoded site name (e.g., "Sales%20Team")
   */
  private String resolveSiteNameToId(String siteName) throws IOException {
    if (siteNameToIdCache.containsKey(siteName)) {
      return siteNameToIdCache.get(siteName);
    }

    try {
      // Check if already a site ID (contains comma for tenantId,siteId format)
      if (siteName.contains(",")) {
        logger.debug("Using site ID directly: {}", siteName);
        return siteName;
      }

      // Try search by display name
      String searchUrl = GRAPH_API_URL + "/sites?search=" +
        java.net.URLEncoder.encode(siteName, "UTF-8");
      JsonNode response = makeGraphApiRequest(searchUrl);

      if (response.has("value") && response.get("value").size() > 0) {
        JsonNode firstSite = response.get("value").get(0);
        if (firstSite.has("id")) {
          String siteId = firstSite.get("id").asText();
          siteNameToIdCache.put(siteName, siteId);
          logger.info("Resolved site '{}' to ID: {}", siteName, siteId);
          return siteId;
        }
      }

      // If search fails, try direct URL-based lookup
      String urlBasedSiteId = resolveSiteByUrl(siteName);
      if (urlBasedSiteId != null) {
        siteNameToIdCache.put(siteName, urlBasedSiteId);
        return urlBasedSiteId;
      }

      throw new IOException("SharePoint site not found: " + siteName);
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException("Error resolving site name '" + siteName + "': " + e.getMessage(), e);
    }
  }

  /**
   * Try to resolve a site by direct URL pattern.
   * Handles URL-encoded names like "Sales%20Team"
   */
  private String resolveSiteByUrl(String siteName) throws IOException {
    try {
      // Try direct /sites/{name} endpoint
      String siteUrl = GRAPH_API_URL + "/sites/" + siteName;
      JsonNode site = makeGraphApiRequest(siteUrl);

      if (site.has("id")) {
        String siteId = site.get("id").asText();
        logger.debug("Resolved site by direct URL: {} -> {}", siteName, siteId);
        return siteId;
      }
    } catch (IOException e) {
      logger.debug("Site not found by direct URL '{}'", siteName);
    }

    return null;
  }

  /**
   * List available SharePoint sites (for discovery).
   * This can be used to browse available sites.
   */
  private List<Map<String, String>> listSharePointSites() throws IOException {
    List<Map<String, String>> sites = new ArrayList<>();
    try {
      String sitesUrl = GRAPH_API_URL + "/sites?$top=200";
      JsonNode response = makeGraphApiRequest(sitesUrl);

      if (response.has("value")) {
        for (JsonNode site : response.get("value")) {
          Map<String, String> siteInfo = new HashMap<>();
          siteInfo.put("displayName", site.get("displayName").asText());
          siteInfo.put("id", site.get("id").asText());
          if (site.has("webUrl")) {
            siteInfo.put("webUrl", site.get("webUrl").asText());
          }
          sites.add(siteInfo);
        }
      }
    } catch (Exception e) {
      logger.warn("Error listing SharePoint sites: {}", e.getMessage());
    }

    return sites;
  }

  private FileStatus jsonNodeToFileStatus(JsonNode item, String basePath) {
    String name = item.has("name") ? item.get("name").asText() : "unknown";
    boolean isDir = item.has("folder");
    long size = item.has("size") ? item.get("size").asLong() : 0;
    long modTime = 0;

    if (item.has("lastModifiedDateTime")) {
      String lastMod = item.get("lastModifiedDateTime").asText();
      try {
        // Parse ISO 8601 timestamp (e.g., "2023-10-15T14:30:00Z")
        ZonedDateTime zdt = ZonedDateTime.parse(lastMod, DateTimeFormatter.ISO_DATE_TIME);
        modTime = zdt.toInstant().toEpochMilli();
      } catch (Exception e) {
        logger.debug("Could not parse timestamp: " + lastMod);
      }
    }

    String fullPath = basePath + (basePath.endsWith("/") ? "" : "/") + name;
    if (basePath.equals("/")) {
      fullPath = "/" + name;
    }

    return new FileStatus(size, isDir, 1, 0, modTime, new Path(fullPath));
  }

  private boolean isListPath(String pathStr) {
    return pathStr.contains("/lists/");
  }

  private FileStatus createVirtualListFileStatus(String pathStr) {
    // Virtual JSON file for a SharePoint list
    return new FileStatus(0, false, 1, 0, 0, new Path(pathStr));
  }
}
