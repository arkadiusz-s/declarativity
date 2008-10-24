/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.dfs;

import java.io.IOException;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.WritableComparator;

class StringBytesWritable extends BytesWritable {

  StringBytesWritable() {
    super();
  }

  /*StringBytesWritable(byte[] bytes) {
    super(bytes);
  }*/

  /**
   * Create a BytesWritable by extracting bytes from the input string.
   */
  StringBytesWritable(String str) throws IOException {
    super(str.getBytes("UTF8"));
  }

  /**
   * Convert BytesWritable to a String.
   */
  String getString() throws IOException {
    return new String(get(),"UTF8");
  }

  /** {@inheritDoc} */
  public String toString() {
    try {
      return getString();
    } catch (IOException e) {
      throw (RuntimeException)new RuntimeException().initCause(e);
    }
  }

  /**
   * Compare to a String.
   */
  boolean equals(String str) throws IOException {
    return WritableComparator.compareBytes(get(), 0, getSize(), 
                                   str.getBytes("UTF8"), 0, str.length()) == 0;
  }
}
