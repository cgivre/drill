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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    // This is a simplified implementation for list fetching
    // In a full implementation, this would fetch list items from SharePoint Lists API
    // For now, return an empty JSON array
    logger.warn("SharePoint List functionality is not fully implemented");
    return "[]".getBytes(StandardCharsets.UTF_8);
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
