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


package org.apache.drill.exec.store.netcdf;

import org.apache.drill.common.AutoCloseables;
import org.apache.drill.common.exceptions.CustomErrorContext;
import org.apache.drill.common.exceptions.UserException;
import org.apache.drill.exec.physical.impl.scan.v3.ManagedReader;
import org.apache.drill.exec.physical.impl.scan.v3.file.FileDescrip;
import org.apache.drill.exec.physical.impl.scan.v3.file.FileSchemaNegotiator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ucar.nc2.NetcdfFile;
import ucar.nc2.dataset.NetcdfDataset;

import java.io.IOException;
import java.io.InputStream;

public class NetCDFBatchReader implements ManagedReader {

  private static final Logger logger = LoggerFactory.getLogger(NetCDFBatchReader.class);
  private final FileDescrip file;
  private final NetcdfFile netcdfFile;
  private final InputStream fsStream;
  private final CustomErrorContext errorContext;

  public NetCDFBatchReader(FileSchemaNegotiator negotiator) {
    file = negotiator.file();
    errorContext = negotiator.parentErrorContext();

    try {
      fsStream = file.fileSystem().openPossiblyCompressedStream(file.split().getPath());

      netcdfFile = NetcdfFile.builder()
          .setLocation(file.filePath().toString())
          .build();

    } catch (IOException e) {
      throw UserException
        .dataReadError(e)
        .message("Unable to open NetCDF File %s", file.filePath())
        .addContext(e.getMessage())
        .addContext(errorContext)
        .build(logger);
    }

    // Now build the schema
    
  }


  @Override
  public boolean next() {
    return false;
  }

  @Override
  public void close() {
    AutoCloseables.closeSilently(fsStream);
    if (netcdfFile != null) {
      try {
        netcdfFile.close();
      } catch (IOException e) {
        throw UserException.dataReadError(e)
            .message("Error closing file: " + file.filePath().getName())
            .addContext(errorContext)
            .build(logger);
      }
    }
  }
}
