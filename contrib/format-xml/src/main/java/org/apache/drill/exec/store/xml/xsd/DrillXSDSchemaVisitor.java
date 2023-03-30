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

package org.apache.drill.exec.store.xml.xsd;

import org.apache.drill.common.types.TypeProtos.DataMode;
import org.apache.drill.common.types.TypeProtos.MinorType;
import org.apache.drill.exec.record.metadata.MapBuilder;
import org.apache.drill.exec.record.metadata.SchemaBuilder;
import org.apache.drill.exec.record.metadata.TupleMetadata;
import org.apache.ws.commons.schema.XmlSchemaAll;
import org.apache.ws.commons.schema.XmlSchemaAny;
import org.apache.ws.commons.schema.XmlSchemaAnyAttribute;
import org.apache.ws.commons.schema.XmlSchemaChoice;
import org.apache.ws.commons.schema.XmlSchemaElement;
import org.apache.ws.commons.schema.XmlSchemaSequence;
import org.apache.ws.commons.schema.walker.XmlSchemaAttrInfo;
import org.apache.ws.commons.schema.walker.XmlSchemaTypeInfo;
import org.apache.ws.commons.schema.walker.XmlSchemaVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Stack;

/**
 * This class transforms an XSD schema into a Drill Schema.
 */
public class DrillXSDSchemaVisitor implements XmlSchemaVisitor {
  private static final Logger logger = LoggerFactory.getLogger(DrillXSDSchemaVisitor.class);
  private final SchemaBuilder builder;
  private MapBuilder currentMapBuilder;
  private Stack<MapBuilder> mapBuilderStack;
  private int nestingLevel;

  public DrillXSDSchemaVisitor(SchemaBuilder builder) {
    this.builder = builder;
    this.mapBuilderStack = new Stack<>();
    this.nestingLevel = -1;
  }

  public TupleMetadata getDrillSchema() {
    return builder.build();
  }

  @Override
  public void onEnterElement(XmlSchemaElement xmlSchemaElement, XmlSchemaTypeInfo xmlSchemaTypeInfo, boolean b) {
    if (xmlSchemaTypeInfo.getType().name().equalsIgnoreCase("COMPLEX")) {
      // Start a map here.
      currentMapBuilder = builder.addMap(xmlSchemaElement.getName());
      mapBuilderStack.push(currentMapBuilder);
      nestingLevel++;
    } else {
      // If the field is a scalar, simply add it to the schema.
      boolean isRepeated = xmlSchemaElement.getMaxOccurs() > 1;
      MinorType dataType = XSDSchemaUtils.getDrillDataType(xmlSchemaTypeInfo.getBaseType().name());
      if (currentMapBuilder == null) {
        // If the current map is null, it means we are not in a nested construct
        if (isRepeated) {
          builder.add(xmlSchemaElement.getName(), dataType, DataMode.REPEATED);
        } else {
          builder.addNullable(xmlSchemaElement.getName(), dataType);
        }
      } else {
        // Otherwise, write to the current map builder
        currentMapBuilder.addNullable(xmlSchemaElement.getName(), dataType);
      }
    }
  }

  @Override
  public void onExitElement(XmlSchemaElement xmlSchemaElement, XmlSchemaTypeInfo xmlSchemaTypeInfo, boolean b) {
    // no op
  }

  @Override
  public void onVisitAttribute(XmlSchemaElement xmlSchemaElement, XmlSchemaAttrInfo xmlSchemaAttrInfo) {
    // no op
  }

  @Override
  public void onEndAttributes(XmlSchemaElement xmlSchemaElement, XmlSchemaTypeInfo xmlSchemaTypeInfo) {
    // no op
  }

  @Override
  public void onEnterSubstitutionGroup(XmlSchemaElement xmlSchemaElement) {
    // no op
  }

  @Override
  public void onExitSubstitutionGroup(XmlSchemaElement xmlSchemaElement) {
    // no op
  }

  @Override
  public void onEnterAllGroup(XmlSchemaAll xmlSchemaAll) {
    // no op
  }

  @Override
  public void onExitAllGroup(XmlSchemaAll xmlSchemaAll) {
    // no op
  }

  @Override
  public void onEnterChoiceGroup(XmlSchemaChoice xmlSchemaChoice) {
    // no op
  }

  @Override
  public void onExitChoiceGroup(XmlSchemaChoice xmlSchemaChoice) {
    // no op
  }

  @Override
  public void onEnterSequenceGroup(XmlSchemaSequence xmlSchemaSequence) {
    // no op
  }

  @Override
  public void onExitSequenceGroup(XmlSchemaSequence xmlSchemaSequence) {
    logger.debug("Leaving map: {} {}", currentMapBuilder.toString(), nestingLevel);
    if (currentMapBuilder != null) {
      if (nestingLevel > 1) {
        currentMapBuilder.resumeMap();
        currentMapBuilder = mapBuilderStack.pop();
      } else {
        // Stack should be empty at this point.
        currentMapBuilder = mapBuilderStack.pop();
        // Root level
        currentMapBuilder.resumeSchema();
      }
      nestingLevel--;
    }
  }

  @Override
  public void onVisitAny(XmlSchemaAny xmlSchemaAny) {
    // no op
  }

  @Override
  public void onVisitAnyAttribute(XmlSchemaElement xmlSchemaElement, XmlSchemaAnyAttribute xmlSchemaAnyAttribute) {
    // no op
  }
}
