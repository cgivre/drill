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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.drill.exec.store.solr.schema.CVSchema;
import org.apache.drill.exec.store.solr.schema.CVSchemaField;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.io.Tuple;
import org.apache.solr.client.solrj.io.stream.SolrStream;
import org.apache.solr.client.solrj.request.CoreAdminRequest;
import org.apache.solr.client.solrj.response.CoreAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CoreAdminParams.CoreAdminAction;

import com.beust.jcommander.internal.Lists;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;

public class SolrClientAPIExec {
  static final org.slf4j.Logger logger = org.slf4j.LoggerFactory
      .getLogger(SolrClientAPIExec.class);
  private SolrClient solrClient;

  public SolrClient getSolrClient() {
    return solrClient;
  }

  public void setSolrClient(SolrClient solrClient) {
    this.solrClient = solrClient;
  }

  public SolrClientAPIExec(SolrClient solrClient) {
    this.solrClient = solrClient;
  }

  public SolrClientAPIExec() {

  }

  public Set<String> getSolrCoreList() {
    // Request core list
    logger.debug("getting cores from solr..");
    CoreAdminRequest request = new CoreAdminRequest();
    request.setAction(CoreAdminAction.STATUS);
    Set<String> coreList = null;
    try {
      CoreAdminResponse cores = request.process(solrClient);
      coreList = new HashSet<String>(cores.getCoreStatus().size());
      for (int i = 0; i < cores.getCoreStatus().size(); i++) {
        String coreName = cores.getCoreStatus().getName(i);
        coreList.add(coreName);
      }
    } catch (SolrServerException | IOException e) {
      logger.info("error getting core info from solr server...");
    }
    return coreList;
  }

  public CVSchema getSchemaForCore(String coreName, String solrServerUrl) {
    String schemaUrl = "{0}{1}/schema/fields";
    schemaUrl = MessageFormat.format(schemaUrl, solrServerUrl, coreName);
    HttpClient client = new DefaultHttpClient();
    HttpGet request = new HttpGet(schemaUrl);
    CVSchema oCVSchema = null;
    request.setHeader("Content-Type", "application/json");
    try {
      HttpResponse response = client.execute(request);
      BufferedReader rd = new BufferedReader(new InputStreamReader(response
          .getEntity().getContent()));
      StringBuffer result = new StringBuffer();
      String line = "";
      while ((line = rd.readLine()) != null) {
        result.append(line);
      }
      ObjectMapper mapper = new ObjectMapper();
      mapper
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      oCVSchema = mapper.readValue(result.toString(), CVSchema.class);
    } catch (Exception e) {
      logger.info("exception occured while fetching schema details..."
          + e.getMessage());
    }
    return oCVSchema;
  }

  public SolrDocumentList getSolrDocs(String solrServer, String solrCoreName,
      List<String> fields, StringBuilder filters) {
    SolrClient solrClient = new HttpSolrClient(solrServer + solrCoreName);
    SolrDocumentList sList = null;
    SolrQuery solrQuery = new SolrQuery().setTermsRegexFlag("case_insensitive")
        .setQuery("*:*").setRows(Integer.MAX_VALUE);

    if (fields != null) {
      String fieldStr = Joiner.on(",").join(fields);
      solrQuery.setParam("fl", fieldStr);
      logger.debug("response field list.." + fieldStr);
    }
    if (filters.length() > 0) {
      solrQuery.setParam("fq", filters.toString());
      logger.debug("filter query.." + filters.toString());
    }
    try {
      logger.debug("setting solrquery..");
      QueryResponse rsp = solrClient.query(solrQuery);
      logger.debug("response recieved from " + solrServer + " core "
          + solrCoreName);
      sList = rsp.getResults();
    } catch (SolrServerException | IOException e) {
      logger.debug("error occured while fetching results from solr server "
          + e.getMessage());
    }
    return sList;
  }

  public List<Tuple> getSolrResponse(String solrServer, SolrClient solrClient,
      String solrCoreName, Map<String, String> solrParams) {
    logger.info("sending request to solr server " + solrServer + " on core "
        + solrCoreName);
    SolrStream solrStream = new SolrStream(solrServer, solrParams);
    List<Tuple> resultTuple = Lists.newArrayList();
    try {
      solrStream.open();

      Tuple tuple = null;
      while (true) {
        tuple = solrStream.read();
        if (tuple.EOF) {
          break;
        }
        resultTuple.add(tuple);
      }
    } catch (Exception e) {
      logger.info("error occured while fetching results from solr server "
          + e.getMessage());
    } finally {
      try {
        solrStream.close();
      } catch (IOException e) {
        logger.debug("error occured while fetching results from solr server "
            + e.getMessage());
      }

    }
    return resultTuple;
  }

  public void createSolrView(String solrCoreName, String solrServerUrl,
      String solrCoreViewWorkspace) throws ClientProtocolException, IOException {
    CVSchema oCVSchema = getSchemaForCore(solrCoreName, solrServerUrl);
    List<CVSchemaField> schemaFieldList = oCVSchema.getFields();
    List<String> fieldNames = Lists.newArrayList(schemaFieldList.size());
    String createViewSql = "CREATE OR REPLACE VIEW {0}.{1} as SELECT {2} from solr.{3}";
    for (CVSchemaField cvSchemaField : schemaFieldList) {
      fieldNames.add(cvSchemaField.getName());
    }
    String fieldStr = Joiner.on(",").join(fieldNames);
    int lastIdxOf = solrCoreName.lastIndexOf("_");
    String viewName = solrCoreName.toLowerCase() + "view";
    if (lastIdxOf > -1) {
      viewName = solrCoreName.substring(0, lastIdxOf).toLowerCase() + "view";
    }
    createViewSql = MessageFormat.format(createViewSql, solrCoreViewWorkspace,
        viewName, fieldStr, solrCoreName);
    logger.debug("create solr view with sql command :: " + createViewSql);
    String drillWebUI = "http://localhost:8047/query";
    HttpClient client = HttpClientBuilder.create().build();
    HttpPost httpPost = new HttpPost(drillWebUI);
    List<BasicNameValuePair> urlParameters = new ArrayList<BasicNameValuePair>();
    urlParameters.add(new BasicNameValuePair("queryType", "SQL"));
    urlParameters.add(new BasicNameValuePair("query", createViewSql));
    httpPost.setEntity(new UrlEncodedFormEntity(urlParameters));
    HttpResponse response = client.execute(httpPost);
    logger.debug("Response Code after executing create view command : "
        + response.getStatusLine().getStatusCode());
  }
}
