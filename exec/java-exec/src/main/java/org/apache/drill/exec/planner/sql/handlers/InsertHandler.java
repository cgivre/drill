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

package org.apache.drill.exec.planner.sql.handlers;

import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlInsert;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.tools.RelConversionException;
import org.apache.calcite.tools.ValidationException;
import org.apache.drill.common.config.DrillConfig;
import org.apache.drill.common.exceptions.UserException;
import org.apache.drill.common.util.DrillStringUtils;
import org.apache.drill.exec.ExecConstants;
import org.apache.drill.exec.physical.PhysicalPlan;
import org.apache.drill.exec.planner.logical.DrillRel;
import org.apache.drill.exec.planner.logical.DrillScreenRel;
import org.apache.drill.exec.planner.logical.DrillWriterRel;
import org.apache.drill.exec.planner.sql.DirectPlan;
import org.apache.drill.exec.planner.sql.SchemaUtilites;
import org.apache.drill.exec.rpc.user.UserSession;
import org.apache.drill.exec.store.AbstractSchema;
import org.apache.drill.exec.store.StorageStrategy;
import org.apache.drill.exec.util.Pointer;
import org.apache.drill.exec.work.foreman.ForemanSetupException;
import org.apache.drill.exec.work.foreman.SqlUnsupportedException;
import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class InsertHandler extends DefaultSqlHandler {

  private static final Logger logger = LoggerFactory.getLogger(InsertHandler.class);

  public InsertHandler(SqlHandlerConfig config, Pointer<String> textPlan) {
    super(config, textPlan);
    logger.debug("In INSERT Handler");
  }

  @Override
  public PhysicalPlan getPlan(SqlNode sqlNode) throws ValidationException, RelConversionException, IOException, ForemanSetupException {
    logger.debug("Getting INSERT plan");

    final SqlInsert sqlInsert = unwrap(sqlNode, SqlInsert.class);
    final String originalTableName = DrillStringUtils.removeLeadingSlash(getTableName(sqlInsert));

    final ConvertedRelNode convertedRelNode = validateAndConvert(sqlInsert.getSource());
    final RelDataType validatedRowType = convertedRelNode.getValidatedRowType();
    final RelNode queryRelNode = convertedRelNode.getConvertedNode();

    final List<String> fieldNames = getFieldNames(sqlInsert);

    final RelNode newTblRelNode =
      SqlHandlerUtil.resolveNewTableRel(false, fieldNames, validatedRowType, queryRelNode);

    // TODO Check for table existence?

    final DrillConfig drillConfig = context.getConfig();
    final AbstractSchema drillSchema = resolveSchema(sqlInsert, config.getConverter().getDefaultSchema(), drillConfig);
    final String schemaPath = drillSchema.getFullSchemaName();


    // Check table creation possibility
    if (!checkTableCreationPossibility(drillSchema, originalTableName, drillConfig, context.getSession(), schemaPath, false)) {
      return DirectPlan.createDirectPlan(context, false,
        String.format("A table or view with given name [%s] already exists in schema [%s]", originalTableName, schemaPath));
    }

    StorageStrategy storageStrategy = new StorageStrategy(context.getOption(ExecConstants.PERSISTENT_TABLE_UMASK).string_val, false);
    String newTableName = originalTableName;

    DrillRel drel = convertToDrel(newTblRelNodeWithPCol, drillSchema, newTableName,
      sqlCreateTable.getPartitionColumns(), newTblRelNode.getRowType(), storageStrategy);


    return null;
  }

  private DrillRel convertToDrel(RelNode relNode,
                                 AbstractSchema schema,
                                 String tableName,
                                 List<String> partitionColumns,
                                 RelDataType queryRowType,
                                 StorageStrategy storageStrategy)
    throws RelConversionException, SqlUnsupportedException {
    final DrillRel convertedRelNode = convertToRawDrel(relNode);

    // Put a non-trivial topProject to ensure the final output field name is preserved, when necessary.
    // Only insert project when the field count from the child is same as that of the queryRowType.
    final DrillRel topPreservedNameProj = queryRowType.getFieldCount() == convertedRelNode.getRowType().getFieldCount() ?
      addRenamedProject(convertedRelNode, queryRowType) : convertedRelNode;

    final RelTraitSet traits = convertedRelNode.getCluster().traitSet().plus(DrillRel.DRILL_LOGICAL);
    final DrillWriterRel writerRel = new DrillWriterRel(convertedRelNode.getCluster(),
      traits, topPreservedNameProj, schema.createNewTable(tableName, partitionColumns, storageStrategy));
    return new DrillScreenRel(writerRel.getCluster(), writerRel.getTraitSet(), writerRel);
  }



  public String getTableName(SqlInsert sqlInsert) {
    return sqlInsert.getTargetTable().toString();
  }

  public List<String> getFieldNames(SqlInsert sqlInsert) {
    List<String> columnNames = Lists.newArrayList();
    SqlNodeList targetColumnList = sqlInsert.getTargetColumnList();
    if (targetColumnList != null) {
      List<SqlNode> fieldList = targetColumnList.getList();
      for (SqlNode node : fieldList) {
        columnNames.add(node.toString());
      }
    }
    return columnNames;
  }

  /**
   * Validates if table can be created in indicated schema
   * Checks if any object (persistent table / temporary table / view) with the same name exists
   * or if object with the same name exists but if not exists flag is set.
   *
   * @param drillSchema schema where table will be created
   * @param tableName table name
   * @param config drill config
   * @param userSession current user session
   * @param schemaPath schema path
   * @param checkTableNonExistence whether duplicate check is requested
   * @return if duplicate found in indicated schema
   * @throws UserException if duplicate found in indicated schema and no duplicate check requested
   */
  private boolean checkTableCreationPossibility(AbstractSchema drillSchema,
                                                String tableName,
                                                DrillConfig config,
                                                UserSession userSession,
                                                String schemaPath,
                                                boolean checkTableNonExistence) {
    boolean isTemporaryTable = userSession.isTemporaryTable(drillSchema, config, tableName);

    if (isTemporaryTable || SqlHandlerUtil.getTableFromSchema(drillSchema, tableName) != null) {
      if (checkTableNonExistence) {
        return false;
      } else {
        throw UserException.validationError()
          .message("A table or view with given name [%s] already exists in schema [%s]", tableName, schemaPath)
          .build(logger);
      }
    }
    return true;
  }

  /**
   * Resolves schema taking into account type of table being created.
   * If schema path wasn't indicated in sql call and table type to be created is temporary
   * returns temporary workspace.
   *
   * If schema path is indicated, resolves to mutable drill schema.
   * Though if table to be created is temporary table, checks if resolved schema is valid default temporary workspace.
   *
   * @param sqlInsert create table call
   * @param defaultSchema default schema
   * @param config drill config
   * @return resolved schema
   * @throws UserException if attempted to create temporary table outside of temporary workspace
   */
  private AbstractSchema resolveSchema(SqlInsert sqlInsert, SchemaPlus defaultSchema, DrillConfig config) {
    AbstractSchema resolvedSchema;
    SqlIdentifier table = (SqlIdentifier) sqlInsert.getOperandList().get(1);
    List<String> schemaPath = SchemaUtilites.getSchemaPath(table);
    resolvedSchema = SchemaUtilites.resolveToMutableDrillSchema(defaultSchema, schemaPath);
    return resolvedSchema;
  }
}
