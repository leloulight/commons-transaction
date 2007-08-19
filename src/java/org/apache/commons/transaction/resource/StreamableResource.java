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
     * @return a list of children (empty if this is a file), never
     *         <code>null</code>
     * @throws ResourceException
     *             in case anything goes fatally wrong
     */
    List<? extends StreamableResource> getChildren() throws ResourceException;

    /**
     * Gets the parent of this resource, i.e. the directory this resource
     * resides in or <code>null</code> if this is the root folder.
     * 
     * @return the parent directory or <code>null</code> if there is none
     * @throws ResourceException
     *             in case anything goes fatally wrong
     */
    StreamableResource getParent() throws ResourceException;

    /**
     * Gets a specific child of this resource. This method returns a resource
     * even if it does not exist.
     * 
     * @param name
     *            the name of the child resource
     * @return the resource object - even if it does not exist
     * @throws ResourceException
     *             in case anything goes fatally wrong
     */
    StreamableResource getChild(String name) throws ResourceException;

    /**
     * Gets the input stream associated to this resource. You are responsible
     * for closing the stream after using it.
     * 
     * @return the input stream for reading
     * @throws ResourceException
     *             in case anything goes fatally wrong
     */
    InputStream readStream() throws ResourceException;

    /**
     * Gets the output stream associated to this resource. You are responsible
     * for closing the stream after using it.
     * 
     * @param append
     *            determines whether you append to the existing content
     * @return the output stream for writing
     * @throws ResourceException
     *             in case anything goes fatally wrong
     */
    OutputStream writeStream(boolean append) throws ResourceException;

    /**
     * <em>Physically</em> deletes this resource.
     * 
     * @throws ResourceException
     *             in case anything goes fatally wrong or you have not been able
     *             to delete the resource
     */
    void delete() throws ResourceException;

    /**
     * Moves (or renames) this resource to a new one. If nothing goes wrong this
     * resource will be deleted afterwards.
     * 
     * @param destination
     *            the new resource
     * @throws ResourceException
     *             in case anything goes fatally wrong or you have not been able
     *             to move this resource
     */
    void move(StreamableResource destination) throws ResourceException;

    /**
     * Copies this resource to a new one. If nothing goes wrong there will be
     * two existing resources afterwards.
     * 
     * @param destination
     *            the new resource
     * @throws ResourceException
     *             in case anything goes fatally wrong or you have not been able
     *             to copy this resource
     */
    void copy(StreamableResource destination) throws ResourceException;

    /**
     * Checks if this resource <em>physically</em> exists.
     * 
     * @return <code>true</code> if it exists
     */
    boolean exists();

    /**
     * Creates this resource or a <em>physical</em> directory. In case there
     * already is a physical resource having the same name, this request will
     * fail and throw an exception.
     * 
     * @throws ResourceException
     *             in case anything goes fatally wrong or you have not been able
     *             to create this resource as a directory
     */
    void createAsDirectory() throws ResourceException;

    /**
     * Creates this resource or a <em>physical</em> file. In case there
     * already is a physical resource having the same name, this request will
     * fail and throw an exception.
     * 
     * @throws ResourceException
     *             in case anything goes fatally wrong or you have not been able
     *             to create this resource as a file
     */
    void createAsFile() throws ResourceException;

    /**
     * Retrieves a specific property. Which properties there are and whether you
     * can create new ones depends on the specific implementation.
     * 
     * @param name
     *            the name of the property
     * @return the value of the property or <code>null</code> if there is no
     *         such property
     */
    Object getProperty(String name);

    /**
     * Sets a specific property. Not all implementations support his operation.
     * If unsupported an exception in thrown.
     * 
     * @param name
     *            the name of the property
     * @param newValue
     *            the new value
     * @throws UnsupportedOperationException
     *             if this operation is not supported by the specific
     *             implementation
     */

    void setProperty(String name, Object newValue) throws UnsupportedOperationException;

    /**
     * Removes a specific property. Not all implementations support his
     * operation. If unsupported an exception in thrown.
     * 
     * @param name
     *            the name of the property
     * @throws UnsupportedOperationException
     *             if this operation is not supported by the specific
     *             implementation
     */
    void removeProperty(String name) throws UnsupportedOperationException;

    /**
     * Explicitly sets a read lock on this resource. This means no other thread
     * can write to this resource while this read lock is held. Other threads
     * are allowed to set further read locks, though.
     * 
     * <p>
     * Not all implementations support his operation as it only makes sense in a
     * transactional environment. There is no <code>unlock</code> operation as
     * the release of all locks has to be controlled by the transaction manager.
     * 
     * <p>
     * If unsupported nothing will happen.
     */
    void readLock();

    /**
     * Explicitly sets a write lock on this resource. This means no other thread
     * can neither write nor read to this resource while this write lock is
     * held.
     * 
     * <p>
     * Not all implementations support his operation as it only makes sense in a
     * transactional environment. There is no <code>unlock</code> operation as
     * the release of all locks has to be controlled by the transaction manager.
     * 
     * <p>
     * If unsupported nothing will happen.
     */
    void writeLock();
}
