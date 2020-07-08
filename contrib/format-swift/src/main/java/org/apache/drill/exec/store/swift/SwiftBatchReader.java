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

package org.apache.drill.exec.store.swift;

import com.prowidesoftware.swift.model.field.Field;
import com.prowidesoftware.swift.model.mt.AbstractMT;
import org.apache.drill.common.AutoCloseables;
import org.apache.drill.common.exceptions.CustomErrorContext;
import org.apache.drill.common.exceptions.UserException;
import org.apache.drill.common.types.TypeProtos;
import org.apache.drill.exec.physical.impl.scan.file.FileScanFramework.FileSchemaNegotiator;
import org.apache.drill.exec.physical.impl.scan.framework.ManagedReader;
import org.apache.drill.exec.physical.resultSet.ResultSetLoader;
import org.apache.drill.exec.physical.resultSet.RowSetLoader;
import org.apache.drill.exec.record.metadata.ColumnMetadata;
import org.apache.drill.exec.record.metadata.MetadataUtils;
import org.apache.drill.exec.record.metadata.SchemaBuilder;
import org.apache.drill.exec.record.metadata.TupleMetadata;
import org.apache.drill.exec.vector.accessor.ScalarWriter;
import org.apache.drill.exec.vector.accessor.TupleWriter;
import org.apache.hadoop.mapred.FileSplit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;


public class SwiftBatchReader implements ManagedReader<FileSchemaNegotiator> {

  private static final Logger logger = LoggerFactory.getLogger(SwiftBatchReader.class);
  private FileSplit split;
  private InputStream fsStream;
  private RowSetLoader rowWriter;
  private CustomErrorContext errorContext;
  private AbstractMT message;

  // Writers
  private ScalarWriter messageTypeIntWriter;
  private ScalarWriter messageNameWriter;
  private ScalarWriter messageAppIDWriter;
  private ScalarWriter logicalTerminalAddressWriter;
  private ScalarWriter sessionNumberWriter;
  private ScalarWriter sequenceNumberWriter;

  public static class SwiftReaderConfig {
    protected final SwiftFormatPlugin plugin;
    protected final boolean showFieldCodes;

    public SwiftReaderConfig(SwiftFormatPlugin plugin) {
      this.plugin = plugin;
      this.showFieldCodes = plugin.getConfig().getIncludeFieldCodes();
    }
  }

  @Override
  public boolean open(FileSchemaNegotiator negotiator) {
    split = negotiator.split();
    openFile(negotiator);
    errorContext = negotiator.parentErrorContext();
    SchemaBuilder builder = new SchemaBuilder();
    TupleMetadata schema = buildSchema(builder);
    negotiator.tableSchema(schema, false);
    ResultSetLoader loader = negotiator.build();
    rowWriter = loader.writer();
    populateColumnWriters(rowWriter);
    return true;
  }

  @Override
  public boolean next() {
    while (!rowWriter.isFull()) {
      if (!processMessage()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void close() {
    if (fsStream != null) {
      AutoCloseables.closeSilently(fsStream);
      fsStream = null;
    }
  }

  private void openFile(FileSchemaNegotiator negotiator) {
    try {
      fsStream = negotiator.fileSystem().openPossiblyCompressedStream(split.getPath());
      message = AbstractMT.parse(fsStream);
    } catch (Exception e) {
      throw UserException
        .dataReadError(e)
        .message("Unable to open Swift File %s", split.getPath())
        .addContext(e.getMessage())
        .addContext(errorContext)
        .build(logger);
    }
  }

  private boolean processMessage() {
    try {
      // Case for empty message
      if (message == null) {
        return false;
      }

      rowWriter.start();
      // Block 1 Fields
      messageAppIDWriter.setString(message.getApplicationId());
      logicalTerminalAddressWriter.setString(message.getLogicalTerminal());
      sessionNumberWriter.setString(message.getSessionNumber());
      sequenceNumberWriter.setString(message.getSequenceNumber());

      // Block Two Fields
      int messageType = message.getSwiftMessage().getTypeInt();
      // Header Information
      messageTypeIntWriter.setInt(messageType);
      messageNameWriter.setString(message.getMessageType());

      // Parse out fields unique to each message type
      for (Field field : message.getFields()) {
        String fieldName = Field.getLabel(field.getName(), message.getMessageType(), null);
        String fieldValue = field.getValueDisplay().trim();
        writeStringColumn(rowWriter, fieldName, fieldValue);
      }

      rowWriter.save();

    } catch (Exception e) {
      throw UserException
        .dataReadError(e)
        .message("Error reading Swift Message.")
        .addContext(errorContext)
        .build(logger);
    }
    return false;
  }

  private void populateColumnWriters(RowSetLoader rowWriter) {
    messageTypeIntWriter = rowWriter.scalar("message_type");
    messageNameWriter = rowWriter.scalar("message_name");
    messageAppIDWriter = rowWriter.scalar("application_id");
    logicalTerminalAddressWriter = rowWriter.scalar("logical_terminal_address");
    sessionNumberWriter = rowWriter.scalar("session_number");
    sequenceNumberWriter = rowWriter.scalar("sequence_number");


  }

  private TupleMetadata buildSchema(SchemaBuilder builder) {
    String[] INT_COLS = { "message_type"};
    String[] STRING_COLS = {"message_name", "application_id", "logical_terminal_address", "session_number", "sequence_number"};

    for (String name : STRING_COLS) {
      builder.addNullable(name, TypeProtos.MinorType.VARCHAR);
    }

    for (String name : INT_COLS) {
      builder.addNullable(name, TypeProtos.MinorType.INT);
    }

    return builder.buildSchema();
  }

  private void writeStringColumn(TupleWriter rowWriter, String name, String value) {
    int index = rowWriter.tupleSchema().index(name);
    if (index == -1) {
      ColumnMetadata colSchema = MetadataUtils.newScalar(name, TypeProtos.MinorType.VARCHAR, TypeProtos.DataMode.OPTIONAL);
      index = rowWriter.addColumn(colSchema);
    }
    ScalarWriter colWriter = rowWriter.scalar(index);
    colWriter.setString(value);
  }
}
