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

import org.apache.drill.exec.planner.logical.DrillTable;
import org.apache.drill.exec.store.AbstractSchema;
import org.apache.pulsar.client.admin.Namespaces;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class PulsarSchema extends AbstractSchema {

  private static final Logger logger = LoggerFactory.getLogger(PulsarSchema.class);
  private final Map<String, DrillTable> activeTables = new HashMap<>();
  private final PulsarStoragePlugin plugin;
  private final PulsarAdmin admin;

  public PulsarSchema(String name, PulsarStoragePlugin plugin) {
    super(Collections.emptyList(), name);
    this.plugin = plugin;
    this.admin = plugin.getAdmin();
    registerTopics();
  }


  @Override
  public boolean showInInformationSchema()  { return true; }

  @Override
  public String getTypeName() {
    return PulsarStoragePluginConfig.NAME;
  }

  private void registerTopics() {
    PulsarStoragePluginConfig config = plugin.getConfig();
    Namespaces namespaces = admin.namespaces();
  }


}
