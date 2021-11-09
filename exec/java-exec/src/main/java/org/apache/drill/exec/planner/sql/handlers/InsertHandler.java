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

import org.apache.calcite.sql.SqlInsert;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.tools.RelConversionException;
import org.apache.calcite.tools.ValidationException;
import org.apache.drill.common.config.DrillConfig;
import org.apache.drill.common.exceptions.UserException;
import org.apache.drill.common.util.DrillStringUtils;
import org.apache.drill.exec.physical.PhysicalPlan;
import org.apache.drill.exec.rpc.user.UserSession;
import org.apache.drill.exec.store.AbstractSchema;
import org.apache.drill.exec.util.Pointer;
import org.apache.drill.exec.work.foreman.ForemanSetupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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

    final DrillConfig drillConfig = context.getConfig();
    final AbstractSchema drillSchema = resolveSchema(sqlInsert, config.getConverter().getDefaultSchema(), drillConfig);
    final String schemaPath = drillSchema.getFullSchemaName();

    return null;
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

}
