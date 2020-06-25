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

package org.apache.drill.exec.store.splunk;

import java.util.Arrays;

public class SplunkUtils {
  /**
   * These are special fields that alter the queries sent to Splunk.
   */
  public enum SPECIAL_FIELDS {
    /**
     * The sourcetype of a query. Specifying the sourcetype can improve query performance.
     */
    sourcetype,
    /**
     * Used to send raw SPL to Splunk
     */
    spl,
    /**
     * The earliest time boundary of a query, overwrites config variable
     */
    earliestTime,
    /**
     * The latest time bound of a query, overwrites config variable
     */
    latestTime
  }

  /**
   * Indicates whether the field in question is a special field and should be pushed down to the query or not.
   * @param field The field to be pushed down
   * @return true if the field is a special field, false if not.
   */
  public static boolean isSpecialField (String field) {
    return Arrays.asList(SPECIAL_FIELDS.values()).contains(field);
  }
}
