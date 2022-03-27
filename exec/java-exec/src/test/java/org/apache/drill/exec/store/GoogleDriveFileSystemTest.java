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
import org.apache.drill.exec.physical.rowSet.DirectRowSet;
import org.apache.drill.exec.store.dfs.FileSystemConfig;
import org.apache.drill.exec.store.dfs.WorkspaceConfig;
import org.apache.drill.exec.store.easy.json.JSONFormatPlugin;
import org.apache.drill.exec.store.easy.text.TextFormatConfig;
import org.apache.drill.test.ClusterFixture;
import org.apache.drill.test.ClusterTest;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Ignore("Please create a Google Drive account and run these tests manually")
public class GoogleDriveFileSystemTest extends ClusterTest {

  @BeforeClass
  public static void setup() throws Exception {
    ClusterTest.startCluster(ClusterFixture.builder(dirTestWatcher));

    // Create workspaces
    WorkspaceConfig rootWorkspace = new WorkspaceConfig("/", false, null, false);
    WorkspaceConfig csvWorkspace = new WorkspaceConfig("/csv", false, null, false);
    Map<String, WorkspaceConfig> workspaces = new HashMap<>();
    workspaces.put("root", rootWorkspace);
    workspaces.put("csv", csvWorkspace);

    // Add formats
    Map<String, FormatPluginConfig> formats = new HashMap<>();
    List<String> jsonExtensions = new ArrayList<>();
    jsonExtensions.add("json");
    FormatPluginConfig jsonFormatConfig = new JSONFormatPlugin.JSONFormatConfig(jsonExtensions, null, null, null, null, null);

    // CSV Format
    List<String> csvExtensions = new ArrayList<>();
    csvExtensions.add("csv");
    csvExtensions.add("csvh");
    FormatPluginConfig csvFormatConfig = new TextFormatConfig(csvExtensions, "\n", ",", "\"", null, null, false, true);

    StoragePluginConfig googleConfig = new FileSystemConfig("googledrive:///", new HashMap<>(), workspaces, formats, null);
    googleConfig.setEnabled(true);

    cluster.defineStoragePlugin("gd", googleConfig);
    cluster.defineFormat("gd", "json", jsonFormatConfig);
    cluster.defineFormat("gd", "csv", csvFormatConfig);
  }

  @Test
  public void testListFiles() throws Exception {
    String sql = "SHOW FILES IN gd";
    DirectRowSet results = client.queryBuilder().sql(sql).rowSet();
    results.print();
  }
}
