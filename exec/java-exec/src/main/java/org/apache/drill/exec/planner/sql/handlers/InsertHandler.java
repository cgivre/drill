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

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexUtil;
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
import org.apache.drill.exec.physical.base.PhysicalOperator;
import org.apache.drill.exec.planner.logical.DrillRel;
import org.apache.drill.exec.planner.logical.DrillScreenRel;
import org.apache.drill.exec.planner.logical.DrillWriterRel;
import org.apache.drill.exec.planner.physical.Prel;
import org.apache.drill.exec.planner.physical.ProjectAllowDupPrel;
import org.apache.drill.exec.planner.physical.ProjectPrel;
import org.apache.drill.exec.planner.physical.WriterPrel;
import org.apache.drill.exec.planner.physical.visitor.BasePrelVisitor;
import org.apache.drill.exec.planner.sql.DirectPlan;
import org.apache.drill.exec.planner.sql.DrillSqlOperator;
import org.apache.drill.exec.planner.sql.SchemaUtilites;
import org.apache.drill.exec.rpc.user.UserSession;
import org.apache.drill.exec.store.AbstractSchema;
import org.apache.drill.exec.store.StorageStrategy;
import org.apache.drill.exec.util.Pointer;
import org.apache.drill.exec.work.foreman.ForemanSetupException;
import org.apache.drill.exec.work.foreman.SqlUnsupportedException;
import org.apache.drill.shaded.guava.com.google.common.collect.ImmutableList;
import org.apache.drill.shaded.guava.com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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

    final DrillConfig drillConfig = context.getConfig();
    final AbstractSchema drillSchema = resolveSchema(sqlInsert, config.getConverter().getDefaultSchema(), drillConfig);
    final String schemaPath = drillSchema.getFullSchemaName();

    // TODO Somehow check to see if the file or table exists.  This method doesn't seem to work at all,
    // so perhaps do it in the reader?
    // Check table creation possibility
    if (!checkTableInsertionPossibility(drillSchema, originalTableName, drillConfig, context.getSession(), schemaPath, true)) {
      return DirectPlan.createDirectPlan(context, false,
        String.format("A table or view with given name [%s] already exists in schema [%s]", originalTableName, schemaPath));
    }

    // Convert the query to Drill Logical plan and insert a writer operator on top.
    StorageStrategy storageStrategy = new StorageStrategy(context.getOption(ExecConstants.PERSISTENT_TABLE_UMASK).string_val, false);
    String newTableName = originalTableName;

    DrillRel drel = convertToDrel(newTblRelNode, drillSchema, newTableName, Collections.EMPTY_LIST, newTblRelNode.getRowType(), storageStrategy);
    Prel prel = convertToPrel(drel, newTblRelNode.getRowType(), Collections.EMPTY_LIST);
    logAndSetTextPlan("Drill Physical", prel, logger);

    PhysicalOperator pop = convertToPop(prel);
    PhysicalPlan plan = convertToPlan(pop);
    log("Drill Plan", plan, logger);

    String message = String.format("Inserting into %s table", originalTableName);
    logger.info(message);

    return plan;
  }

  private Prel convertToPrel(RelNode drel, RelDataType inputRowType, List<String> partitionColumns)
    throws RelConversionException, SqlUnsupportedException {
    Prel prel = convertToPrel(drel, inputRowType);

    prel = prel.accept(new ProjectForWriterVisitor(inputRowType, partitionColumns), null);

    return prel;
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
    logger.debug("Column names from Insert Handler: {}", columnNames);
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
  private boolean checkTableInsertionPossibility(AbstractSchema drillSchema,
                                                 String tableName,
                                                 DrillConfig config,
                                                 UserSession userSession,
                                                 String schemaPath,
                                                 boolean checkTableNonExistence) {
    boolean isTemporaryTable = userSession.isTemporaryTable(drillSchema, config, tableName);

    boolean tableFound = SqlHandlerUtil.getTableFromSchema(drillSchema, tableName) != null;
    if (isTemporaryTable || tableFound) {
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

  private class ProjectForWriterVisitor extends BasePrelVisitor<Prel, Void, RuntimeException> {

    private final RelDataType queryRowType;
    private final List<String> partitionColumns;

    ProjectForWriterVisitor(RelDataType queryRowType, List<String> partitionColumns) {
      this.queryRowType = queryRowType;
      this.partitionColumns = partitionColumns;
    }

    @Override
    public Prel visitPrel(Prel prel, Void value) throws RuntimeException {
      List<RelNode> children = Lists.newArrayList();
      for(Prel child : prel){
        child = child.accept(this, null);
        children.add(child);
      }

      return (Prel) prel.copy(prel.getTraitSet(), children);

    }

    @Override
    public Prel visitWriter(WriterPrel prel, Void value) throws RuntimeException {

      final Prel child = ((Prel) prel.getInput()).accept(this, null);

      final RelDataType childRowType = child.getRowType();

      final RelOptCluster cluster = prel.getCluster();

      final List<RexNode> exprs = Lists.newArrayListWithExpectedSize(queryRowType.getFieldCount() + 1);
      final List<String> fieldNames = new ArrayList<>(queryRowType.getFieldNames());

      for (final RelDataTypeField field : queryRowType.getFieldList()) {
        exprs.add(RexInputRef.of(field.getIndex(), queryRowType));
      }

      // No partition columns.
      if (partitionColumns.size() == 0) {
        final ProjectPrel projectUnderWriter = new ProjectAllowDupPrel(cluster,
          cluster.getPlanner().emptyTraitSet().plus(Prel.DRILL_PHYSICAL), child, exprs, queryRowType);

        return prel.copy(projectUnderWriter.getTraitSet(),
          Collections.singletonList( (RelNode) projectUnderWriter));
      } else {
        // find list of partition columns.
        final List<RexNode> partitionColumnExprs = Lists.newArrayListWithExpectedSize(partitionColumns.size());
        for (final String colName : partitionColumns) {
          final RelDataTypeField field = childRowType.getField(colName, false, false);

          if (field == null) {
            throw UserException.validationError()
              .message("Partition column %s is not in the SELECT list of CTAS!", colName)
              .build(logger);
          }

          partitionColumnExprs.add(RexInputRef.of(field.getIndex(), childRowType));
        }

        // Add partition column comparator to Project's field name list.
        fieldNames.add(WriterPrel.PARTITION_COMPARATOR_FIELD);

        // Add partition column comparator to Project's expression list.
        final RexNode partionColComp = createPartitionColComparator(prel.getCluster().getRexBuilder(), partitionColumnExprs);
        exprs.add(partionColComp);


        final RelDataType rowTypeWithPCComp = RexUtil.createStructType(cluster.getTypeFactory(), exprs, fieldNames, null);

        final ProjectPrel projectUnderWriter = new ProjectAllowDupPrel(cluster,
          cluster.getPlanner().emptyTraitSet().plus(Prel.DRILL_PHYSICAL), child, exprs, rowTypeWithPCComp);

        return prel.copy(projectUnderWriter.getTraitSet(),
          Collections.singletonList( (RelNode) projectUnderWriter));
      }
    }
  }


  private RexNode createPartitionColComparator(final RexBuilder rexBuilder, List<RexNode> inputs) {
    final DrillSqlOperator op = new DrillSqlOperator(WriterPrel.PARTITION_COMPARATOR_FUNC, 1, true, false);

    final List<RexNode> compFuncs = Lists.newArrayListWithExpectedSize(inputs.size());

    for (final RexNode input : inputs) {
      compFuncs.add(rexBuilder.makeCall(op, ImmutableList.of(input)));
    }

    return composeDisjunction(rexBuilder, compFuncs);
  }

  private RexNode composeDisjunction(final RexBuilder rexBuilder, List<RexNode> compFuncs) {
    final DrillSqlOperator booleanOrFunc
      = new DrillSqlOperator("orNoShortCircuit", 2, true, false);
    RexNode node = compFuncs.remove(0);
    while (!compFuncs.isEmpty()) {
      node = rexBuilder.makeCall(booleanOrFunc, node, compFuncs.remove(0));
    }
    return node;
  }


}
