/**
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
package org.apache.drill.exec.store.solr;

import java.io.IOException;

import org.apache.calcite.plan.RelOptRuleCall;
import org.apache.calcite.plan.RelOptRuleOperand;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexNode;
import org.apache.drill.common.exceptions.DrillRuntimeException;
import org.apache.drill.common.expression.LogicalExpression;
import org.apache.drill.exec.planner.logical.DrillOptiq;
import org.apache.drill.exec.planner.logical.DrillParseContext;
import org.apache.drill.exec.planner.logical.RelOptHelper;
import org.apache.drill.exec.planner.physical.FilterPrel;
import org.apache.drill.exec.planner.physical.PrelUtil;
import org.apache.drill.exec.planner.physical.ScanPrel;
import org.apache.drill.exec.store.StoragePluginOptimizerRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

public class SolrQueryFilterRule extends StoragePluginOptimizerRule {
  public static final StoragePluginOptimizerRule INSTANCE = new SolrQueryFilterRule();
  static final Logger logger = LoggerFactory
      .getLogger(SolrQueryFilterRule.class);

  public SolrQueryFilterRule(RelOptRuleOperand operand, String description) {
    super(operand, description);
    logger.info("SolrQueryFilterRule :: contructor");
  }

  public SolrQueryFilterRule() {
    super(
        RelOptHelper.some(FilterPrel.class, RelOptHelper.any(ScanPrel.class)),
        "SolrQueryFilterRule");
  }

  @Override
  public void onMatch(RelOptRuleCall call) {
    logger.debug("SolrQueryFilterRule :: onMatch");
    final ScanPrel scan = (ScanPrel) call.rel(1);
    final FilterPrel filter = (FilterPrel) call.rel(0);
    final RexNode condition = filter.getCondition();
    
    SolrGroupScan solrGroupScan = (SolrGroupScan) scan.getGroupScan();

    LogicalExpression conditionExp = DrillOptiq.toDrill(new DrillParseContext(
        PrelUtil.getPlannerSettings(call.getPlanner())), scan, condition);
    
    logger.info("conditionExp " + conditionExp);

    SolrQueryBuilder sQueryBuilder = new SolrQueryBuilder(solrGroupScan,
        conditionExp);
    SolrScanSpec newScanSpec = sQueryBuilder.parseTree();
    if (newScanSpec == null)
      return;
    logger.debug(" field names :: "+scan.getRowType().getFieldNames());
    SolrGroupScan newGroupScan = new SolrGroupScan(solrGroupScan.getUserName(),
        solrGroupScan.getSolrPlugin(), newScanSpec, solrGroupScan.getColumns());
    final ScanPrel newScanPrel = ScanPrel.create(scan, filter.getTraitSet(),
        newGroupScan, scan.getRowType());

    if (sQueryBuilder.isAllExpressionsConverted()) {
      logger.info("all expressions converted.. ");
      call.transformTo(newScanPrel);
    } else {
      call.transformTo(filter.copy(filter.getTraitSet(),
          ImmutableList.of((RelNode) newScanPrel)));
    }
  }

  @Override
  public boolean matches(RelOptRuleCall call) {
    final ScanPrel scan = (ScanPrel) call.rel(1);
    if (scan.getGroupScan() instanceof SolrGroupScan) {
      return super.matches(call);
    }
    return false;
  }
}
