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


import org.apache.calcite.schema.SchemaPlus;

import org.apache.drill.common.exceptions.UserException;
import org.apache.drill.exec.server.DrillbitContext;
import org.apache.drill.exec.store.AbstractStoragePlugin;
import org.apache.drill.exec.store.SchemaConfig;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PulsarStoragePlugin extends AbstractStoragePlugin {
  private static final Logger logger  = LoggerFactory.getLogger(PulsarStoragePlugin.class);

  private final PulsarStoragePluginConfig config;
  private final PulsarSchemaFactory schemaFactory;
  private PulsarClient client;
  private PulsarAdmin admin;

  public PulsarStoragePlugin(PulsarStoragePluginConfig configuration, DrillbitContext context, String name) {
    super(context, name);
    this.config = configuration;
    this.schemaFactory = new PulsarSchemaFactory(this);
  }

  public PulsarClient getClient() {
    if (client != null) {
      return client;
    } else {
      try {
        client = PulsarClient.builder()
          .serviceUrl(config.getServiceUrl())
          .build();
        return client;
      } catch (PulsarClientException e) {
        throw UserException.connectionError()
          .message("Cannot connect to Pulsar: " + e.getMessage())
          .build(logger);
      }
    }
  }

  public PulsarAdmin getAdmin() {
    if (admin != null) {
      return admin;
    }
    try {
       admin = PulsarAdmin.builder()
        .serviceHttpUrl(config.getServiceUrl())
        .build();
      return admin;
    } catch (PulsarClientException e) {
      throw UserException.connectionError()
        .message("Cannot connect to Pulsar: " + e.getMessage())
        .build(logger);
    }
  }


  @Override
  public PulsarStoragePluginConfig getConfig() {
    return config;
  }

  @Override
  public boolean supportsRead() {
    return true;
  }

  @Override
  public void registerSchemas(SchemaConfig schemaConfig, SchemaPlus parent) {
    //schemaFactory.registerSchemas(schemaConfig, parent);
  }
}
