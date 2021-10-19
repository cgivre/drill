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

package org.apache.drill.exec.planner.sql.parser;

import org.apache.calcite.sql.SqlWriter;
import org.apache.drill.exec.planner.sql.SchemaUtilites;
import org.apache.drill.exec.planner.sql.handlers.InsertHandler;
import org.apache.drill.exec.planner.sql.handlers.SqlHandlerUtil;
import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlSpecialOperator;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.drill.exec.planner.sql.handlers.AbstractSqlHandler;
import org.apache.drill.exec.planner.sql.handlers.SqlHandlerConfig;
import org.apache.drill.exec.util.Pointer;
import org.apache.drill.shaded.guava.com.google.common.base.Preconditions;

import java.util.List;

public class SqlInsert extends DrillSqlCall {

  public static final SqlSpecialOperator OPERATOR = new SqlSpecialOperator("INSERT", SqlKind.INSERT) {
    @Override
    public SqlCall createCall(SqlLiteral functionQualifier, SqlParserPos pos, SqlNode... operands) {
      Preconditions.checkArgument(operands.length == 3, "SqlInsert.createCall() has to receive 3 operands!");
      return new SqlInsert(pos,
        (SqlIdentifier) operands[0],
        (SqlNodeList) operands[1],
        operands[2]);
    }
  };

  private final SqlIdentifier tblName;
  private final SqlNodeList fieldList;
  private final SqlNode query;
  public SqlInsert(SqlParserPos pos, SqlIdentifier tblName, SqlNodeList fieldList, SqlNode query) {
    super(pos);
    this.tblName = tblName;
    this.fieldList = fieldList;
    this.query = query;
  }

  @Override
  public SqlOperator getOperator() {
    return OPERATOR;
  }

  @Override
  public List<SqlNode> getOperandList() {
    List<SqlNode> ops = Lists.newArrayList();
    ops.add(tblName);
    ops.add(fieldList);
    ops.add(query);
    return ops;
  }

  @Override
  public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
    writer.keyword("INSERT");
    writer.keyword("INTO");
    tblName.unparse(writer, leftPrec, rightPrec);

    if (fieldList.size() > 0) {
      SqlHandlerUtil.unparseSqlNodeList(writer, leftPrec, rightPrec, fieldList);
    }

    query.unparse(writer, leftPrec, rightPrec);
  }


  @Override
  public AbstractSqlHandler getSqlHandler(SqlHandlerConfig config) {
    return getSqlHandler(config, null);
  }

  @Override
  public AbstractSqlHandler getSqlHandler(SqlHandlerConfig config, Pointer<String> textPlan) {
    assert textPlan != null : "INSERT statement should have a plan";
    return new InsertHandler(config, textPlan);
  }

  public List<String> getSchemaPath() {
    return SchemaUtilites.getSchemaPath(tblName);
  }

  public String getName() {
    if (tblName.isSimple()) {
      return tblName.getSimple();
    }

    return tblName.names.get(tblName.names.size() - 1);
  }

  public List<String> getFieldNames() {
    List<String> columnNames = Lists.newArrayList();
    for(SqlNode node : fieldList.getList()) {
      columnNames.add(node.toString());
    }
    return columnNames;
  }

  public SqlNode getQuery() { return query; }
}
