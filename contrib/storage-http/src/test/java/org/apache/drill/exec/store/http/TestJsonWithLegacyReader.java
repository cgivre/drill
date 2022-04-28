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

package org.apache.drill.exec.store.http;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.apache.drill.common.logical.StoragePluginConfig.AuthMode;
import org.apache.drill.common.logical.security.PlainCredentialsProvider;
import org.apache.drill.common.util.DrillFileUtils;
import org.apache.drill.exec.physical.rowSet.RowSet;
import org.apache.drill.exec.store.security.UsernamePasswordCredentials;
import org.apache.drill.shaded.guava.com.google.common.base.Charsets;
import org.apache.drill.shaded.guava.com.google.common.collect.ImmutableMap;
import org.apache.drill.shaded.guava.com.google.common.io.Files;
import org.apache.drill.test.ClusterFixture;
import org.apache.drill.test.ClusterTest;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.fail;

public class TestJsonWithLegacyReader extends ClusterTest {

  // Use high-numbered ports to avoid colliding with other tools on the
  // build machine.
  private static final int MOCK_SERVER_PORT = 44336;
  private static String TEST_JSON_RESPONSE_WITH_SCHEMA_CHANGE;
  public static String makeUrl(String url) {
    return String.format(url, MOCK_SERVER_PORT);
  }

  @BeforeClass
  public static void setup() throws Exception {
    startCluster(ClusterFixture.builder(dirTestWatcher));
    TEST_JSON_RESPONSE_WITH_SCHEMA_CHANGE = Files.asCharSource(DrillFileUtils.getResourceAsFile("/data/schema_change.json"), Charsets.UTF_8).read();

    dirTestWatcher.copyResourceToRoot(Paths.get("data/"));
    makeMockConfig();
  }

  private static void makeMockConfig() {

    HttpApiConfig mockTableWithSimpleJson = HttpApiConfig.builder()
      .url(makeUrl("http://localhost:%d/json"))
      .method("GET")
      .requireTail(false)
      .jsonOptions(HttpJsonOptions.builder()
        .unionEnabled(true)
        .build()
      )
      .build();

    Map<String, HttpApiConfig> configs = new HashMap<>();
    configs.put("simpleJson", mockTableWithSimpleJson);

    HttpStoragePluginConfig mockStorageConfigWithWorkspace =
      new HttpStoragePluginConfig(false, configs, 2, "globaluser", "globalpass", "",
        80, "", "", "", null, new PlainCredentialsProvider(ImmutableMap.of(
        UsernamePasswordCredentials.USERNAME, "globaluser",
        UsernamePasswordCredentials.PASSWORD, "globalpass")), AuthMode.SHARED_USER.name());
    mockStorageConfigWithWorkspace.setEnabled(true);
    cluster.defineStoragePlugin("local", mockStorageConfigWithWorkspace);
  }

  @Test
  public void simpleTestWithJsonConfig() {
    try (MockWebServer server = startServer()) {
      server.enqueue(new MockResponse().setResponseCode(200).setBody(TEST_JSON_RESPONSE_WITH_SCHEMA_CHANGE));
      String sql = "SELECT * FROM local.simpleJson";
      RowSet results = queryBuilder().sql(sql).rowSet();
      results.print();

    } catch (Exception e) {
      System.out.println(e.getMessage() + e.getCause());
      fail();
    }
  }

  /**
   * Helper function to start the MockHTTPServer
   * @return Started Mock server
   * @throws IOException If the server cannot start, throws IOException
   */
  public static MockWebServer startServer() throws IOException {
    MockWebServer server = new MockWebServer();
    server.start(MOCK_SERVER_PORT);
    return server;
  }
}
