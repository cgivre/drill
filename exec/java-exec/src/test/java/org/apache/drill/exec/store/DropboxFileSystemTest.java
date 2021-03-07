package org.apache.drill.exec.store;

import org.apache.drill.exec.physical.rowSet.RowSet;
import org.apache.drill.exec.store.dfs.FileSystemConfig;
import org.apache.drill.exec.store.dfs.WorkspaceConfig;
import org.apache.drill.test.ClusterFixture;
import org.apache.drill.test.ClusterTest;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Map;

public class DropboxFileSystemTest extends ClusterTest {

  @BeforeClass
  public static void setup() throws Exception {
    ClusterTest.startCluster(ClusterFixture.builder(dirTestWatcher));
    StoragePluginRegistry pluginRegistry = cluster.storageRegistry();

    FileSystemConfig pluginConfig = (FileSystemConfig) pluginRegistry.getPlugin("dropbox").getConfig();
    Map<String, WorkspaceConfig> workspaces = pluginConfig.getWorkspaces();
  }

  @Test
  public void testListFiles() throws Exception {
    String sql = "SHOW FILES IN dropbox.root";
    RowSet results = client.queryBuilder().sql(sql).rowSet();
    results.print();
  }

  @Test
  public void testQuery() throws Exception {
    String sql = "SELECT * FROM dropbox.root.`/http-pcap.json` LIMIT 5";
    RowSet results = client.queryBuilder().sql(sql).rowSet();
    results.print();
  }
}
