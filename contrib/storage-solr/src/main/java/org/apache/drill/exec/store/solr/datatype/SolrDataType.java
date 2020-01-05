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

package org.apache.drill.exec.store.solr.datatype;

import java.util.ArrayList;
import java.util.List;
import java.util.AbstractMap.SimpleImmutableEntry;

import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.drill.exec.store.RecordDataType;
import org.apache.drill.exec.store.solr.schema.SolrSchemaPojo;
import org.apache.drill.exec.store.solr.schema.SolrSchemaField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.drill.shaded.guava.com.google.common.collect.Lists;

public class SolrDataType extends RecordDataType {
  private static final Logger logger = LoggerFactory.getLogger(SolrDataType.class);

  private final List<SimpleImmutableEntry<SqlTypeName, Boolean>> types = new ArrayList<>();

  private final List<String> names = Lists.newArrayList();

  private final SolrSchemaPojo cvSchema;

  private final boolean isNullable = true;

  public SolrDataType(SolrSchemaPojo cvSchema) {
    this.cvSchema = cvSchema;
    for (SolrSchemaField cvSchemaField : cvSchema.getSchemaFields()) {
      if (!cvSchemaField.isSkipdelete()) {// not
        // adding
        // cv
        // fields.
        names.add(cvSchemaField.getFieldName());
        String solrFieldType = cvSchemaField.getType();
        if (solrFieldType.equals("string") || solrFieldType.equals("commaDelimited") || solrFieldType.equals("text_general") || solrFieldType.equals("currency") || solrFieldType.equals("uuid")) {
          types.add(new SimpleImmutableEntry<SqlTypeName, Boolean>(SqlTypeName.VARCHAR, isNullable));
        } else if (solrFieldType.equals("int") || solrFieldType.equals("tint") || solrFieldType.equals("pint")) {
          types.add(new SimpleImmutableEntry<SqlTypeName, Boolean>(SqlTypeName.INTEGER, isNullable));
        } else if (solrFieldType.equals("boolean")) {
          types.add(new SimpleImmutableEntry<SqlTypeName, Boolean>(SqlTypeName.BOOLEAN, isNullable));
        } else if (solrFieldType.equals("double") || solrFieldType.equals("pdouble") || solrFieldType.equals("tdouble") || solrFieldType.equals("tlong") || solrFieldType.equals("rounded1024") || solrFieldType.equals("long")) {
          types.add(new SimpleImmutableEntry<SqlTypeName, Boolean>(SqlTypeName.DOUBLE, isNullable));
        } else if (solrFieldType.equals("date") || solrFieldType.equals("tdate") || solrFieldType.equals("timestamp")) {
          types.add(new SimpleImmutableEntry<SqlTypeName, Boolean>(SqlTypeName.TIMESTAMP, isNullable));
        } else if (solrFieldType.equals("float") || solrFieldType.equals("tfloat")) {
          types.add(new SimpleImmutableEntry<SqlTypeName, Boolean>(SqlTypeName.DECIMAL, isNullable));
        } else {
          logger.trace(String.format("PojoDataType doesn't yet support conversions from type [%s] for field [%s].Defaulting to varchar.", solrFieldType, cvSchemaField.getFieldName()));
          types.add(new SimpleImmutableEntry<SqlTypeName, Boolean>(SqlTypeName.VARCHAR, isNullable));
        }
      }
    }
  }

  @Override
  public List<SimpleImmutableEntry<SqlTypeName, Boolean>> getFieldSqlTypeNames() {
    return types;
  }

  @Override
  public List<String> getFieldNames() {
    return names;
  }
}
