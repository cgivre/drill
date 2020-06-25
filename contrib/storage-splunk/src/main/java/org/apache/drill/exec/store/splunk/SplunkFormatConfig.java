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

package org.apache.drill.exec.store.splunk;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.drill.common.PlanStringBuilder;
import org.apache.drill.common.logical.StoragePluginConfigBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@JsonTypeName(SplunkFormatConfig.NAME)
public class SplunkFormatConfig extends StoragePluginConfigBase {

  public static final String NAME = "splunk";

  private static final Logger logger = LoggerFactory.getLogger(SplunkFormatConfig.class);

  private final String username;

  private final String password;

  private final String hostname;

  private final int port;

  @JsonCreator
  public SplunkFormatConfig(@JsonProperty("username") String username,
                            @JsonProperty("password") String password,
                            @JsonProperty("hostname") String hostname,
                            @JsonProperty("port") int port) {
    this.username = username;
    this.password = password;
    this.hostname = hostname;
    this.port = port;
  }


  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    } else if (that == null || getClass() != that.getClass()) {
      return false;
    }
    SplunkFormatConfig thatConfig = (SplunkFormatConfig) that;
    return Objects.equals(username, thatConfig.username) &&
      Objects.equals(password, thatConfig.password) &&
      Objects.equals(hostname, thatConfig.hostname) &&
      Objects.equals(port, thatConfig.port);
  }

  @Override
  public int hashCode() {
    return Objects.hash(username, password, hostname, port);
  }

  @Override
  public String toString() {
    return new PlanStringBuilder(this)
      .field("username", username)
      .field("password", password)
      .field("hostname", hostname)
      .field("port", port)
      .toString();
  }
}
