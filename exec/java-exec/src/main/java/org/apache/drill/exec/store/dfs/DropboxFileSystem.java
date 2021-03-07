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
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PositionedReadable;
import org.apache.hadoop.fs.Seekable;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.util.Progressable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class DropboxFileSystem extends FileSystem {
  private static final Logger logger = LoggerFactory.getLogger(DropboxFileSystem.class);

  // TODO Get this from the config or password vault
  private static final String ACCESS_TOKEN = "e9aB6wxgt6kAAAAAAAAAAayiv0u56eRpMeioVAiHIunhH2SuJoadXFxMKSjlZVTk";


  private static final String ERROR_MSG = "Dropbox is read only.";
  private Path workingDirectory;
  private DbxClientV2 client;
  private FileStatus[] fileStatuses;


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
    FSDataInputStream fsDataInputStream = null;
    String filename = getFileName(path);
    client = getClient();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      client.files().download(filename).download(out);
      fsDataInputStream = new FSDataInputStream(new SeekableByteArrayInputStream(out.toByteArray()));
    } catch (DbxException e) {
      throw new IOException(e.getMessage());
    }
    return fsDataInputStream;
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
    client = getClient();
    List<FileStatus> fileStatusList = new ArrayList<>();

    // TODO Cache this so that we don't have to do this multiple times.

    // Get files and folder metadata from Dropbox root directory
    try {
      ListFolderResult result = client.files().listFolder("");
      while (true) {
        for (Metadata metadata : result.getEntries()) {
          fileStatusList.add(getFileInformation(metadata));
        }
        if (!result.getHasMore()) {
          break;
        }
        result = client.files().listFolderContinue(result.getCursor());
      }
    } catch (DbxException e) {
      throw new IOException(e.getMessage());
    }

    // Convert to Array
    fileStatuses = new FileStatus[fileStatusList.size()];
    for (int i = 0; i < fileStatusList.size(); i++) {
      fileStatuses[i] = fileStatusList.get(i);
    }

    return fileStatuses;
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

    logger.debug("Getting metadata for file at {}", filePath);
    client = getClient();
    boolean isDirectory;
    try {
      ListFolderResult listFolder = client.files().listFolder("");
      Metadata metadata = client.files().getMetadata("/http-pcap.json");

      isDirectory = isDirectory(metadata);
      if (isDirectory) {
        // TODO Get size and mod date of directories
        return new FileStatus(0, true, 1, 0, 0, path);
      } else {
        FileMetadata fileMetadata = (FileMetadata) metadata;
        return new FileStatus(fileMetadata.getSize(), false, 1, 0, fileMetadata.getClientModified().getTime(), path);
      }
    } catch (Exception e) {
      throw new IOException("Error accessing file " + path.getName());
    }
  }

  private FileStatus getFileInformation(Metadata metadata) {
    if (isDirectory(metadata)) {
      // TODO Get size and mod date of directories
      return new FileStatus(0, true, 1, 0, 0, new Path(metadata.getPathLower()));
    } else {
      FileMetadata fileMetadata = (FileMetadata) metadata;
      return new FileStatus(fileMetadata.getSize(), false, 1, 0, fileMetadata.getClientModified().getTime(), new Path(metadata.getPathLower()));
    }
  }

  private DbxClientV2 getClient() {
    if (this.client != null) {
      return client;
    }

    DbxRequestConfig config = DbxRequestConfig.newBuilder("datadistillr").build();
    this.client = new DbxClientV2(config, ACCESS_TOKEN);
    return this.client;
  }

  private boolean isDirectory(Metadata metadata) {
    return metadata instanceof FolderMetadata;
  }

  private boolean isFile(Metadata metadata) {
    return metadata instanceof FileMetadata;
  }

  private String getFileName(Path path){
    return path.toUri().getPath();
  }

  static class SeekableByteArrayInputStream extends ByteArrayInputStream implements Seekable, PositionedReadable {

    public SeekableByteArrayInputStream(byte[] buf)
    {
      super(buf);
    }
    @Override
    public long getPos() throws IOException{
      return pos;
    }

    @Override
    public void seek(long pos) throws IOException {
      if (mark != 0)
        throw new IllegalStateException();

      reset();
      long skipped = skip(pos);

      if (skipped != pos)
        throw new IOException();
    }

    @Override
    public boolean seekToNewSource(long targetPos) throws IOException {
      return false;
    }

    @Override
    public int read(long position, byte[] buffer, int offset, int length) throws IOException {

      if (position >= buf.length)
        throw new IllegalArgumentException();
      if (position + length > buf.length)
        throw new IllegalArgumentException();
      if (length > buffer.length)
        throw new IllegalArgumentException();

      System.arraycopy(buf, (int) position, buffer, offset, length);
      return length;
    }

    @Override
    public void readFully(long position, byte[] buffer) throws IOException {
      read(position, buffer, 0, buffer.length);

    }

    @Override
    public void readFully(long position, byte[] buffer, int offset, int length) throws IOException {
      read(position, buffer, offset, length);
    }
  }
}
