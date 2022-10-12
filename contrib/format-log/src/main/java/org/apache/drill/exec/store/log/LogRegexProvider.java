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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LogRegexProvider {
  private static final Pattern NEWLINE_PATTERN = Pattern.compile("\\\\n");
  private final List<Pattern> patternList;
  private final List<Integer> offsetList;
  private final int fieldCount;
  private int currentIndex;
  private int offsetIndex;
  private int fieldOffset;

  public LogRegexProvider(String rawRegex, int fieldCount) {
    this.fieldCount = fieldCount;
    this.currentIndex = 0;
    this.offsetIndex = -1;
    this.fieldOffset = 0;
    this.patternList = new ArrayList<>();
    this.offsetList = new ArrayList<>();

    // Split the regex by newline character

    List<String> regexList = NEWLINE_PATTERN.splitAsStream(rawRegex).collect(Collectors.toList());

    for (String regex : regexList) {
      Pattern pattern = Pattern.compile(regex);
      patternList.add(pattern);

      // Populate offset list
      Matcher matcher = pattern.matcher("");
      offsetList.add(matcher.groupCount());
    }
  }

  /**
   * Gets the current matcher.
   * @param line A {@link String} containing the current line.
   * @return A {@link Matcher} object corresponding to the current line.
   */
  public Matcher getMatcher(String line) {
    return getPattern().matcher(line);
  }

  public int getFieldOffset() {
    return fieldOffset;
  }

  private Pattern getPattern() {
    Pattern pattern =  patternList.get(currentIndex);
    currentIndex++;

    if (offsetIndex < 0) {
      fieldOffset = 0;
    } else {
      fieldOffset = offsetList.get(offsetIndex);
    }
    offsetIndex++;

    // Reset the regex index and offset
    if (patternList.size() <= currentIndex) {
      currentIndex = 0;
      offsetIndex = -1;
    }
    return pattern;
  }
}
