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

import org.apache.drill.common.logical.FormatPluginConfig;
import org.apache.drill.common.logical.StoragePluginConfig;
import org.apache.drill.common.types.TypeProtos.DataMode;
import org.apache.drill.common.types.TypeProtos.MinorType;
import org.apache.drill.exec.physical.rowSet.RowSet;
import org.apache.drill.exec.record.metadata.SchemaBuilder;
import org.apache.drill.exec.record.metadata.TupleMetadata;
import org.apache.drill.exec.store.dfs.FileSystemConfig;
import org.apache.drill.exec.store.dfs.WorkspaceConfig;
import org.apache.drill.exec.store.easy.json.JSONFormatConfig;
import org.apache.drill.exec.store.easy.text.TextFormatConfig;
import org.apache.drill.test.ClusterFixture;
import org.apache.drill.test.ClusterTest;
import org.apache.drill.test.rowSet.RowSetComparison;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Ignore("Please create a SharePoint/OneDrive access token and run these tests manually")
public class SharePointFileSystemTest extends ClusterTest {

  private static final String ACCESS_TOKEN = "<Your SharePoint/OneDrive Access Token Here>";

  /*
   Instructions for running SharePoint/OneDrive Unit Tests
   1.  Register an Azure App Registration with the following API permissions:
       - Files.Read
       - Sites.Read.All
       - offline_access
   2.  Obtain an access token for the registered app (you can use the Azure Portal or Microsoft Graph Explorer)
   3.  Paste the access token above into the ACCESS_TOKEN variable
   4.  Upload test files to your OneDrive (e.g., test.json, test.csv in the /me path)
   5.  Run tests
   */

  @BeforeClass
  public static void setup() throws Exception {
    assertTrue(! ACCESS_TOKEN.equalsIgnoreCase("<Your SharePoint/OneDrive Access Token Here>"));
    ClusterTest.startCluster(ClusterFixture.builder(dirTestWatcher));

    Map<String, String> sharePointConfigVars = new HashMap<>();
    sharePointConfigVars.put("sharepointAccessToken", ACCESS_TOKEN);

    // Create workspaces
    WorkspaceConfig rootWorkspace = new WorkspaceConfig("/", false, null, false);
    Map<String, WorkspaceConfig> workspaces = new HashMap<>();
    workspaces.put("root", rootWorkspace);

    // Add formats
    Map<String, FormatPluginConfig> formats = new HashMap<>();
    List<String> jsonExtensions = new ArrayList<>();
    jsonExtensions.add("json");
    FormatPluginConfig jsonFormatConfig = new JSONFormatConfig(jsonExtensions, null, null, null, null, null);

    // CSV Format
    List<String> csvExtensions = new ArrayList<>();
    csvExtensions.add("csv");
    csvExtensions.add("csvh");
    FormatPluginConfig csvFormatConfig = new TextFormatConfig(csvExtensions, "\n", ",", "\"", null, null, false, true);

    StoragePluginConfig sharePointConfig = new FileSystemConfig("sharepoint:///", sharePointConfigVars,
      workspaces, formats, null, null);
    sharePointConfig.setEnabled(true);

    cluster.defineStoragePlugin("sharepoint_test", sharePointConfig);
    cluster.defineFormat("sharepoint_test", "json", jsonFormatConfig);
    cluster.defineFormat("sharepoint_test", "csv", csvFormatConfig);
  }

  @Test
  @Ignore("Please create a SharePoint/OneDrive access token and run this test manually")
  public void testListOneDriveFiles() throws Exception {
    String sql = "SHOW FILES IN sharepoint_test.root";
    RowSet results = client.queryBuilder().sql(sql).rowSet();
    assertTrue(results.rowCount() > 0);

    TupleMetadata expectedSchema = new SchemaBuilder()
      .addNullable("name", MinorType.VARCHAR)
      .add("isDirectory", MinorType.BIT)
      .add("isFile", MinorType.BIT)
      .add("length", MinorType.BIGINT)
      .add("owner", MinorType.VARCHAR, DataMode.OPTIONAL)
      .add("group", MinorType.VARCHAR, DataMode.OPTIONAL)
      .add("permissions", MinorType.VARCHAR, DataMode.OPTIONAL)
      .add("accessTime", MinorType.TIMESTAMP, DataMode.OPTIONAL)
      .add("modificationTime", MinorType.TIMESTAMP, DataMode.OPTIONAL)
      .buildSchema();

    assertEquals(expectedSchema.getFieldCount(), results.batchSchema().getFieldCount());
  }

  @Test
  @Ignore("Please create a SharePoint/OneDrive access token and run this test manually")
  public void testJSONFileQuery() throws Exception {
    String sql = "SELECT * FROM sharepoint_test.`/me/test.json` LIMIT 5";
    RowSet results = client.queryBuilder().sql(sql).rowSet();
    assertTrue(results.rowCount() > 0);
    assertTrue(results.batchSchema().getFieldCount() > 0);
  }

  @Test
  @Ignore("Please create a SharePoint/OneDrive access token and run this test manually")
  public void testCSVFileQuery() throws Exception {
    String sql = "SELECT * FROM sharepoint_test.`/me/test.csv` LIMIT 5";
    RowSet results = client.queryBuilder().sql(sql).rowSet();
    assertTrue(results.rowCount() > 0);
    assertTrue(results.batchSchema().getFieldCount() > 0);
  }

  @Test
  @Ignore("Please create a SharePoint/OneDrive access token and run this test manually")
  public void testSharePointListQuery() throws Exception {
    String sql = "SELECT * FROM sharepoint_test.`/sites/MySite/lists/MyList` LIMIT 5";
    RowSet results = client.queryBuilder().sql(sql).rowSet();
    assertTrue(results.rowCount() >= 0);
  }
}
