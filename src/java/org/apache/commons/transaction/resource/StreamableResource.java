/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.transaction.resource;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public interface StreamableResource {
    String getPath();

    boolean isDirectory();

    boolean isFile();

    List<? extends StreamableResource> getChildren() throws ResourceException;

    StreamableResource getParent() throws ResourceException;

    InputStream readStream() throws ResourceException;

    OutputStream writeStream(boolean append) throws ResourceException;

    void delete() throws ResourceException;

    void move(StreamableResource destination) throws ResourceException;

    void copy(StreamableResource destination) throws ResourceException;

    boolean exists();

    void createAsDirectory() throws ResourceException;

    void createAsFile() throws ResourceException;

    Object getProperty(String name);

    void setProperty(String name, Object newValue);
    void removeProperty(String name);
    
    void readLock();

    void writeLock();
}
