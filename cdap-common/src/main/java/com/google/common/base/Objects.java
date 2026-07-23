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

package com.google.common.base;

public final class Objects extends ExtraObjectsMethodsForWeb {
  private Objects() {}

  public static boolean equal(Object a, Object b) {
    return java.util.Objects.equals(a, b);
  }

  public static int hashCode(Object... objects) {
    return java.util.Arrays.hashCode(objects);
  }

  public static ToStringHelper toStringHelper(Object self) {
    return new ToStringHelper(MoreObjects.toStringHelper(self));
  }

  public static ToStringHelper toStringHelper(Class<?> clazz) {
    return new ToStringHelper(MoreObjects.toStringHelper(clazz));
  }

  public static ToStringHelper toStringHelper(String className) {
    return new ToStringHelper(MoreObjects.toStringHelper(className));
  }

  public static final class ToStringHelper {
    private final MoreObjects.ToStringHelper delegate;

    private ToStringHelper(MoreObjects.ToStringHelper delegate) {
      this.delegate = delegate;
    }

    public ToStringHelper add(String name, Object value) {
      delegate.add(name, value);
      return this;
    }

    public ToStringHelper add(String name, boolean value) {
      delegate.add(name, value);
      return this;
    }

    public ToStringHelper add(String name, char value) {
      delegate.add(name, value);
      return this;
    }

    public ToStringHelper add(String name, double value) {
      delegate.add(name, value);
      return this;
    }

    public ToStringHelper add(String name, float value) {
      delegate.add(name, value);
      return this;
    }

    public ToStringHelper add(String name, int value) {
      delegate.add(name, value);
      return this;
    }

    public ToStringHelper add(String name, long value) {
      delegate.add(name, value);
      return this;
    }

    public ToStringHelper addValue(Object value) {
      delegate.addValue(value);
      return this;
    }

    public ToStringHelper addValue(boolean value) {
      delegate.addValue(value);
      return this;
    }

    public ToStringHelper addValue(char value) {
      delegate.addValue(value);
      return this;
    }

    public ToStringHelper addValue(double value) {
      delegate.addValue(value);
      return this;
    }

    public ToStringHelper addValue(float value) {
      delegate.addValue(value);
      return this;
    }

    public ToStringHelper addValue(int value) {
      delegate.addValue(value);
      return this;
    }

    public ToStringHelper addValue(long value) {
      delegate.addValue(value);
      return this;
    }

    public ToStringHelper omitNullValues() {
      delegate.omitNullValues();
      return this;
    }

    @Override
    public String toString() {
      return delegate.toString();
    }
  }
}
