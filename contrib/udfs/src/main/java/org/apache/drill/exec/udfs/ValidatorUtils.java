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

package org.apache.drill.exec.udfs;

import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.commons.validator.routines.UrlValidator;

public class ValidatorUtils {

  public static boolean validate (String data, String dataType) {
    switch (dataType.toUpperCase()) {
      case "CREDIT_CARD":
      case "CREDIT CARD":
      case "CREDITCARD":
        break;
      case "DOMAIN":
      case "DOMAIN NAME":
      case "DOMAIN_NAME":
        DomainValidator domainValidator = DomainValidator.getInstance();
        return domainValidator.isValid(data);
      case "EMAIL":
      case "EMAILADDRESS":
      case "EMAIL_ADDRESS":
      case "EMAIL ADDRESS":
        EmailValidator emailValidator = EmailValidator.getInstance();
        return emailValidator.isValid(data);
      case "URL":
        UrlValidator urlValidator = new UrlValidator();
        return urlValidator.isValid(data);
      default:
        return false;
    }

    return false;
  }
}
