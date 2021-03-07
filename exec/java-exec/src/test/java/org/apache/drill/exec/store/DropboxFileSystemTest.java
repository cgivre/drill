package org.apache.drill.exec.store;

import org.apache.drill.exec.physical.rowSet.RowSet;
import org.apache.drill.exec.store.dfs.FileSystemConfig;
import org.apache.drill.test.ClusterFixture;
import org.apache.drill.test.ClusterTest;
import org.junit.BeforeClass;
import org.junit.Test;

public class DropboxFileSystemTest extends ClusterTest {

  @BeforeClass
  public static void setup() throws Exception {
    ClusterTest.startCluster(ClusterFixture.builder(dirTestWatcher));
    StoragePluginRegistry pluginRegistry = cluster.storageRegistry();

    FileSystemConfig pluginConfig = (FileSystemConfig) pluginRegistry.getPlugin("dropbox").getConfig();
  }

  @Test
  public void testListFiles() throws Exception {
    String sql = "SHOW FILES IN dropbox";
    RowSet results = client.queryBuilder().sql(sql).rowSet();
    results.print();
  }

  @Test
  public void testQuery() throws Exception {
    String sql = "SELECT * FROM dropbox.`http-pcap.json`";
    RowSet results = client.queryBuilder().sql(sql).rowSet();
    results.print();
  }
}
