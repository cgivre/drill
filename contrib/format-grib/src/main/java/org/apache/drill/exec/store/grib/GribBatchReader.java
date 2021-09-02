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


package org.apache.drill.exec.store.grib;

import org.apache.drill.common.exceptions.CustomErrorContext;
import org.apache.drill.common.exceptions.UserException;
import org.apache.drill.exec.physical.impl.scan.file.FileScanFramework.FileSchemaNegotiator;
import org.apache.drill.exec.physical.impl.scan.framework.ManagedReader;
import org.apache.hadoop.mapred.FileSplit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.nc2.NetcdfFile;
import ucar.nc2.dataset.NetcdfDataset;

import java.io.IOException;
import java.io.InputStream;

public class GribBatchReader implements ManagedReader<FileSchemaNegotiator> {

  private static final Logger logger = LoggerFactory.getLogger(GribBatchReader.class);
  private final int maxRecords;
  private FileSplit split;
  private InputStream fsStream;
  private CustomErrorContext errorContext;

  @Override
  public boolean open(FileSchemaNegotiator negotiator) {
    split = negotiator.split();
    errorContext = negotiator.parentErrorContext();
    openFile(negotiator);
    return false;
  }

  private void openFile(FileSchemaNegotiator negotiator) {
    try {
      fsStream = negotiator.fileSystem().openPossiblyCompressedStream(split.getPath());

      NetcdfFile.builder()
        .setLocation(split.getPath().toString())
        .build();

    } catch (IOException e) {
      throw UserException
        .dataReadError(e)
        .message("Unable to open GRIB File %s", split.getPath())
        .addContext(e.getMessage())
        .addContext(errorContext)
        .build(logger);
    }
  }


  @Override
  public boolean next() {
    return false;
  }

  @Override
  public void close() {

  }

  public static class GribReaderConfig {

    protected final GribFormatPlugin plugin;

    public GribReaderConfig(GribFormatPlugin plugin) {
      this.plugin = plugin;
    }
  }

  public GribBatchReader(int maxRecords) {
    this.maxRecords = maxRecords;
  }



}
