/*
 * Copyright (C) 2007 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.io;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public final class Closeables {
  private static final Logger logger = Logger.getLogger(Closeables.class.getName());

  private Closeables() {}

  public static void close(@Nullable Closeable closeable, boolean swallowIOException) throws IOException {
    if (closeable == null) {
      return;
    }
    try {
      closeable.close();
    } catch (IOException e) {
      if (swallowIOException) {
        logger.log(Level.WARNING, "IOException thrown while closing Closeable.", e);
      } else {
        throw e;
      }
    }
  }

  public static void closeQuietly(@Nullable Closeable closeable) {
    if (closeable == null) {
      return;
    }
    try {
      close(closeable, true);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "IOException should not have been thrown.", e);
    }
  }

  public static void closeQuietly(@Nullable InputStream inputStream) {
    try {
      close(inputStream, true);
    } catch (IOException impossible) {
      throw new AssertionError(impossible);
    }
  }

  public static void closeQuietly(@Nullable Reader reader) {
    try {
      close(reader, true);
    } catch (IOException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
