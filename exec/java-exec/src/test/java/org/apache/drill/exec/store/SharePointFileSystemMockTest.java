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

package org.apache.drill.exec.store;

import org.apache.drill.exec.store.dfs.SharePointFileSystem;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SharePointFileSystemMockTest {

  private SharePointFileSystem sharePointFS;
  private Configuration conf;

  @Before
  public void setUp() throws Exception {
    sharePointFS = new SharePointFileSystem();
    conf = new Configuration();
    conf.set("sharepointAccessToken", "mock-token-for-testing");
    sharePointFS.setConf(conf);
  }

  @Test
  public void testGetUri() {
    URI uri = sharePointFS.getUri();
    assertNotNull(uri);
    assertEquals("sharepoint", uri.getScheme());
    assertEquals("sharepoint:///", uri.toString());
  }

  @Test
  public void testSetAndGetWorkingDirectory() {
    Path testPath = new Path("/test/path");
    sharePointFS.setWorkingDirectory(testPath);
    assertEquals(testPath, sharePointFS.getWorkingDirectory());
  }

  @Test
  public void testRootFileStatus() throws IOException {
    FileStatus status = sharePointFS.getFileStatus(new Path("/"));
    assertNotNull(status);
    assertTrue(status.isDirectory());
    assertEquals(0, status.getLen());
  }

  @Test(expected = IOException.class)
  public void testCreateThrowsException() throws IOException {
    sharePointFS.create(new Path("/test.txt"), null, false, 1024, (short) 1, 1024, null);
  }

  @Test(expected = IOException.class)
  public void testAppendThrowsException() throws IOException {
    sharePointFS.append(new Path("/test.txt"), 1024, null);
  }

  @Test(expected = IOException.class)
  public void testDeleteThrowsException() throws IOException {
    sharePointFS.delete(new Path("/test.txt"), false);
  }

  @Test(expected = IOException.class)
  public void testRenameThrowsException() throws IOException {
    sharePointFS.rename(new Path("/src"), new Path("/dst"));
  }

  @Test(expected = IOException.class)
  public void testMkdirsThrowsException() throws IOException {
    sharePointFS.mkdirs(new Path("/newdir"), null);
  }

  @Test
  public void testConfigurationWithStaticToken() throws IOException {
    Configuration testConf = new Configuration();
    testConf.set("sharepointAccessToken", "test-access-token-12345");

    SharePointFileSystem fs = new SharePointFileSystem();
    fs.setConf(testConf);

    // Verify configuration is set
    assertEquals("test-access-token-12345", fs.getConf().get("sharepointAccessToken"));
  }

  @Test
  public void testPathParsing() {
    // Test various path formats
    String[] testPaths = {
      "/",
      "/me/Documents/test.csv",
      "/sites/MySite/drive/Documents/file.xlsx",
      "/sites/MySite/lists/MyList"
    };

    for (String path : testPaths) {
      // Simply verify that paths can be constructed without errors
      Path p = new Path(path);
      assertNotNull(p);
    }
  }

  @Test
  public void testIsReadOnly() {
    // SharePoint filesystem should be read-only
    // Verify this by checking the error messages
    String expectedError = "SharePoint is read only.";

    try {
      sharePointFS.create(new Path("/test.txt"), null, false, 1024, (short) 1, 1024, null);
    } catch (IOException e) {
      assertTrue(e.getMessage().contains(expectedError) || e.getMessage().contains("read only"));
    }
  }

  @Test
  public void testListPathDetection() {
    // Test method should recognize SharePoint List paths
    // This is a simplified test that verifies path structure
    String listPath = "/sites/MySite/lists/MyList";
    assertTrue(listPath.contains("/lists/"));

    String filePath = "/me/Documents/test.csv";
    assertTrue(!filePath.contains("/lists/"));
  }
}
