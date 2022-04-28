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

package org.apache.drill.exec.store.http;

import com.typesafe.config.Config;
import okhttp3.HttpUrl;
import okhttp3.HttpUrl.Builder;
import org.apache.drill.common.AutoCloseables;
import org.apache.drill.common.exceptions.ExecutionSetupException;
import org.apache.drill.exec.ExecConstants;
import org.apache.drill.exec.ops.FragmentContext;
import org.apache.drill.exec.ops.OperatorContext;
import org.apache.drill.exec.physical.impl.OutputMutator;
import org.apache.drill.exec.store.AbstractRecordReader;
import org.apache.drill.exec.store.easy.json.JSONFormatConfig;
import org.apache.drill.exec.store.easy.json.JSONRecordReader;
import org.apache.drill.exec.store.http.HttpApiConfig.PostLocation;
import org.apache.drill.exec.store.http.util.HttpProxyConfig;
import org.apache.drill.exec.store.http.util.HttpProxyConfig.ProxyBuilder;
import org.apache.drill.exec.store.http.util.SimpleHttp;
import org.apache.drill.exec.store.security.UsernamePasswordWithProxyCredentials;
import org.apache.drill.shaded.guava.com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HttpLegacyJsonRecordReader extends AbstractRecordReader {
  private static final Logger logger = LoggerFactory.getLogger(HttpLegacyJsonRecordReader.class);
  private final JSONRecordReader jsonRecordReader;
  private String baseUrl;
  private final HttpSubScan subScan;
  private final int maxRecords;

  public HttpLegacyJsonRecordReader(FragmentContext context, HttpSubScan subScan) {
    this.subScan = subScan;
    this.maxRecords = subScan.maxRecords();
    this.baseUrl = subScan.tableSpec().connectionConfig().url();
    HttpJsonOptions jsonOptions = subScan.tableSpec().connectionConfig().jsonOptions();

    JSONFormatConfig config = new JSONFormatConfig(null, jsonOptions.allTextMode(),
      jsonOptions.readNumbersAsDouble(), null, jsonOptions.enableEscapeAnyChar(), jsonOptions.allowNanInf(), true);
    jsonRecordReader = new JSONRecordReader(context, subScan.columns(), config);
  }

  @Override
  public void setup(OperatorContext context, OutputMutator output) throws ExecutionSetupException {

    // Result set loader setup
    String tempDirPath = context.getFragmentContext().getConfig().getString(ExecConstants.DRILL_TMP_DIR);

    HttpUrl url = buildUrl();
    logger.debug("Final URL: {}", url);

    // Http client setup
    SimpleHttp http = SimpleHttp.builder()
      .scanDefn(subScan)
      .url(url)
      .tempDir(new File(tempDirPath))
      .proxyConfig(proxySettings(context.getFragmentContext().getConfig(), url))
      .build();

    jsonRecordReader.setInputStream(http.getInputStream());
    jsonRecordReader.setup(context, output);
  }

  protected HttpUrl buildUrl() {
    logger.debug("Building URL from {}", baseUrl);
    HttpApiConfig apiConfig = subScan.tableSpec().connectionConfig();

    // Append table name, if available.
    if (subScan.tableSpec().tableName() != null) {
      baseUrl += subScan.tableSpec().tableName();
    }

    // Add URL Parameters to baseURL
    HttpUrl parsedURL = HttpUrl.parse(baseUrl);
    baseUrl = SimpleHttp.mapURLParameters(parsedURL, subScan.filters());

    HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl).newBuilder();
    if (apiConfig.params() != null &&
      !apiConfig.params().isEmpty() &&
      subScan.filters() != null) {
      addFilters(urlBuilder, apiConfig.params(), subScan.filters());
    }

    // Add limit parameter if defined and limit set
    if (! Strings.isNullOrEmpty(apiConfig.limitQueryParam()) && maxRecords > 0) {
      urlBuilder.addQueryParameter(apiConfig.limitQueryParam(), String.valueOf(maxRecords));
    }

    return urlBuilder.build();
  }

  /**
   * Convert equality filter conditions into HTTP query parameters
   * Parameters must appear in the order defined in the config.
   */
  protected void addFilters(Builder urlBuilder, List<String> params,
                            Map<String, String> filters) {

    // If the request is a POST query and the user selected to push the filters to either JSON body
    // or the post body, do not add to the query string.
    if (subScan.tableSpec().connectionConfig().getMethodType() == HttpApiConfig.HttpMethod.POST &&
      (subScan.tableSpec().connectionConfig().getPostLocation() == PostLocation.POST_BODY ||
        subScan.tableSpec().connectionConfig().getPostLocation() == PostLocation.JSON_BODY)
    ) {
      return;
    }
    for (String param : params) {
      String value = filters.get(param);
      if (value != null) {
        urlBuilder.addQueryParameter(param, value);
      }
    }
  }

  protected HttpProxyConfig proxySettings(Config drillConfig, HttpUrl url) {
    final HttpStoragePluginConfig config = subScan.tableSpec().config();
    final ProxyBuilder builder = HttpProxyConfig.builder()
      .fromConfigForURL(drillConfig, url.toString());
    final String proxyType = config.proxyType();
    if (proxyType != null && !"direct".equals(proxyType)) {
      builder
        .type(config.proxyType())
        .host(config.proxyHost())
        .port(config.proxyPort());

      Optional<UsernamePasswordWithProxyCredentials> credentials = config.getUsernamePasswordCredentials();
      if (credentials.isPresent()) {
        builder.username(credentials.get().getUsername()).password(credentials.get().getPassword());
      }
    }
    return builder.build();
  }

  @Override
  public int next() {
    return jsonRecordReader.next();
  }

  @Override
  public void close() throws Exception {
    AutoCloseables.closeSilently(jsonRecordReader);
  }
}
