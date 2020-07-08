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

import org.apache.drill.categories.RowSetTests;
import org.apache.drill.common.types.TypeProtos;
import org.apache.drill.exec.physical.rowSet.RowSet;
import org.apache.drill.exec.physical.rowSet.RowSetBuilder;
import org.apache.drill.exec.record.metadata.SchemaBuilder;
import org.apache.drill.exec.record.metadata.TupleMetadata;
import org.apache.drill.test.ClusterFixture;
import org.apache.drill.test.ClusterTest;
import org.apache.drill.test.QueryBuilder;
import org.apache.drill.test.rowSet.RowSetComparison;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.nio.file.Paths;

import static org.apache.drill.test.QueryTestUtil.generateCompressedFile;
import static org.junit.Assert.assertEquals;

@Category(RowSetTests.class)
public class TestSwiftMessage extends ClusterTest {

  @BeforeClass
  public static void setup() throws Exception {
    ClusterTest.startCluster(ClusterFixture.builder(dirTestWatcher));

    // Needed for compressed file unit test
    dirTestWatcher.copyResourceToRoot(Paths.get("messages/"));
  }

  @Test
  public void testStarQuery() throws Exception {
    String sql = "SELECT * FROM dfs.`messages/MT103.fin`";

    QueryBuilder q = client.queryBuilder().sql(sql);
    RowSet results = q.rowSet();

    TupleMetadata expectedSchema = new SchemaBuilder()
      .addNullable("message_name", TypeProtos.MinorType.VARCHAR)
      .addNullable("application_id", TypeProtos.MinorType.VARCHAR)
      .addNullable("logical_terminal_address", TypeProtos.MinorType.VARCHAR)
      .addNullable("session_number", TypeProtos.MinorType.VARCHAR)
      .addNullable("sequence_number", TypeProtos.MinorType.VARCHAR)
      .addNullable("message_type", TypeProtos.MinorType.INT)
      .addNullable("Sender's Reference", TypeProtos.MinorType.VARCHAR)
      .addNullable("Time Indication", TypeProtos.MinorType.VARCHAR)
      .addNullable("Bank Operation Code", TypeProtos.MinorType.VARCHAR)
      .addNullable("Instruction Code", TypeProtos.MinorType.VARCHAR)
      .addNullable("Value Date/Currency/Interbank Settled Amount", TypeProtos.MinorType.VARCHAR)
      .addNullable("Currency/Instructed Amount", TypeProtos.MinorType.VARCHAR)
      .addNullable("Ordering Customer", TypeProtos.MinorType.VARCHAR)
      .addNullable("Ordering Institution", TypeProtos.MinorType.VARCHAR)
      .addNullable("Sender's Correspondent", TypeProtos.MinorType.VARCHAR)
      .addNullable("Account With Institution", TypeProtos.MinorType.VARCHAR)
      .addNullable("Beneficiary Customer", TypeProtos.MinorType.VARCHAR)
      .addNullable("Details of Charges", TypeProtos.MinorType.VARCHAR)
      .addNullable("Sender to Receiver Information", TypeProtos.MinorType.VARCHAR)
      .buildSchema();

    RowSet expected =  new RowSetBuilder(client.allocator(), expectedSchema)
      .addRow("103", "F", "BICFOOYYAXXX", "8683", "497519", 103, "0061350113089908", "RNCTIME 15:34 + 0000", "CRED", "SDVA", "EUR 100,000.00 28/10/2006", "EUR 100,000", "AGENTES DE BOLSA FOO AGENCIA", "2337 FOOAESMMXXX", "FOOAESMMXXX", "BICFOOYYXXX", "ES0123456789012345671234 FOO AGENTES DE BOLSA ASOC", "OUR", "/BNF/TRANSF. BCO. FOO")
      .build();

    assertEquals(1, results.rowCount());

    new RowSetComparison(expected).verifyAndClearAll(results);
  }

  @Test
  public void testSerDe() throws Exception {
    String sql = "SELECT COUNT(*) FROM dfs.`messages/MT103.fin`";
    String plan = queryBuilder().sql(sql).explainJson();
    long cnt = queryBuilder().physical(plan).singletonLong();
    assertEquals("Counts should match", 1L, cnt);
  }

  @Test
  public void testExplicitQueryWithCompressedFile() throws Exception {
    generateCompressedFile("spss/testdata.sav", "zip", "spss/testdata.sav.zip");

    String sql = "SELECT ID, Urban, Urban_value FROM dfs.`spss/testdata.sav.zip`  WHERE d16=4";
    RowSet results = client.queryBuilder().sql(sql).rowSet();
    TupleMetadata expectedSchema = new SchemaBuilder()
      .addNullable("ID", TypeProtos.MinorType.FLOAT8)
      .addNullable("Urban", TypeProtos.MinorType.FLOAT8)
      .addNullable("Urban_value", TypeProtos.MinorType.VARCHAR)
      .buildSchema();


    RowSet expected = new RowSetBuilder(client.allocator(), expectedSchema)
      .addRow(47.0, 1.0, "Urban").addRow(53.0, 1.0, "Urban")
      .addRow(66.0, 1.0, "Urban")
      .build();

    assertEquals(3, results.rowCount());

    new RowSetComparison(expected).verifyAndClearAll(results);
  }
}
