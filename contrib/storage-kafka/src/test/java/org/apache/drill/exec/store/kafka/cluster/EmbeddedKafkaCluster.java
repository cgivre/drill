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
package org.apache.drill.exec.store.kafka.cluster;

import java.util.Properties;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.Collections;
import java.util.List;


public class EmbeddedKafkaCluster {

  private KafkaContainer kafkaContainer;
  private final Properties props;

  public EmbeddedKafkaCluster() {
    this(new Properties());
  }

  public EmbeddedKafkaCluster(Properties properties) {
    this.props = new Properties();
    this.props.putAll(properties);
    DockerImageName kafkaImage = DockerImageName.parse("confluentinc/cp-kafka:6.2.1")
    .asCompatibleSubstituteFor("apache/kafka");
    this.kafkaContainer = new KafkaContainer(kafkaImage);

    this.kafkaContainer.start();

    this.props.put("bootstrap.servers", kafkaContainer.getBootstrapServers());
    // If you need to pass Zookeeper connection for legacy code, Testcontainers manages it inside the container.
    // this.props.put("zookeeper.connect", ...); // Not needed in new Kafka versions.
  }

  public void shutDownCluster() {
    if (kafkaContainer != null) {
      kafkaContainer.stop();
      kafkaContainer = null;
    }
  }

  public String getKafkaBootstrapServers() {
    return kafkaContainer.getBootstrapServers();
  }

  /**
   * Returns Kafka broker bootstrap server connection string.
   *
   * @return String e.g. "PLAINTEXT://localhost:port"
   */
  public String getKafkaBrokerList() {
    if (kafkaContainer != null && kafkaContainer.isRunning()) {
      return kafkaContainer.getBootstrapServers();
    }
    return "";
  }

  public Properties getProps() {
    Properties tmpProps = new Properties();
    tmpProps.putAll(this.props);
    return tmpProps;
  }

  // Compatible API for clients expecting a list of "brokers"
  public List<String> getBrokers() {
    return kafkaContainer != null && kafkaContainer.isRunning()
        ? Collections.singletonList(kafkaContainer.getBootstrapServers())
        : Collections.emptyList();
  }

  // No-op for compatibility
  public void setBrokers(List<String> brokers) {
    // Not needed with Testcontainers
  }

  // Modern Kafka does not expose Zookeeper; this returns null for compatibility.
  public Object getZkServer() {
    return null;
  }

  // No-op as Testcontainers manages the lifecycle automatically
  public void registerToClose(AutoCloseable autoCloseable) {
    // Nothing to do
  }
}
