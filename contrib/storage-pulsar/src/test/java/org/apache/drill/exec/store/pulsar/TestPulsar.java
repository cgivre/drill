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

package org.apache.drill.exec.store.pulsar;

import org.apache.drill.test.ClusterFixture;
import org.apache.drill.test.ClusterTest;
import org.apache.drill.test.QueryBuilder.QuerySummary;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PulsarContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestPulsar extends ClusterTest {
  private static final DockerImageName PULSAR_DOCKER_CONTAINER = DockerImageName.parse("apachepulsar/pulsar:2.8.1");
  private static final Logger logger = LoggerFactory.getLogger(TestPulsar.class);
  private static PulsarContainer pulsar;


  @BeforeClass
  public static void setup() throws Exception {
    startCluster(ClusterFixture.builder(dirTestWatcher));
    dirTestWatcher.copyResourceToRoot(Paths.get("data/"));

    pulsar = new PulsarContainer(PULSAR_DOCKER_CONTAINER).withFunctionsWorker();
    pulsar.start();
    logger.debug("Broker URL: {} ", pulsar.getPulsarBrokerUrl());
    logger.debug("Service URL: {}", pulsar.getHttpServiceUrl());

    PulsarStoragePluginConfig pulsarConfig = new PulsarStoragePluginConfig(pulsar.getPulsarBrokerUrl(), pulsar.getHttpServiceUrl(), null);
    pulsarConfig.setEnabled(true);
    cluster.defineStoragePlugin("pulsar", pulsarConfig);
  }

  @Test
  public void testSchemaResolution() throws Exception {
    String sql = "SHOW DATABASES";
    QuerySummary schemaResults = queryBuilder().sql(sql).run();
    assertTrue(schemaResults.succeeded());

    fail("Not yet implemented");
  }

  @AfterClass
  public static void cleanup() {
    pulsar.close();
  }

}
