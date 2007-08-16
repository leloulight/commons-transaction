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

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Interface for a resource that has a stream and properties. The resource is
 * organized in a hierarchy.
 * 
 * <p>
 * This can be a direct match for {@link File}.
 * 
 */
public interface StreamableResource {

    /**
     * Gets the full path of this resource
     * 
     * @return the full path
     */
    String getPath();

    /**
     * Gets the name, i.e. the last segment of the {@link #getPath() path}.
     * 
     * @return the name
     */
    String getName();

    /**
     * Checks whether this resource is a directory, i.e. whether it can have
     * children. Note that a resource can be both a directory and a file.
     * 
     * @return <code>true</code> if this resource can have children
     */
    boolean isDirectory();

    /**
     * Checks whether this resource is a file, i.e. whether it contains a
     * content stream. Note that a resource can be both a directory and a file.
     * 
     * @return <code>true</code> if this resource contains a content stream
     */
    boolean isFile();

    /**
     * Gets the children of the resource.
     * 
     * @return a list of children (empty if this is a file), never <code>null</code>
     * @throws ResourceException in case anything goes fatally wrong
     */
    List<? extends StreamableResource> getChildren() throws ResourceException;

    StreamableResource getParent() throws ResourceException;

    StreamableResource getChild(String name) throws ResourceException;

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
