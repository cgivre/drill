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

import java.util.List;
import java.util.Map;

import org.apache.drill.common.exceptions.ExecutionSetupException;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.common.types.TypeProtos;
import org.apache.drill.common.types.TypeProtos.MajorType;
import org.apache.drill.exec.exception.SchemaChangeException;
import org.apache.drill.exec.ops.FragmentContext;
import org.apache.drill.exec.ops.OperatorContext;
import org.apache.drill.exec.physical.impl.OutputMutator;
import org.apache.drill.exec.record.MaterializedField;
import org.apache.drill.exec.store.AbstractRecordReader;
import org.apache.drill.exec.vector.NullableBigIntVector;
import org.apache.drill.exec.vector.ValueVector;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.response.FieldStatsInfo;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.internal.Maps;

public class SolrAggrReader extends AbstractRecordReader {
  static final Logger logger = LoggerFactory.getLogger(SolrAggrReader.class);

  private FragmentContext fc;
  protected Map<String, ValueVector> vectors = Maps.newHashMap();
  protected String solrServerUrl;
  protected SolrClient solrClient;
  protected SolrSubScan solrSubScan;
  protected List<SolrScanSpec> scanList;
  protected SolrClientAPIExec solrClientApiExec;
  protected OutputMutator outputMutator;
  protected List<String> fields;
  private MajorType.Builder t;
  Map<String, FieldStatsInfo> fieldStatsInfoMap;
  private List<SolrAggrParam> solrAggrParams;

  public SolrAggrReader(FragmentContext context, SolrSubScan config) {
    fc = context;
    solrSubScan = config;

    solrServerUrl = solrSubScan.getSolrPlugin().getSolrStorageConfig()
        .getSolrServer();
    scanList = solrSubScan.getScanList();
    solrClientApiExec = solrSubScan.getSolrPlugin().getSolrClientApiExec();
    solrClient = solrSubScan.getSolrPlugin().getSolrClient();

    String solrCoreName = scanList.get(0).getSolrCoreName();
    List<SchemaPath> colums = config.getColumns();
    SolrFilterParam filters = config.getSolrScanSpec().getFilter();
    solrAggrParams = config.getSolrScanSpec().getAggrParams();

    StringBuilder sb = new StringBuilder();

    if (filters != null) {
      for (String filter : filters) {
        sb.append(filter);
      }
    }
    if (!solrAggrParams.isEmpty()) {
      QueryResponse queryRsp = solrClientApiExec.getSolrFieldStats(
          solrServerUrl, solrCoreName, this.fields, sb);
      if (queryRsp != null) {
        fieldStatsInfoMap = queryRsp.getFieldStatsInfo();
      }
    }

  }

  @Override
  public void setup(OperatorContext context, OutputMutator output)
      throws ExecutionSetupException {
    logger.debug("SolrAggrReader :: setup");
    if (fieldStatsInfoMap != null) {
      for (String field : fieldStatsInfoMap.keySet()) {
        logger.trace("stats field " + field);
      }
      for (SolrAggrParam solrAggrParam : solrAggrParams) {
        logger.debug("solrAggrParam :: " + solrAggrParam.getFieldName());
        if (fieldStatsInfoMap.containsKey(solrAggrParam.getFieldName())) {
          logger.debug("solrAggrParam1 :: " + solrAggrParam.getFieldName());
          t = MajorType.newBuilder().setMinorType(TypeProtos.MinorType.BIGINT);
          MaterializedField m_field = MaterializedField.create(
              solrAggrParam.getFieldName(), t.build());
          try {
            vectors.put(
                solrAggrParam.getFunctionName() + "_"
                    + solrAggrParam.getFieldName(),
                output.addField(m_field, NullableBigIntVector.class));
          } catch (SchemaChangeException e) {

          }
        }

      }
    }
  }

  @Override
  public int next() {
    logger.debug("SolrAggrReader :: next");
    int counter = 0;
    for (String key : vectors.keySet()) {
      ValueVector vv = vectors.get(key);
      String functionName = key.substring(0, key.lastIndexOf("_"));
      String solrField = vv.getField().getPath().toString().replaceAll("`", "");
      FieldStatsInfo fieldStats = fieldStatsInfoMap.get(solrField);

      if (vv.getClass().equals(NullableBigIntVector.class)) {
        NullableBigIntVector v = (NullableBigIntVector) vv;
        if (functionName.equalsIgnoreCase("sum")) {
          logger.debug("functionName [ " + functionName + " ]");
          Double d = new Double(fieldStats.getSum().toString());
          v.getMutator().setSafe(counter, d.longValue());
        } else {
          logger.debug("yet to implement function type [ " + functionName
              + " ]");
          v.getMutator().setSafe(counter, 0l);
        }
      }
      counter++;
    }
    for (String functionName : vectors.keySet()) {
      ValueVector vv = vectors.get(functionName);
      vv.getMutator().setValueCount(counter > 0 ? counter : 0);
    }
    vectors.clear();
    return counter;

  }

  @Override
  public void cleanup() {

  }

}
