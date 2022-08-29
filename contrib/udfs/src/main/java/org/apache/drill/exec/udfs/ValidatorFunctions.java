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

import org.apache.drill.exec.expr.DrillSimpleFunc;
import org.apache.drill.exec.expr.annotations.FunctionTemplate;
import org.apache.drill.exec.expr.annotations.Output;
import org.apache.drill.exec.expr.annotations.Param;
import org.apache.drill.exec.expr.holders.BitHolder;
import org.apache.drill.exec.expr.holders.VarCharHolder;

public class ValidatorFunctions {

  @FunctionTemplate(names = {"isValid", "is_valid"},
    scope = FunctionTemplate.FunctionScope.SIMPLE,
    nulls = FunctionTemplate.NullHandling.NULL_IF_NULL)
  public static class ValidatorUDF implements DrillSimpleFunc {

    @Param
    VarCharHolder rawInput;

    @Param
    VarCharHolder rawValidationType;

    @Output
    BitHolder out;

    @Override
    public void setup() {
    }

    @Override
    public void eval() {
      String input = org.apache.drill.exec.expr.fn.impl.StringFunctionHelpers.toStringFromUTF8(rawInput.start, rawInput.end, rawInput.buffer);
      String validationType = org.apache.drill.exec.expr.fn.impl.StringFunctionHelpers.toStringFromUTF8(rawValidationType.start, rawValidationType.end, rawValidationType.buffer);

      boolean result = org.apache.drill.exec.udfs.ValidatorUtils.validate(input, validationType);
      out.value = result ? 1 : 0;
    }
  }
}
