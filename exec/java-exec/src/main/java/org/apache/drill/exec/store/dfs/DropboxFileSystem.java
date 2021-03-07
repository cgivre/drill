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

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.FolderMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.util.Progressable;
import org.apache.parquet.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class DropboxFileSystem extends FileSystem {
  private static final Logger logger = LoggerFactory.getLogger(DropboxFileSystem.class);
  private static final String ACCESS_TOKEN = "sl" +
    ".AsNCj1AGxUJd8yY8iKf_NULLf7RM4h1GX1bBiRc_t5QjFhlHqIiKRzsH15Zcu9DRBVQ9S0B7eoArqeRGpD3Z_e1TCbJRipQo7CPlnn311iQAbFTZUgGnTIt2hV4pvVx1c6hhBIo";


  private static final String ERROR_MSG = "Dropbox is read only.";
  private Path workingDirectory;


  @Override
  public URI getUri() {
    try {
      return new URI("dropbox:///");
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public FSDataInputStream open(Path path, int bufferSize) throws IOException {
    FSDataInputStream fis;
    String file = getFileName(path);
    DbxClientV2 client = getClient();
    FileOutputStream outputStream = null;

    try {
      outputStream = new FileOutputStream(outputFile);
      client.files().download(file).download(outputStream);
      FileChannel outputChannel = outputStream.getChannel();


      fis = new FSDataInputStream(inputFile);
      FileChannel inputChannel = fis.getChannel();

      //Transfer from output stream to input stream is happening here
      outputChannel.transferTo(0, inputChannel.size(), inputChannel);

    } catch (DbxException | IOException ex) {
      ex.printStackTrace();
    } finally {
      IOUtils.closeQuietly(outputStream);
    }

    return FSDataInputStream;
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
    return false;
  }

  @Override
  public boolean delete(Path f, boolean recursive) throws IOException {
    throw new IOException(ERROR_MSG);
  }

  @Override
  public FileStatus[] listStatus(Path path) throws FileNotFoundException, IOException {
    DbxClientV2 client = getClient();
    // Get files and folder metadata from Dropbox root directory
    List<FileStatus> fileStatusList = new ArrayList<>();

    try {
      ListFolderResult result = client.files().listFolder("");
      while (true) {
        for (Metadata metadata : result.getEntries()) {

          System.out.println(metadata.getPathLower());
        }

        if (!result.getHasMore()) {
          break;
        }

        result = client.files().listFolderContinue(result.getCursor());
      }
    } catch (Exception e) {

    }

    return (FileStatus[]) fileStatusList.toArray();
  }

  @Override
  public void setWorkingDirectory(Path new_dir) {

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
    String filePath  = Path.getPathWithoutSchemeAndAuthority(path).toString();

    // Remove trailing slash
    if ((!filePath.isEmpty()) && filePath.endsWith("/")) {
      filePath = filePath.substring(0, filePath.length() -1);
    }

    logger.debug("Getting metadata for file at {}", filePath);
    DbxClientV2 client = getClient();

    boolean isDirectory;
    try {
      Metadata metadata = client.files().getMetadata("");
      isDirectory = isDirectory(metadata);
      if (isDirectory) {
        // TODO Get size and mod date of directories
        return new FileStatus(0, true, 1, 0, 0, path);
      } else {
        FileMetadata fileMetadata = (FileMetadata) metadata;
        return new FileStatus(fileMetadata.getSize(), false, 1, 0, fileMetadata.getClientModified().getTime(), path);
      }
    } catch (Exception e) {
      throw new IOException("Error accessing file " + filePath);
    }
  }

  private DbxClientV2 getClient() {
    DbxRequestConfig config = DbxRequestConfig.newBuilder("datadistillr").build();
    return new DbxClientV2(config, ACCESS_TOKEN);
  }

  private boolean isDirectory(Metadata metadata) {
    return metadata instanceof FolderMetadata;
  }

  private boolean isFile(Metadata metadata) {
    return metadata instanceof FileMetadata;
  }

  private String getFileName(Path path){
    String file = path.toUri().getPath();
    if(file.charAt(0) == '/'){
      file = file.substring(1);
    }
    return file;
  }
}
