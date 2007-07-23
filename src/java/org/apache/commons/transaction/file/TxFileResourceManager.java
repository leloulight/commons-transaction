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
package org.apache.commons.transaction.file;

import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.AbstractTransactionalResourceManager;
import org.apache.commons.transaction.ManageableResourceManager;
import org.apache.commons.transaction.AbstractTransactionalResourceManager.AbstractTxContext;
import org.apache.commons.transaction.file.FileResourceManager.FileResource;
import org.apache.commons.transaction.locking.LockException;
import org.apache.commons.transaction.resource.ResourceException;
import org.apache.commons.transaction.resource.ResourceManager;
import org.apache.commons.transaction.resource.StreamableResource;

public class TxFileResourceManager extends
        AbstractTransactionalResourceManager<TxFileResourceManager.FileTxContext> implements
        ManageableResourceManager, ResourceManager<StreamableResource> {

    private Log logger = LogFactory.getLog(getClass());

    protected String contextFileName = "transaction.log";

    protected String rootPath;

    protected FileResourceManager wrapped;

    protected FileResourceUndoManager undoManager;

    public TxFileResourceManager(String rootPath) {
        this.rootPath = rootPath;
        wrapped = new FileResourceManager(rootPath);
    }

    public void setContextFileName(String contextFile) {
        this.contextFileName = contextFile;
    }

    @Override
    protected FileTxContext createContext() {
        return new FileTxContext();
    }

    // TODO resource manager needs to forward requests to this context, locking
    // will happen here
    // FIXME
    // needs
    // - custom commit / rollback
    // - proper resource tracking
    public class FileTxContext extends AbstractTxContext implements
            ResourceManager<StreamableResource> {

        // list of streams participating in this tx
        private Collection<Closeable> openStreams = new ArrayList<Closeable>();

        public FileTxContext() {
        }

        protected void registerStream(Closeable stream) {
            openStreams.add(stream);
        }

        public StreamableResource getResource(String path) throws ResourceException {
            return new TxFileResource(path);
        }

        public String getRootPath() {
            return TxFileResourceManager.this.getRootPath();
        }

        // FIXME needs custom implementations
        // Details:
        // - Hierarchical locking
        // - Calls to configured undo manager
        protected class TxFileResource extends FileResource {

            public TxFileResource(File file) {
                super(file);
            }

            public TxFileResource(String path) {
                super(path);
            }

            public void copy(String destinationpath) throws ResourceException {
                super.copy(destinationpath);
            }

            public void createAsDirectory() throws ResourceException {
                super.createAsDirectory();
            }

            public void createAsFile() throws ResourceException {
                super.createAsFile();
            }

            public void delete() throws ResourceException {
                super.delete();
            }

            public boolean exists() {
                return super.exists();
            }

            public List<StreamableResource> getChildren() throws ResourceException {
                return super.getChildren();
            }

            public StreamableResource getParent() throws ResourceException {
                return super.getParent();
            }

            public String getPath() throws ResourceException {
                return super.getPath();
            }

            public Object getProperty(String name) {
                return super.getProperty(name);
            }

            public boolean isDirectory() {
                return super.isDirectory();
            }

            public boolean isFile() {
                return super.isFile();
            }

            public void move(String destinationpath) throws ResourceException {
                super.move(destinationpath);
            }

            public InputStream readStream() throws ResourceException {
                return super.readStream();
            }

            public void removeProperty(String name) {
                super.removeProperty(name);
            }

            public void setProperty(String name, Object newValue) {
                super.setProperty(name, newValue);
            }

            public boolean tryReadLock() {
                try {
                    return getLm().tryLock(getName(), getPath(), false);
                } catch (ResourceException e) {
                    // FIXME: ouch!
                    throw new LockException(e);
                }
            }

            public boolean tryWriteLock() {
                try {
                    return getLm().tryLock(getName(), getPath(), true);
                } catch (ResourceException e) {
                    // FIXME: ouch!
                    throw new LockException(e);
                }
            }

            public void readLock() {
                try {
                    getLm().lock(getName(), getPath(), false);
                } catch (ResourceException e) {
                    // FIXME: ouch!
                    throw new LockException(e);
                }
                super.readLock();
            }

            public void writeLock() {
                try {
                    getLm().lock(getName(), getPath(), true);
                } catch (ResourceException e) {
                    // FIXME: ouch!
                    throw new LockException(e);
                }
                super.writeLock();
            }

            public OutputStream writeStream(boolean append) throws ResourceException {
                return super.writeStream(append);
            }

        }
    }

    @Override
    public boolean commitCanFail() {
        return false;
    }

    public StreamableResource getResource(String path) throws ResourceException {
        FileTxContext context = getActiveTx();
        if (context != null) {
            return context.getResource(path);
        } else {
            return wrapped.getResource(path);
        }

    }

    public String getRootPath() {
        return rootPath;
    }

    public FileResourceUndoManager getUndoManager() {
        return undoManager;
    }

    public void setUndoManager(FileResourceUndoManager undoManager) {
        this.undoManager = undoManager;
    }

}
