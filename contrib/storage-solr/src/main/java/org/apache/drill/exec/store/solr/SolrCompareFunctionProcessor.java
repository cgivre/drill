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
package org.apache.drill.exec.store.solr;

import org.apache.drill.common.expression.BooleanOperator;
import org.apache.drill.common.expression.CastExpression;
import org.apache.drill.common.expression.ConvertExpression;
import org.apache.drill.common.expression.FunctionCall;
import org.apache.drill.common.expression.FunctionHolderExpression;
import org.apache.drill.common.expression.IfExpression;
import org.apache.drill.common.expression.LogicalExpression;
import org.apache.drill.common.expression.NullExpression;
import org.apache.drill.common.expression.SchemaPath;
import org.apache.drill.common.expression.TypedNullConstant;
import org.apache.drill.common.expression.ValueExpressions.BooleanExpression;
import org.apache.drill.common.expression.ValueExpressions.DateExpression;
import org.apache.drill.common.expression.ValueExpressions.Decimal18Expression;
import org.apache.drill.common.expression.ValueExpressions.Decimal28Expression;
import org.apache.drill.common.expression.ValueExpressions.Decimal38Expression;
import org.apache.drill.common.expression.ValueExpressions.Decimal9Expression;
import org.apache.drill.common.expression.ValueExpressions.DoubleExpression;
import org.apache.drill.common.expression.ValueExpressions.FloatExpression;
import org.apache.drill.common.expression.ValueExpressions.IntExpression;
import org.apache.drill.common.expression.ValueExpressions.IntervalDayExpression;
import org.apache.drill.common.expression.ValueExpressions.IntervalYearExpression;
import org.apache.drill.common.expression.ValueExpressions.LongExpression;
import org.apache.drill.common.expression.ValueExpressions.QuotedString;
import org.apache.drill.common.expression.ValueExpressions.TimeExpression;
import org.apache.drill.common.expression.ValueExpressions.TimeStampExpression;
import org.apache.drill.common.expression.visitors.ExprVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class SolrCompareFunctionProcessor implements
    ExprVisitor<Boolean, LogicalExpression, RuntimeException> {
  private Object value;
  private boolean success;
  private boolean isEqualityFn;
  private SchemaPath path;
  private String functionName;
  static final Logger logger = LoggerFactory.getLogger(SolrCompareFunctionProcessor.class);

  public static boolean isCompareFunction(String functionName) {
    return COMPARE_FUNCTIONS_TRANSPOSE_MAP.keySet().contains(functionName);
  }

  public static SolrCompareFunctionProcessor process(FunctionCall call) {
    String functionName = call.getName();
    LogicalExpression nameArg = call.args.get(0);
    LogicalExpression valueArg = call.args.size() == 2 ? call.args.get(1)
        : null;
    SolrCompareFunctionProcessor evaluator = new SolrCompareFunctionProcessor(
        functionName);

    if (valueArg != null) { // binary function
      if (VALUE_EXPRESSION_CLASSES.contains(nameArg.getClass())) {
        LogicalExpression swapArg = valueArg;
        valueArg = nameArg;
        nameArg = swapArg;
        evaluator.functionName = COMPARE_FUNCTIONS_TRANSPOSE_MAP
            .get(functionName);
      }

      evaluator.success = nameArg.accept(evaluator, valueArg);
      logger.info(" this is a success "+evaluator.success);
    } else if (call.args.get(0) instanceof SchemaPath) {
      evaluator.success = true;
      evaluator.path = (SchemaPath) nameArg;
    }
    logger.info(" call "+call.args.get(0));

    return evaluator;
  }

  public SolrCompareFunctionProcessor(String functionName) {
    this.success = false;
    this.functionName = functionName;
    this.isEqualityFn = COMPARE_FUNCTIONS_TRANSPOSE_MAP
        .containsKey(functionName)
        && COMPARE_FUNCTIONS_TRANSPOSE_MAP.get(functionName).equals(
            functionName);
  }

  public Object getValue() {
    return value;
  }

  public boolean isSuccess() {
    return success;
  }

  public SchemaPath getPath() {
    return path;
  }

  public String getFunctionName() {
    return functionName;
  }

  @Override
  public Boolean visitCastExpression(CastExpression e,
      LogicalExpression valueArg) throws RuntimeException {
    if (e.getInput() instanceof CastExpression
        || e.getInput() instanceof SchemaPath) {
      return e.getInput().accept(this, valueArg);
    }
    return false;
  }

  @Override
  public Boolean visitConvertExpression(ConvertExpression e,
      LogicalExpression valueArg) throws RuntimeException {
    if (e.getConvertFunction() == ConvertExpression.CONVERT_FROM
        && e.getInput() instanceof SchemaPath) {
      String encodingType = e.getEncodingType();
      switch (encodingType) {
      case "INT_BE":
      case "INT":
      case "UINT_BE":
      case "UINT":
      case "UINT4_BE":
      case "UINT4":
        if (valueArg instanceof IntExpression
            && (isEqualityFn || encodingType.startsWith("U"))) {
          this.value = ((IntExpression) valueArg).getInt();
        }
        break;
      case "BIGINT_BE":
      case "BIGINT":
      case "UINT8_BE":
      case "UINT8":
        if (valueArg instanceof LongExpression
            && (isEqualityFn || encodingType.startsWith("U"))) {
          this.value = ((LongExpression) valueArg).getLong();
        }
        break;
      case "FLOAT":
        if (valueArg instanceof FloatExpression && isEqualityFn) {
          this.value = ((FloatExpression) valueArg).getFloat();
        }
        break;
      case "DOUBLE":
        if (valueArg instanceof DoubleExpression && isEqualityFn) {
          this.value = ((DoubleExpression) valueArg).getDouble();
        }
        break;
      case "TIME_EPOCH":
      case "TIME_EPOCH_BE":
        if (valueArg instanceof TimeExpression) {
          this.value = ((TimeExpression) valueArg).getTime();
        }
        break;
      case "DATE_EPOCH":
      case "DATE_EPOCH_BE":
        if (valueArg instanceof DateExpression) {
          this.value = ((DateExpression) valueArg).getDate();
        }
        break;
      case "BOOLEAN_BYTE":
        if (valueArg instanceof BooleanExpression) {
          this.value = ((BooleanExpression) valueArg).getBoolean();
        }
        break;
      case "UTF8":
        // let visitSchemaPath() handle this.
        return e.getInput().accept(this, valueArg);
      }

      if (value != null) {
        this.path = (SchemaPath) e.getInput();
        return true;
      }
    }
    return false;
  }

  @Override
  public Boolean visitUnknown(LogicalExpression e, LogicalExpression valueArg)
      throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitSchemaPath(SchemaPath path, LogicalExpression valueArg)
      throws RuntimeException {
    if (valueArg instanceof QuotedString) {
      this.value = ((QuotedString) valueArg).value;
      this.path = path;
      return true;
    }

    if (valueArg instanceof IntExpression) {
      this.value = ((IntExpression) valueArg).getInt();
      this.path = path;
      return true;
    }

    if (valueArg instanceof LongExpression) {
      this.value = ((LongExpression) valueArg).getLong();
      this.path = path;
      return true;
    }

    if (valueArg instanceof FloatExpression) {
      this.value = ((FloatExpression) valueArg).getFloat();
      this.path = path;
      return true;
    }

    if (valueArg instanceof DoubleExpression) {
      this.value = ((DoubleExpression) valueArg).getDouble();
      this.path = path;
      return true;
    }

    if (valueArg instanceof BooleanExpression) {
      this.value = ((BooleanExpression) valueArg).getBoolean();
      this.path = path;
      return true;
    }

    return false;
  }

  private static final ImmutableSet<Class<? extends LogicalExpression>> VALUE_EXPRESSION_CLASSES;
  static {
    ImmutableSet.Builder<Class<? extends LogicalExpression>> builder = ImmutableSet
        .builder();
    VALUE_EXPRESSION_CLASSES = builder.add(BooleanExpression.class)
        .add(DateExpression.class).add(DoubleExpression.class)
        .add(FloatExpression.class).add(IntExpression.class)
        .add(LongExpression.class).add(QuotedString.class)
        .add(TimeExpression.class).build();
  }
  private static final ImmutableMap<String, String> COMPARE_FUNCTIONS_TRANSPOSE_MAP;
  static {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    COMPARE_FUNCTIONS_TRANSPOSE_MAP = builder
        // unary functions
        .put("isnotnull", "isnotnull")
        .put("isNotNull", "isNotNull")
        .put("is not null", "is not null")
        .put("isnull", "isnull")
        .put("isNull", "isNull")
        .put("is null", "is null")
        // binary functions
        .put("equal", "equal").put("not_equal", "not_equal")
        .put("greater_than_or_equal_to", "less_than_or_equal_to")
        .put("greater_than", "less_than")
        .put("less_than_or_equal_to", "greater_than_or_equal_to")
        .put("less_than", "greater_than").build();
  }

  @Override
  public Boolean visitFunctionCall(FunctionCall call, LogicalExpression value)
      throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitFunctionHolderExpression(FunctionHolderExpression holder,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitIfExpression(IfExpression ifExpr, LogicalExpression value)
      throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitBooleanOperator(BooleanOperator call,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitIntConstant(IntExpression intExpr, LogicalExpression value)
      throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitFloatConstant(FloatExpression fExpr,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitLongConstant(LongExpression intExpr,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitDateConstant(DateExpression intExpr,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitTimeConstant(TimeExpression intExpr,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitTimeStampConstant(TimeStampExpression intExpr,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitIntervalYearConstant(IntervalYearExpression intExpr,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitIntervalDayConstant(IntervalDayExpression intExpr,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitDecimal9Constant(Decimal9Expression decExpr,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitDecimal18Constant(Decimal18Expression decExpr,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitDecimal28Constant(Decimal28Expression decExpr,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitDecimal38Constant(Decimal38Expression decExpr,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitDoubleConstant(DoubleExpression dExpr,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitBooleanConstant(BooleanExpression e,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitQuotedStringConstant(QuotedString e,
      LogicalExpression value) throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitNullConstant(TypedNullConstant e, LogicalExpression value)
      throws RuntimeException {
    return false;
  }

  @Override
  public Boolean visitNullExpression(NullExpression e, LogicalExpression value)
      throws RuntimeException {
    return false;
  }
}
