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
package org.apache.drill.exec.store.parquet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.column.ParquetProperties;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.GroupFactory;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetFileWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.ExampleParquetWriter;
import org.apache.parquet.hadoop.example.GroupWriteSupport;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;

import java.io.IOException;
import java.util.Arrays;

import static org.apache.drill.exec.store.parquet.ParquetSimpleTestFileGenerator.EnumType.MAX_VALUE;
import static org.apache.drill.exec.store.parquet.ParquetSimpleTestFileGenerator.EnumType.MIN_VALUE;
import static org.apache.drill.exec.store.parquet.ParquetSimpleTestFileGenerator.EnumType.RANDOM_VALUE;


/**
 * Use the Parquet examples to build a simple to use Parquet test file generator.
 * Files currently generated by the main program were renamed to be used in the unit tests for DRILL-5971
 * and cover all logical types defined in  <a href="https://github.com/apache/parquet-format/blob/master/LogicalTypes.md">Parquet Logical Types </a>
 * that are supported by Drill. Embedded types specified in the Parquet specification are not covered by the
 * examples but can be added.
 * To create a new parquet file, define a schema, create a GroupWriter based on the schema, then add values
 * for individual records to the GroupWriter.
 * @see  org.apache.drill.exec.store.parquet.TestFileGenerator TestFileGenerator
 * @see org.apache.parquet.hadoop.example.GroupWriteSupport GroupWriteSupport
 * @see org.apache.parquet.example.Paper Dremel Example
 */
public class ParquetSimpleTestFileGenerator {

  public enum EnumType {
    RANDOM_VALUE, MAX_VALUE, MIN_VALUE
  }

  public static Path root = new Path("file:/tmp/parquet/");
  public static Configuration conf = new Configuration();

  public static String simpleSchemaMsg =
      "message ParquetLogicalDataTypes { \n" +
          "  required int32 rowKey; \n" +
          "  required binary _UTF8  ( UTF8 ) ; \n" +
          "  required binary _Enum  ( ENUM ) ; \n" +
          "  required fixed_len_byte_array(16) _UUID  ( UUID ) ; \n" +
          "  required int32  _INT32_RAW  ; \n" +
          "  required int32 _INT_8  ( INT_8 ) ; \n" +
          "  required int32 _INT_16  ( INT_16 ) ; \n" +
          "  required int32 _INT_32  ( INT_32 ) ; \n" +
          "  required int32 _UINT_8  ( UINT_8 ) ; \n" +
          "  required int32 _UINT_16  ( UINT_16 ) ; \n" +
          "  required int32 _UINT_32  ( UINT_32 ) ; \n" +
          "  required int32 _DECIMAL_decimal9  ( DECIMAL (9,2) ) ; \n" +
          "  required int64  _INT64_RAW  ; \n" +
          "  required int64 _INT_64  ( INT_64 ) ; \n" +
          "  required int64 _UINT_64  ( UINT_64 ) ; \n" +
          "  required int64 _DECIMAL_decimal18  ( DECIMAL (18,2) ) ; \n" +
          "  required fixed_len_byte_array(20) _DECIMAL_fixed_n  ( DECIMAL (20, 2) ) ; \n" +
          "  required binary _DECIMAL_unlimited  ( DECIMAL (30,2) ) ; \n" +
          "  required int32 _DATE_int32  ( DATE ) ; \n" +
          "  required int32 _TIME_MILLIS_int32  ( TIME_MILLIS ) ; \n" +
          //  "      required int64 _TIME_MICROS_int64  ( TIME_MICROS ) ; \n" +
          "  required int64 _TIMESTAMP_MILLIS_int64  ( TIMESTAMP_MILLIS ) ; \n" +
          "  required int64 _TIMESTAMP_MICROS_int64  ( TIMESTAMP_MICROS ) ; \n" +
          "  required fixed_len_byte_array(12) _INTERVAL_fixed_len_byte_array_12  ( INTERVAL ) ; \n" +
          "  required int96  _INT96_RAW  ; \n" +
          "} \n";
  public static String simpleNullableSchemaMsg =
      "message ParquetLogicalDataTypes { \n" +
          "  required int32 rowKey; \n" +
          "  optional binary _UTF8  ( UTF8 ) ; \n" +
          "  optional binary _Enum  ( ENUM ) ; \n" +
          "  optional fixed_len_byte_array(16) _UUID  ( UUID ) ; \n" +
          "  optional int32  _INT32_RAW  ; \n" +
          "  optional int32 _INT_8  ( INT_8 ) ; \n" +
          "  optional int32 _INT_16  ( INT_16 ) ; \n" +
          "  optional int32 _INT_32  ( INT_32 ) ; \n" +
          "  optional int32 _UINT_8  ( UINT_8 ) ; \n" +
          "  optional int32 _UINT_16  ( UINT_16 ) ; \n" +
          "  optional int32 _UINT_32  ( UINT_32 ) ; \n" +
          "  optional int32 _DECIMAL_decimal9  ( DECIMAL (9,2) ) ; \n" +
          "  optional int64  _INT64_RAW  ; \n" +
          "  optional int64 _INT_64  ( INT_64 ) ; \n" +
          "  optional int64 _UINT_64  ( UINT_64 ) ; \n" +
          "  optional int64 _DECIMAL_decimal18  ( DECIMAL (18,2) ) ; \n" +
          "  optional fixed_len_byte_array(20) _DECIMAL_fixed_n  ( DECIMAL (20, 2) ) ; \n" +
          "  optional binary _DECIMAL_unlimited  ( DECIMAL (30,2) ) ; \n" +
          "  optional int32 _DATE_int32  ( DATE ) ; \n" +
          "  optional int32 _TIME_MILLIS_int32  ( TIME_MILLIS ) ; \n" +
          //  "      optional int64 _TIME_MICROS_int64  ( TIME_MICROS ) ; \n" +
          "  optional int64 _TIMESTAMP_MILLIS_int64  ( TIMESTAMP_MILLIS ) ; \n" +
          "  optional int64 _TIMESTAMP_MICROS_int64  ( TIMESTAMP_MICROS ) ; \n" +
          "  optional fixed_len_byte_array(12) _INTERVAL_fixed_len_byte_array_12  ( INTERVAL ) ; \n" +
          "  optional int96  _INT96_RAW  ; \n" +
          "} \n";

  public static String complexSchemaMsg =
      "message ParquetLogicalDataTypes { \n" +
          "  required int32 rowKey; \n" +
          "  required group StringTypes { \n" +
          "    required binary _UTF8  ( UTF8 ) ; \n" +
          "    required binary _Enum  ( ENUM ) ; \n" +
          "    required fixed_len_byte_array(16) _UUID  ( UUID ) ; \n" +
          "  } \n" +
          "  required group NumericTypes { \n" +
          "    required group Int32 { \n" +
          "      required int32  _INT32_RAW  ; \n" +
          "      required int32 _INT_8  ( INT_8 ) ; \n" +
          "      required int32 _INT_16  ( INT_16 ) ; \n" +
          "      required int32 _INT_32  ( INT_32 ) ; \n" +
          "      required int32 _UINT_8  ( UINT_8 ) ; \n" +
          "      required int32 _UINT_16  ( UINT_16 ) ; \n" +
          "      required int32 _UINT_32  ( UINT_32 ) ; \n" +
          "      required int32 _DECIMAL_decimal9  ( DECIMAL (9,2) ) ; \n" +
          "    } \n" +
          "    required group Int64 { \n" +
          "      required int64  _INT64_RAW  ; \n" +
          "      required int64 _INT_64  ( INT_64 ) ; \n" +
          "      required int64 _UINT_64  ( UINT_64 ) ; \n" +
          "      required int64 _DECIMAL_decimal18  ( DECIMAL (18,2) ) ; \n" +
          "    } \n" +
          "    required group FixedLen { \n" +
          "      required fixed_len_byte_array(20) _DECIMAL_fixed_n  ( DECIMAL (20, 2) ) ; \n" +
          "    } \n" +
          "    required group Binary { \n" +
          "      required binary _DECIMAL_unlimited  ( DECIMAL (30,2) ) ; \n" +
          "    } \n" +
          "    required group DateTimeTypes { \n" +
          "      required int32 _DATE_int32  ( DATE ) ; \n" +
          "      required int32 _TIME_MILLIS_int32  ( TIME_MILLIS ) ; \n" +
          //      "      required int64 _TIME_MICROS_int64  ( TIME_MICROS ) ; \n" +
          "      required int64 _TIMESTAMP_MILLIS_int64  ( TIMESTAMP_MILLIS ) ; \n" +
          "      required int64 _TIMESTAMP_MICROS_int64  ( TIMESTAMP_MICROS ) ; \n" +
          "      required fixed_len_byte_array(12) _INTERVAL_fixed_len_byte_array_12  ( INTERVAL ) ; \n" +
          "    } \n" +
          "    required group Int96 { \n" +
          "      required int96  _INT96_RAW  ; \n" +
          "    } \n" +
          "  } \n" +
          "} \n";
  public static String complexNullableSchemaMsg =
      "message ParquetLogicalDataTypes { \n" +
          "  required int32 rowKey; \n" +
          "  optional group StringTypes { \n" +
          "    optional binary _UTF8  ( UTF8 ) ; \n" +
          "    optional binary _Enum  ( ENUM ) ; \n" +
          "    optional fixed_len_byte_array(16) _UUID  ( UUID ) ; \n" +
          "  } \n" +
          "  optional group NumericTypes { \n" +
          "    optional group Int32 { \n" +
          "      optional int32  _INT32_RAW  ; \n" +
          "      optional int32 _INT_8  ( INT_8 ) ; \n" +
          "      optional int32 _INT_16  ( INT_16 ) ; \n" +
          "      optional int32 _INT_32  ( INT_32 ) ; \n" +
          "      optional int32 _UINT_8  ( UINT_8 ) ; \n" +
          "      optional int32 _UINT_16  ( UINT_16 ) ; \n" +
          "      optional int32 _UINT_32  ( UINT_32 ) ; \n" +
          "      optional int32 _DECIMAL_decimal9  ( DECIMAL (9,2) ) ; \n" +
          "    } \n" +
          "    optional group Int64 { \n" +
          "      optional int64  _INT64_RAW  ; \n" +
          "      optional int64 _INT_64  ( INT_64 ) ; \n" +
          "      optional int64 _UINT_64  ( UINT_64 ) ; \n" +
          "      optional int64 _DECIMAL_decimal18  ( DECIMAL (18,2) ) ; \n" +
          "    } \n" +
          "    optional group FixedLen { \n" +
          "      optional fixed_len_byte_array(20) _DECIMAL_fixed_n  ( DECIMAL (20, 2) ) ; \n" +
          "    } \n" +
          "    optional group Binary { \n" +
          "      optional binary _DECIMAL_unlimited  ( DECIMAL (30,2) ) ; \n" +
          "    } \n" +
          "    optional group DateTimeTypes { \n" +
          "      optional int32 _DATE_int32  ( DATE ) ; \n" +
          "      optional int32 _TIME_MILLIS_int32  ( TIME_MILLIS ) ; \n" +
          //      "      optional int64 _TIME_MICROS_int64  ( TIME_MICROS ) ; \n" +
          "      optional int64 _TIMESTAMP_MILLIS_int64  ( TIMESTAMP_MILLIS ) ; \n" +
          "      optional int64 _TIMESTAMP_MICROS_int64  ( TIMESTAMP_MICROS ) ; \n" +
          "      optional fixed_len_byte_array(12) _INTERVAL_fixed_len_byte_array_12  ( INTERVAL ) ; \n" +
          "    } \n" +
          "    optional group Int96 { \n" +
          "      optional int96  _INT96_RAW  ; \n" +
          "    } \n" +
          "  } \n" +
          "} \n";
  public static String repeatedIntSchemaMsg =
      "message ParquetRepeated { \n" +
          "  required int32 rowKey; \n" +
          "  repeated int32 repeatedInt ( INTEGER(32,true) ) ; \n" +
          "} \n";
  public static final String microsecondColumnsSchemaMsg =
      "message ParquetMicrosecondDataTypes { \n" +
          "  required int32 rowKey; \n" +
          "  required int64 _TIME_MICROS_int64  ( TIME_MICROS ) ; \n" +
          "  required int64 _TIMESTAMP_MICROS_int64  ( TIMESTAMP_MICROS ) ; \n" +
          "} \n";

  public static MessageType simpleSchema = MessageTypeParser.parseMessageType(simpleSchemaMsg);
  public static MessageType complexSchema = MessageTypeParser.parseMessageType(complexSchemaMsg);
  public static MessageType simpleNullableSchema = MessageTypeParser.parseMessageType(simpleNullableSchemaMsg);
  public static MessageType complexNullableSchema = MessageTypeParser.parseMessageType(complexNullableSchemaMsg);
  public static MessageType repeatedIntSchema = MessageTypeParser.parseMessageType(repeatedIntSchemaMsg);
  public static MessageType microsecondColumnsSchema = MessageTypeParser.parseMessageType(microsecondColumnsSchemaMsg);


  public static Path initFile(String fileName) {
    return new Path(root, fileName);
  }

  public static ParquetWriter<Group> initWriter(MessageType schema, String fileName, boolean dictEncoding) throws IOException {
    return initWriter(schema, fileName, ParquetProperties.WriterVersion.PARQUET_1_0, dictEncoding);
  }

  public static ParquetWriter<Group> initWriter(
      MessageType schema,
      String fileName,
      ParquetProperties.WriterVersion version,
      boolean dictEncoding) throws IOException {

    GroupWriteSupport.setSchema(schema, conf);

    return ExampleParquetWriter.builder(initFile(fileName))
        .withDictionaryEncoding(dictEncoding)
        .withWriteMode(ParquetFileWriter.Mode.OVERWRITE)
        .withCompressionCodec(CompressionCodecName.SNAPPY)
        .withPageSize(1024)
        .withDictionaryPageSize(512)
        .withValidation(false)
        .withWriterVersion(version)
        .withConf(conf)
        .build();
  }

  public static void writeComplexValues(GroupFactory gf, ParquetWriter<Group> complexWriter, boolean writeNulls) throws IOException {
    int rowKey = 0;
    byte[] bytes12 = {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0', 'a', 'b' };
    // Write complex values
    {
      Group complexGroup = gf.newGroup();
      complexGroup.add("rowKey", ++rowKey);
      byte[] bytes = new byte[30];
      Arrays.fill(bytes, (byte) 1);
      complexGroup.addGroup("StringTypes")
          .append("_UTF8", "UTF8 string" + rowKey)
          .append("_Enum", RANDOM_VALUE.toString())
          .append("_UUID", Binary.fromConstantByteArray(bytes, 0, 16));
      Group numeric = complexGroup.addGroup("NumericTypes");
      numeric.addGroup("Int32")
          .append("_INT32_RAW", 1234567)
          .append("_INT_8", 123)
          .append("_INT_16", 12345)
          .append("_INT_32", 1234567)
          .append("_UINT_8", 123)
          .append("_UINT_16", 1234)
          .append("_UINT_32", 1234567)
          .append("_DECIMAL_decimal9", 1234567);
      numeric.addGroup("Int64")
          .append("_INT64_RAW", 1234567890123456L)
          .append("_INT_64", 1234567890123456L)
          .append("_UINT_64", 1234567890123456L)
          .append("_DECIMAL_decimal18", 1234567890123456L);
      numeric.addGroup("FixedLen").append("_DECIMAL_fixed_n", "12345678901234567890");
      numeric.addGroup("Binary").append("_DECIMAL_unlimited", "123456789012345678901234567890");
      numeric.addGroup("DateTimeTypes")
          .append("_DATE_int32", 1234567)
          .append("_TIME_MILLIS_int32", 1234567)
          .append("_TIMESTAMP_MILLIS_int64", 123456789012L)
          .append("_TIMESTAMP_MICROS_int64", 123456789012L)
          .append("_INTERVAL_fixed_len_byte_array_12", Binary.fromConstantByteArray(bytes12, 0, 12));
      numeric.addGroup("Int96").append("_INT96_RAW", Binary.fromConstantByteArray(bytes12, 0, 12));
      complexWriter.write(complexGroup);
    }
    {
      Group complexGroup = gf.newGroup();
      complexGroup.add("rowKey", ++rowKey);
      byte[] bytes = new byte[30];
      Arrays.fill(bytes, (byte) 1);
      complexGroup.addGroup("StringTypes")
          .append("_UTF8", "UTF8 string" + rowKey)
          .append("_Enum", MAX_VALUE.toString())
          .append("_UUID", Binary.fromConstantByteArray(bytes, 0, 16));
      Group numeric = complexGroup.addGroup("NumericTypes");
      numeric.addGroup("Int32")
          .append("_INT32_RAW", 0x7FFFFFFF)
          .append("_INT_8", 0x7F)
          .append("_INT_16", 0x7FFF)
          .append("_INT_32", 0x7FFFFFFF)
          .append("_UINT_8", 0xFF)
          .append("_UINT_16", 0xFFFF)
          .append("_UINT_32", 0xFFFFFFFF)
          .append("_DECIMAL_decimal9", 0xFFFFFFFF);
      numeric.addGroup("Int64")
          .append("_INT64_RAW", 0x7FFFFFFFFFFFFFFFL)
          .append("_INT_64", 0x7FFFFFFFFFFFFFFFL)
          .append("_UINT_64", 0xFFFFFFFFFFFFFFFFL)
          .append("_DECIMAL_decimal18", 0xFFFFFFFFFFFFFFFFL);
      numeric.addGroup("FixedLen").append("_DECIMAL_fixed_n", Binary.fromConstantByteArray(bytes, 0, 20));
      numeric.addGroup("Binary").append("_DECIMAL_unlimited", Binary.fromConstantByteArray(bytes, 0, 30));
      numeric.addGroup("DateTimeTypes")
          .append("_DATE_int32", 0xFFFFFFFF)
          .append("_TIME_MILLIS_int32", 0xFFFFFFFF)
          .append("_TIMESTAMP_MILLIS_int64", 0x1F3FFFFFFFFL)
          .append("_TIMESTAMP_MICROS_int64", 0x7FFFFFFFFFFFFFFFL)
          .append("_INTERVAL_fixed_len_byte_array_12", Binary.fromConstantByteArray(bytes, 0, 12));
      numeric.addGroup("Int96").append("_INT96_RAW", Binary.fromConstantByteArray(bytes, 0, 12));
      complexWriter.write(complexGroup);
    }
    {
      Group complexGroup = gf.newGroup();
      complexGroup.add("rowKey", ++rowKey);
      byte[] bytes = new byte[30];
      Arrays.fill(bytes, (byte) 1);
      complexGroup.addGroup("StringTypes")
          .append("_UTF8", "UTF8 string" + rowKey)
          .append("_Enum", MIN_VALUE.toString())
          .append("_UUID", Binary.fromConstantByteArray(bytes, 0, 16));
      Group numeric = complexGroup.addGroup("NumericTypes");
      numeric.addGroup("Int32")
          .append("_INT32_RAW", 0x80000000)
          .append("_INT_8", 0xFFFFFF80)
          .append("_INT_16", 0xFFFF8000)
          .append("_INT_32", 0x80000000)
          .append("_UINT_8", 0x0)
          .append("_UINT_16", 0x0)
          .append("_UINT_32", 0x0)
          .append("_DECIMAL_decimal9", 0x0);
      numeric.addGroup("Int64")
          .append("_INT64_RAW", 0x8000000000000000L)
          .append("_INT_64", 0x8000000000000000L)
          .append("_UINT_64", 0x0L)
          .append("_DECIMAL_decimal18", 0x0L);
      numeric.addGroup("FixedLen").append("_DECIMAL_fixed_n", Binary.fromConstantByteArray(new byte[20], 0, 20));
      numeric.addGroup("Binary").append("_DECIMAL_unlimited", Binary.fromConstantByteArray(new byte[30], 0, 30));
      numeric.addGroup("DateTimeTypes")
          .append("_DATE_int32", 0x0)
          .append("_TIME_MILLIS_int32", 0x0)
          .append("_TIMESTAMP_MILLIS_int64", 0x0L)
          .append("_TIMESTAMP_MICROS_int64", 0x0L)
          .append("_INTERVAL_fixed_len_byte_array_12", Binary.fromConstantByteArray( new byte[12], 0, 12));
      numeric.addGroup("Int96").append("_INT96_RAW", Binary.fromConstantByteArray( new byte[12], 0, 12));
      complexWriter.write(complexGroup);
    }
    if (writeNulls) {
      Group simpleGroup = gf.newGroup();
      simpleGroup.append("rowKey", ++rowKey);
      complexWriter.write(simpleGroup);
    }

  }


  public static void writeSimpleValues(SimpleGroupFactory sgf, ParquetWriter<Group> simpleWriter, boolean writeNulls) throws IOException {
    int rowKey = 0;
    // Write simple values
    {
      byte[] bytes12 = {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0', 'a', 'b' };
      Group simpleGroup = sgf.newGroup();
      simpleGroup.append("rowKey", ++rowKey);
      byte[] bytes = new byte[30];
      Arrays.fill(bytes, (byte) 1);
      simpleGroup
          .append("_UTF8", "UTF8 string" + rowKey)
          .append("_Enum", RANDOM_VALUE.toString())
          .append("_UUID", Binary.fromConstantByteArray(bytes, 0, 16))
          .append("_INT32_RAW", 1234567)
          .append("_INT_8", 123)
          .append("_INT_16", 12345)
          .append("_INT_32", 1234567)
          .append("_UINT_8", 123)
          .append("_UINT_16", 1234)
          .append("_UINT_32", 1234567)
          .append("_DECIMAL_decimal9", 1234567)
          .append("_INT64_RAW", 1234567890123456L)
          .append("_INT_64", 1234567890123456L)
          .append("_UINT_64", 1234567890123456L)
          .append("_DECIMAL_decimal18", 1234567890123456L)
          .append("_DECIMAL_fixed_n", "12345678901234567890")
          .append("_DECIMAL_unlimited", "123456789012345678901234567890")
          .append("_DATE_int32", 1234567)
          .append("_TIME_MILLIS_int32", 1234567)
          .append("_TIMESTAMP_MILLIS_int64", 123456789012L)
          .append("_TIMESTAMP_MICROS_int64", 123456789012L)
          .append("_INTERVAL_fixed_len_byte_array_12", Binary.fromConstantByteArray(bytes12, 0, 12))
          .append("_INT96_RAW", Binary.fromConstantByteArray(bytes12, 0, 12));
      simpleWriter.write(simpleGroup);
    }
    {
      Group simpleGroup = sgf.newGroup();
      byte[] bytes = new byte[30];
      Arrays.fill(bytes, (byte) 1);
      simpleGroup.append("rowKey", ++rowKey);
      simpleGroup
          .append("_UTF8", "UTF8 string" + rowKey)
          .append("_Enum", MAX_VALUE.toString())
          .append("_UUID", Binary.fromConstantByteArray(bytes, 0, 16))
          .append("_INT32_RAW", 0x7FFFFFFF)
          .append("_INT_8", 0x7F)
          .append("_INT_16", 0x7FFF)
          .append("_INT_32", 0x7FFFFFFF)
          .append("_UINT_8", 0xFF)
          .append("_UINT_16", 0xFFFF)
          .append("_UINT_32", 0xFFFFFFFF)
          .append("_DECIMAL_decimal9", 0xFFFFFFFF)
          .append("_INT64_RAW", 0x7FFFFFFFFFFFFFFFL)
          .append("_INT_64", 0x7FFFFFFFFFFFFFFFL)
          .append("_UINT_64", 0xFFFFFFFFFFFFFFFFL)
          .append("_DECIMAL_decimal18", 0xFFFFFFFFFFFFFFFFL)
          .append("_DECIMAL_fixed_n", Binary.fromConstantByteArray(bytes, 0, 20))
          .append("_DECIMAL_unlimited", Binary.fromConstantByteArray(bytes, 0, 30))
          .append("_DATE_int32", 0xFFFFFFFF)
          .append("_TIME_MILLIS_int32", 0xFFFFFFFF)
          .append("_TIMESTAMP_MILLIS_int64", 0x1F3FFFFFFFFL)
          .append("_TIMESTAMP_MICROS_int64", 0x7FFFFFFFFFFFFFFFL)
          .append("_INTERVAL_fixed_len_byte_array_12", Binary.fromConstantByteArray(bytes, 0, 12))
          .append("_INT96_RAW", Binary.fromConstantByteArray(bytes, 0, 12));
      simpleWriter.write(simpleGroup);
    }
    {
      Group simpleGroup = sgf.newGroup();
      simpleGroup.append("rowKey", ++rowKey);
      byte[] bytes = new byte[30];
      Arrays.fill(bytes, (byte) 1);
      simpleGroup
          .append("_UTF8", "UTF8 string" + rowKey)
          .append("_Enum", MIN_VALUE.toString())
          .append("_UUID", Binary.fromConstantByteArray(bytes, 0, 16))
          .append("_INT32_RAW", 0x80000000)
          .append("_INT_8", 0xFFFFFF80)
          .append("_INT_16", 0xFFFF8000)
          .append("_INT_32", 0x80000000)
          .append("_UINT_8", 0x0)
          .append("_UINT_16", 0x0)
          .append("_UINT_32", 0x0)
          .append("_DECIMAL_decimal9", 0x0)
          .append("_INT64_RAW", 0x8000000000000000L)
          .append("_INT_64", 0x8000000000000000L)
          .append("_UINT_64", 0x0L)
          .append("_DECIMAL_decimal18", 0x0L)
          .append("_DECIMAL_fixed_n", Binary.fromConstantByteArray(new byte[20], 0, 20))
          .append("_DECIMAL_unlimited", Binary.fromConstantByteArray(new byte[30], 0, 30))
          .append("_DATE_int32", 0x0)
          .append("_TIME_MILLIS_int32", 0x0)
          .append("_TIMESTAMP_MILLIS_int64", 0x0L)
          .append("_TIMESTAMP_MICROS_int64", 0x0L)
          .append("_INTERVAL_fixed_len_byte_array_12", Binary.fromConstantByteArray( new byte[12], 0, 12))
          .append("_INT96_RAW", Binary.fromConstantByteArray( new byte[12], 0, 12));
      simpleWriter.write(simpleGroup);
    }
    if (writeNulls) {
      Group simpleGroup = sgf.newGroup();
      simpleGroup.append("rowKey", ++rowKey);
      simpleWriter.write(simpleGroup);
    }
  }

  public static void writeRepeatedIntValues(
      SimpleGroupFactory groupFactory,
      ParquetWriter<Group> writer,
      int numRows) throws IOException {

    int[] repeatedValues = {666, 1492, 4711};

    for (int i = 0; i< numRows; i++) {

      Group g = groupFactory.newGroup();
      g.append("rowKey", i+1);
      for (int r :repeatedValues)  {
        g.append("repeatedInt", r);
      }

      writer.write(g);
    }
  }

  public static void writeMicrosecondValues(
      SimpleGroupFactory groupFactory,
      ParquetWriter<Group> writer,
      long[] timeMicrosValues,
      long[] timestampMicrosValues) throws IOException {

    int numValues = Math.min(timeMicrosValues.length, timestampMicrosValues.length);
    for (int i = 0; i < numValues; i++) {

      writer.write(
          groupFactory.newGroup()
              .append("rowKey", i + 1)
              .append("_TIME_MICROS_int64", timeMicrosValues[i])
              .append("_TIMESTAMP_MICROS_int64", timestampMicrosValues[i])
      );
    }
  }

  public static void main(String[] args) throws IOException {

    SimpleGroupFactory sgf = new SimpleGroupFactory(simpleSchema);
    GroupFactory gf = new SimpleGroupFactory(complexSchema);
    SimpleGroupFactory sngf = new SimpleGroupFactory(simpleNullableSchema);
    GroupFactory ngf = new SimpleGroupFactory(complexNullableSchema);
    SimpleGroupFactory repeatedIntGroupFactory = new SimpleGroupFactory(repeatedIntSchema);
    SimpleGroupFactory microsecondGroupFactory = new SimpleGroupFactory(microsecondColumnsSchema);

    // Generate files with dictionary encoding enabled and disabled
    ParquetWriter<Group> simpleWriter = initWriter(simpleSchema, "drill/parquet_test_file_simple", true);
    ParquetWriter<Group> complexWriter = initWriter(complexSchema, "drill/parquet_test_file_complex", true);
    ParquetWriter<Group> simpleNullableWriter = initWriter(simpleNullableSchema, "drill/parquet_test_file_simple_nullable", true);
    ParquetWriter<Group> complexNullableWriter = initWriter(complexNullableSchema, "drill/parquet_test_file_complex_nullable", true);
    ParquetWriter<Group> simpleNoDictWriter = initWriter(simpleSchema, "drill/parquet_test_file_simple_nodict", false);
    ParquetWriter<Group> complexNoDictWriter = initWriter(complexSchema, "drill/parquet_test_file_complex_nodict", false);
    ParquetWriter<Group> simpleNullableNoDictWriter = initWriter(simpleNullableSchema, "drill/parquet_test_file_simple_nullable_nodict", false);
    ParquetWriter<Group> complexNullableNoDictWriter = initWriter(complexNullableSchema, "drill/parquet_test_file_complex_nullable_nodict", false);
    ParquetWriter<Group> repeatedIntV2Writer = initWriter(repeatedIntSchema, "drill/parquet_v2_repeated_int.parquet", ParquetProperties.WriterVersion.PARQUET_2_0, true);
    ParquetWriter<Group> microsecondWriter = initWriter(microsecondColumnsSchema, "drill/microseconds.parquet", false);
    ParquetWriter<Group> microsecondSmallDiffWriter = initWriter(microsecondColumnsSchema, "drill/microseconds_small_diff.parquet", false);

    ParquetSimpleTestFileGenerator.writeSimpleValues(sgf, simpleWriter, false);
    ParquetSimpleTestFileGenerator.writeSimpleValues(sngf, simpleNullableWriter, true);
    ParquetSimpleTestFileGenerator.writeComplexValues(gf, complexWriter, false);
    ParquetSimpleTestFileGenerator.writeComplexValues(ngf, complexNullableWriter, true);
    ParquetSimpleTestFileGenerator.writeSimpleValues(sgf, simpleNoDictWriter, false);
    ParquetSimpleTestFileGenerator.writeSimpleValues(sngf, simpleNullableNoDictWriter, true);
    ParquetSimpleTestFileGenerator.writeComplexValues(gf, complexNoDictWriter, false);
    ParquetSimpleTestFileGenerator.writeComplexValues(ngf, complexNullableNoDictWriter, true);
    ParquetSimpleTestFileGenerator.writeRepeatedIntValues(repeatedIntGroupFactory, repeatedIntV2Writer, 100);
    ParquetSimpleTestFileGenerator.writeMicrosecondValues(
        microsecondGroupFactory,
        microsecondWriter,
        TestMicrosecondColumns.TIME_MICROS_VALUES,
        TestMicrosecondColumns.TIMESTAMP_MICROS_VALUES);
    ParquetSimpleTestFileGenerator.writeMicrosecondValues(
        microsecondGroupFactory,
        microsecondSmallDiffWriter,
        TestMicrosecondColumns.TIME_MICROS_SMALL_DIFF_VALUES,
        TestMicrosecondColumns.TIMESTAMP_MICROS_SMALL_DIFF_VALUES);

    simpleWriter.close();
    complexWriter.close();
    simpleNullableWriter.close();
    complexNullableWriter.close();
    simpleNoDictWriter.close();
    complexNoDictWriter.close();
    simpleNullableNoDictWriter.close();
    complexNullableNoDictWriter.close();
    repeatedIntV2Writer.close();
    microsecondWriter.close();
    microsecondSmallDiffWriter.close();
  }

}
