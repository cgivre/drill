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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.drill.common.PlanStringBuilder;
import org.apache.drill.common.logical.AbstractSecuredStoragePluginConfig;
import org.apache.drill.common.logical.security.CredentialsProvider;
import org.apache.drill.common.logical.security.PlainCredentialsProvider;

import java.util.Objects;

@JsonTypeName(PulsarStoragePluginConfig.NAME)
public class PulsarStoragePluginConfig extends AbstractSecuredStoragePluginConfig {
  public static final String NAME = "pulsar";

  private final String brokerUrl;
  private final String serviceUrl;

  @JsonCreator
  public PulsarStoragePluginConfig(@JsonProperty("brokerUrl") String brokerUrl,
                                   @JsonProperty("serviceUrl") String serviceUrl,
                                   @JsonProperty("credentialsProvider") CredentialsProvider credentialsProvider) {
    super(getCredentialsProvider(credentialsProvider),
      credentialsProvider == null);
    this.brokerUrl = brokerUrl;
    this.serviceUrl = serviceUrl;
  }

  @JsonProperty("brokerUrl")
  public String getBrokerUrl() { return brokerUrl; }

  @JsonProperty("serviceUrl")
  public String getServiceUrl() { return serviceUrl; }

  private static CredentialsProvider getCredentialsProvider(CredentialsProvider credentialsProvider) {
    return credentialsProvider != null ? credentialsProvider : PlainCredentialsProvider.EMPTY_CREDENTIALS_PROVIDER;
  }

  @Override
  public String toString() {
    return new PlanStringBuilder(this)
      .field("brokerUrl", brokerUrl)
      .field("serviceUrl", serviceUrl)
      .toString();
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    } else if (that == null || getClass() != that.getClass()) {
      return false;
    }
    PulsarStoragePluginConfig other = (PulsarStoragePluginConfig) that;
    return Objects.equals(brokerUrl, other.brokerUrl) &&
      Objects.equals(serviceUrl, other.serviceUrl);
  }

  @Override
  public int hashCode() {
    return Objects.hash(brokerUrl, serviceUrl);
  }
}
