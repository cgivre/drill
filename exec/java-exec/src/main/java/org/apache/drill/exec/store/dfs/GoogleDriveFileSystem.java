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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.apache.drill.common.exceptions.UserException;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.util.Progressable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GoogleDriveFileSystem extends FileSystem {

  private static final Logger logger = LoggerFactory.getLogger(GoogleDriveFileSystem.class);
  private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
  private static final String APPLICATION_NAME = "Apache Drill";
  private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);

  private Path workingDirectory;
  private Drive service;

  @Override
  public URI getUri() {
    try {
      return new URI("googledrive:///");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public FSDataInputStream open(Path f, int bufferSize) {
    FSDataInputStream fsDataInputStream;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      service = getGoogleDriveService();
      String fileID = getFileID(f.toString());

      service.files()
        .get(fileID)
        .executeMediaAndDownloadTo(out);
      fsDataInputStream = new FSDataInputStream(new SeekableByteArrayInputStream(out.toByteArray()));

    } catch (GeneralSecurityException | IOException e) {
      throw UserException.connectionError()
        .message("Unable to connect to Google Drive")
        .addContext(e.getMessage())
        .build(logger);
    }
    return fsDataInputStream;
  }

  /**
   * Returns the Google File ID for a given file.  Note that Google allows you
   * to have multiple files with the same name, so this will return the first match.
   * @param fileName The file name
   * @return A string containing the fileID
   * @throws IOException If the file cannot be found, or the service is null, throw IOException
   */
  private String getFileID(String fileName) throws IOException {
    String pageToken = null;
    try {
      service = getGoogleDriveService();
    } catch (GeneralSecurityException e) {
      throw UserException.connectionError()
        .message("Could not connect to Google Drive service.")
        .build(logger);
    }

    FileList result = service.files().list()
      .setQ("name='" + fileName + "'")
      .setSpaces("drive")
      .setFields("nextPageToken, files(id, name)")
      .setPageToken(pageToken)
      .execute();

    if (result.isEmpty()) {
      throw UserException.dataReadError()
        .message("Could not find file " + fileName + " in Google Drive.")
        .build(logger);
    } else if (result.size() > 1) {
      logger.info("Multiple files found with name: {}.  Returning the first match.", fileName);
    }
    List<File> files = result.getFiles();

    // For a directory, the size will be null
    if (files.size() == 0) {
      return null;
    } else {
      File file = files.get(0);
      return file.getId();
    }
  }

  @Override
  public FSDataOutputStream create(Path f, FsPermission permission, boolean overwrite, int bufferSize, short replication, long blockSize, Progressable progress) throws IOException {
    return null;
  }

  @Override
  public FSDataOutputStream append(Path f, int bufferSize, Progressable progress) throws IOException {
    throw new IOException("Drill does not support appending to Google Drive.");
  }

  private Drive getGoogleDriveService() throws GeneralSecurityException, IOException {
    if (service != null) {
      return service;
    }
    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
      .setApplicationName(APPLICATION_NAME)
      .build();
  }

  private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
    // Load client secrets.
    String creds = "{\"installed\":\n" + "  {\"client_id\":\"863751389085-l6086vnffcv2d3vampcfn46evehtmsel.apps.googleusercontent.com\",\n" + "    \"project_id\":\"winged" +
      "-acolyte-231015\",\n" + "    \"auth_uri\":\"https://accounts.google.com/o/oauth2/auth\",\n" + "    \"token_uri\":\"https://oauth2.googleapis.com/token\",\n" + "    \"auth_provider_x509_cert_url\":\"https://www.googleapis.com/oauth2/v1/certs\",\n" + "    \"client_secret\":\"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\",\n" + "    \"redirect_uris\":[\"http://localhost\"]\n" + "    }\n" + "  }";
    InputStream inputStream = new ByteArrayInputStream(creds.getBytes(StandardCharsets.UTF_8));

    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(inputStream));


    // Build flow and trigger user authorization request.
    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
      HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
      .setDataStoreFactory(new FileDataStoreFactory(new java.io.File("/tmp/drill")))
      .setAccessType("offline")
      .build();
    LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
    Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    //returns an authorized Credential object.
    return credential;
  }

  @Override
  public boolean rename(Path src, Path dst) throws IOException {
    return false;
  }

  @Override
  public boolean delete(Path f, boolean recursive) throws IOException {
    return false;
  }

  @Override
  public FileStatus[] listStatus(Path f) throws FileNotFoundException, IOException {
    String pageToken = null;
    FileList result = service.files().list()
      .setQ("'root' in parents and mimeType != 'application/vnd.google-apps.folder' and trashed = false")
      .setSpaces("drive")
      .setFields("nextPageToken, files(id, name, parents)")
      .setPageToken(pageToken)
      .execute();

    List<FileStatus> fileList = new ArrayList<>();
    return (FileStatus[]) fileList.toArray();
  }

  @Override
  public void setWorkingDirectory(Path new_dir) {
    workingDirectory = new_dir;
  }

  @Override
  public Path getWorkingDirectory() {
    return workingDirectory;
  }

  @Override
  public boolean mkdirs(Path f, FsPermission permission) throws IOException {
    return false;
  }

  @Override
  public FileStatus getFileStatus(Path path) throws IOException {
    String filePath  = Path.getPathWithoutSchemeAndAuthority(path).toString();
    String filePathString = path.toString();
    String fileID = getFileID(filePathString);

    if (filePath.equalsIgnoreCase("/")) {
      return new FileStatus(0, true, 1, 0, 0, new Path("/"));
    }

    File data = service.files().get("fileId=" + fileID + ", fields='*'").execute();
    return new FileStatus();
  }
}
