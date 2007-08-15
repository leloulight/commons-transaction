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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.AbstractTransactionalResourceManager;
import org.apache.commons.transaction.ManageableResourceManager;
import org.apache.commons.transaction.AbstractTransactionalResourceManager.AbstractTxContext;
import org.apache.commons.transaction.file.FileResourceManager.FileResource;
import org.apache.commons.transaction.locking.DefaultLockManager;
import org.apache.commons.transaction.locking.HierarchicalLockManager;
import org.apache.commons.transaction.locking.DefaultHierarchicalLockManager;
import org.apache.commons.transaction.locking.LockManager;
import org.apache.commons.transaction.resource.ResourceException;
import org.apache.commons.transaction.resource.ResourceManager;
import org.apache.commons.transaction.resource.StreamableResource;
import org.apache.commons.transaction.util.FileHelper;

public class TxFileResourceManager extends
        AbstractTransactionalResourceManager<TxFileResourceManager.FileTxContext> implements
        ManageableResourceManager, ResourceManager<StreamableResource> {

    private Log logger = LogFactory.getLog(getClass());

    protected FileResourceManager wrapped;

    protected FileResourceUndoManager undoManager;

    protected HierarchicalLockManager hlm;

    public TxFileResourceManager(String name, String rootPath) {
        super(name);
        wrapped = new FileResourceManager(rootPath);
        setLm(new DefaultLockManager<Object, Object>());
    }

    @Override
    protected FileTxContext createContext() {
        return new FileTxContext();
    }

    protected HierarchicalLockManager getHLM() {
        return hlm;
    }

    @Override
    public void setLm(LockManager<Object, Object> lm) {
        super.setLm(lm);
        hlm = new DefaultHierarchicalLockManager(getRootPath(), lm);
    }

    public class FileTxContext extends AbstractTxContext implements
            ResourceManager<StreamableResource> {

        // list of streams participating in this tx
        private Collection<Closeable> openStreams = new ArrayList<Closeable>();

        protected void registerStream(Closeable stream) {
            openStreams.add(stream);
        }

        protected void closeAllStreams() {
            for (Closeable stream : openStreams) {
                try {
                    stream.close();
                } catch (IOException e) {
                    logger.warn("Could not close stream during cleaning", e);
                }
            }
        }

        @Override
        public TxFileResource getResource(String path) throws ResourceException {
            return new TxFileResource(path);
        }

        @Override
        public void start(long timeout, TimeUnit unit) {
            getUndoManager().startRecord();
            super.start(timeout, unit);
        }
        
        @Override
        public String getRootPath() {
            return TxFileResourceManager.this.getRootPath();
        }

        @Override
        public void dispose() {
            closeAllStreams();
            getUndoManager().forgetRecord();
            super.dispose();
        }

        @Override
        public void commit() {
            super.commit();
        }

        @Override
        public void rollback() {
            getUndoManager().undoRecord();
            super.rollback();
        }

        protected class TxFileResource extends FileResource {

            public TxFileResource(File file) throws ResourceException {
                super(file);
            }

            public TxFileResource(String path) throws ResourceException {
                super(path);
            }

            public void createAsDirectory() throws ResourceException {
                writeLock();
                getUndoManager().recordCreate(getFile());
                super.createAsDirectory();
            }

            public void createAsFile() throws ResourceException {
                writeLock();
                getUndoManager().recordCreate(getFile());
                super.createAsFile();
            }

            protected TxFileResource create(File file) throws ResourceException {
                return new TxFileResource(file);
            }

            protected void mkdirs() throws ResourceException {
                if (exists())
                    return;
                TxFileResource parent = getParent();
                if (parent != null) {
                    parent.mkdirs();
                }
                createAsDirectory();
            }

            protected void moveOrcopyRecursive(TxFileResource target, boolean move)
                    throws ResourceException {
                moveOrCopySaneCheck(target);

                if (isDirectory()) {
                    target.mkdirs();
                    for (TxFileResource child : getChildren()) {
                        TxFileResource targetChild = target.getChild(child.getName());
                        if (child.isDirectory()) {
                            targetChild.mkdirs();
                            child.moveOrcopyRecursive(targetChild, move);
                            targetChild.createAsDirectory();
                        } else {
                            if (targetChild.exists()) {
                                targetChild.removeNonRecursive();
                            }
                            moveOrcopyRecursive(targetChild, move);
                        }
                    }
                } else {
                    // isFile()!
                    if (!target.exists()) {
                        target.getParent().mkdirs();
                        target.createAsFile();
                    }
                    if (!move) {
                        copyNonRecursive(target);
                    } else {
                        moveNonRecursive(target);
                    }
                }
            }

            // recursively delete, depth first
            protected void removeRecursive() throws ResourceException {
                if (exists()) {
                    if (isDirectory()) {
                        List<? extends TxFileResource> children = getChildren();
                        for (TxFileResource resource : children) {
                            resource.delete();
                        }
                    }
                    removeNonRecursive();
                }
            }

            protected void copyNonRecursive(TxFileResource target) throws ResourceException {
                readLock();
                target.writeLock();
                getUndoManager().recordCreate(target.getFile());
                try {
                    FileHelper.copy(getFile(), target.getFile());
                } catch (IOException e) {
                    throw new ResourceException(ResourceException.Code.CANT_MOVE_OR_COPY, e);
                }
            }

            protected void moveNonRecursive(TxFileResource target) throws ResourceException {
                writeLock();
                target.writeLock();
                getUndoManager().recordDelete(getFile());
                getUndoManager().recordCreate(target.getFile());
                try {
                    FileHelper.move(getFile(), target.getFile());
                } catch (IOException e) {
                    throw new ResourceException(ResourceException.Code.CANT_MOVE_OR_COPY, e);
                }
            }

            protected void removeNonRecursive() throws ResourceException {
                writeLock();
                getUndoManager().recordDelete(getFile());
                if (!getFile().delete()) {
                    throw new ResourceException(ResourceException.Code.COULD_NOT_DELETE);
                }
            }

            public void delete() throws ResourceException {
                removeRecursive();
            }

            public boolean exists() {
                readLock();
                return super.exists();
            }

            public TxFileResource getChild(String name) throws ResourceException {
                readLock();
                File child = new File(getFile(), name);
                return create(child);
            }

            public List<? extends TxFileResource> getChildren() throws ResourceException {
                readLock();

                List<FileResource> children = (List<FileResource>) super.getChildren();
                List<TxFileResource> txChildren = new ArrayList<TxFileResource>();
                // if we have a list of chidren we do not want them to suddenly
                // vanish
                for (FileResource resource : children) {
                    TxFileResource txFileResource = new TxFileResource(resource.getFile());
                    txFileResource.readLock();
                    txChildren.add(txFileResource);
                }
                return txChildren;
            }

            public TxFileResource getParent() throws ResourceException {
                readLock();
                FileResource parent = super.getParent();
                if (parent == null)
                    return null;
                TxFileResource txFileParent = new TxFileResource(parent.getFile());
                // if we acquire the parent, we want to be sure it really exist
                txFileParent.readLock();
                return txFileParent;
            }

            public String getPath() {
                return super.getPath();
            }

            public Object getProperty(String name) {
                readLock();
                return super.getProperty(name);
            }

            public boolean isDirectory() {
                readLock();
                return super.isDirectory();
            }

            public boolean isFile() {
                readLock();
                return super.isFile();
            }

            public void copy(TxFileResource destination) throws ResourceException {
                moveOrcopyRecursive(destination, false);
            }

            public void move(TxFileResource destination) throws ResourceException {
                moveOrcopyRecursive(destination, true);
            }

            public InputStream readStream() throws ResourceException {
                readLock();
                InputStream is = super.readStream();
                registerStream(is);
                return is;
            }

            public OutputStream writeStream(boolean append) throws ResourceException {
                writeLock();
                getUndoManager().recordUpdate(getFile());
                OutputStream os = super.writeStream(append);
                registerStream(os);
                return os;
            }

            public void removeProperty(String name) {
                // no need to handle, as we do not support this
                super.removeProperty(name);
            }

            public void setProperty(String name, Object newValue) {
                // no need to handle, as we do not support this
                super.setProperty(name, newValue);
            }

            public void readLock() {
                getHLM().lockInHierarchy(TxFileResourceManager.this.getName(), getPath(), false);
                super.readLock();
            }

            public void writeLock() {
                getHLM().lockInHierarchy(TxFileResourceManager.this.getName(), getPath(), true);
                super.writeLock();
            }
        }
    }

    @Override
    public boolean commitCanFail() {
        return false;
    }

    public FileResource getResource(String path) throws ResourceException {
        FileTxContext context = getActiveTx();
        if (context != null) {
            return context.getResource(path);
        } else {
            return wrapped.getResource(path);
        }

    }

    public String getRootPath() {
        return wrapped.getRootPath();
    }

    protected FileResourceUndoManager getUndoManager() {
        return undoManager;
    }

    public void setUndoManager(FileResourceUndoManager undoManager) {
        this.undoManager = undoManager;
    }

}
