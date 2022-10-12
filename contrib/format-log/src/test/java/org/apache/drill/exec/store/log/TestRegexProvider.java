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

package org.apache.drill.exec.store.log;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestRegexProvider {

  @Test
  public void testSingleRegex() {
    LogRegexProvider regexProvider = new LogRegexProvider("^(\\d{4}-\\d{2}-\\d{2}) (\\w+)$", 2);
    assertTrue(regexProvider.getMatcher("2020-01-01 test").find());
    assertEquals(0, regexProvider.getFieldOffset());

    assertTrue(regexProvider.getMatcher("2020-02-01 again").find());
    assertEquals(0, regexProvider.getFieldOffset());

    assertTrue(regexProvider.getMatcher("2020-03-01 yup").find());
    assertEquals(0, regexProvider.getFieldOffset());

    assertTrue(regexProvider.getMatcher("2020-04-01 test").find());
    assertEquals(0, regexProvider.getFieldOffset());
  }

  @Test
  public void testMultilineRegex() {
    LogRegexProvider regexProvider = new LogRegexProvider("^(\\d{4}-\\d{2}-\\d{2}) (\\w+)\\n(\\d{5})", 3);
    assertTrue(regexProvider.getMatcher("2020-01-01 test").find());
    assertEquals(0, regexProvider.getFieldOffset());

    assertTrue(regexProvider.getMatcher("12345").find());
    assertEquals(2, regexProvider.getFieldOffset());

    assertTrue(regexProvider.getMatcher("2020-03-01 yup").find());
    assertEquals(0, regexProvider.getFieldOffset());

    assertTrue(regexProvider.getMatcher("98765").find());
    assertEquals(2, regexProvider.getFieldOffset());
  }
}
