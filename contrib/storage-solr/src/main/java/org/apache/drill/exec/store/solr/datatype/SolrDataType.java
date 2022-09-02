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

import org.apache.drill.shaded.guava.com.google.common.collect.Lists;

public class SolrDataType extends RecordDataType {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SolrDataType.class);

  private final List<SimpleImmutableEntry<SqlTypeName, Boolean>> types = new ArrayList<>();

  private final List<String> names = Lists.newArrayList();

  private final boolean isNullable = true;

  public SolrDataType(SolrSchemaPojo cvSchema) {
    for (SolrSchemaField cvSchemaField : cvSchema.getSchemaFields()) {
      if (!cvSchemaField.isSkipdelete()) {// not
        // adding
        // cv
        // fields.
        names.add(cvSchemaField.getFieldName());
        String solrFieldType = cvSchemaField.getType();
        switch (solrFieldType) {
          case "string":
          case "commaDelimited":
          case "text_general":
          case "currency":
          case "uuid":
            types.add(new SimpleImmutableEntry<>(SqlTypeName.VARCHAR, isNullable));
            break;
          case "int":
          case "tint":
          case "pint":
            types.add(new SimpleImmutableEntry<>(SqlTypeName.INTEGER, isNullable));
            break;
          case "boolean":
            types.add(new SimpleImmutableEntry<>(SqlTypeName.BOOLEAN, isNullable));
            break;
          case "double":
          case "pdouble":
          case "tdouble":
          case "tlong":
          case "rounded1024":
          case "long":
            types.add(new SimpleImmutableEntry<>(SqlTypeName.DOUBLE, isNullable));
            break;
          case "date":
          case "tdate":
          case "timestamp":
            types.add(new SimpleImmutableEntry<>(SqlTypeName.TIMESTAMP, isNullable));
            break;
          case "float":
          case "tfloat":
            types.add(new SimpleImmutableEntry<>(SqlTypeName.DECIMAL, isNullable));
            break;
          default:
            logger.debug(String.format("PojoDataType doesn't yet support conversions from type [%s] for field [%s].Defaulting to varchar.", solrFieldType, cvSchemaField.getFieldName()));
            types.add(new SimpleImmutableEntry<>(SqlTypeName.VARCHAR, isNullable));
            break;
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
