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

import static org.junit.Assert.assertEquals;
import static org.apache.drill.test.QueryTestUtil.generateCompressedFile;

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
    results.print();


    /*TupleMetadata expectedSchema = new SchemaBuilder()
      .addNullable("ID", TypeProtos.MinorType.FLOAT8)
      .addNullable("Urban", TypeProtos.MinorType.FLOAT8)
      .addNullable("Urban_value", TypeProtos.MinorType.VARCHAR)
      .buildSchema();


    RowSet expected = new RowSetBuilder(client.allocator(), expectedSchema)
      .addRow(47.0, 1.0, "Urban").addRow(53.0, 1.0, "Urban")
      .addRow(66.0, 1.0, "Urban")
      .build();*/

    assertEquals(1, results.rowCount());

    //new RowSetComparison(expected).verifyAndClearAll(results);
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
