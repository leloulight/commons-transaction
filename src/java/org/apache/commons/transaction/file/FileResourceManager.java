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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.TransactionException;
import org.apache.commons.transaction.resource.ResourceException;
import org.apache.commons.transaction.resource.ResourceManager;
import org.apache.commons.transaction.resource.StreamableResource;
import org.apache.commons.transaction.util.FileHelper;

/**
 * Default file system implementation of a {@link ResourceManager resource manager}.
 * 
 * <p>
 * This implementation is <b>NOT</b> <em>thread-safe</em>. Use
 * {@link TxFileResourceManager} if you require a <em>thread-safe</em>
 * implementation.
 */
public class FileResourceManager implements ResourceManager<FileResourceManager.FileResource> {

    private Log logger = LogFactory.getLog(getClass());

    protected String rootPath;

    public FileResourceManager(String rootPath) {
        try {
            File file = new File(rootPath);
            file.mkdirs();
            this.rootPath = file.getCanonicalPath();
        } catch (IOException e) {
            throw new TransactionException(e);
        }
    }

    public FileResourceManager.FileResource getResource(String path) throws ResourceException {
        return new FileResource(path);
    }

    public String getRootPath() {
        return rootPath;
    }

    protected static class FileResource implements StreamableResource {

        private final File file;

        private final String canonicalPath;

        private final String name;

        protected static File getFileForResource(StreamableResource resource)
                throws ResourceException {
            if (!(resource instanceof FileResource)) {
                throw new ResourceException(
                        "Destination must be of created by FileResourceManager only!");

            }
            return ((FileResource) resource).getFile();
        }

        public FileResource(String path) throws ResourceException {
            this(new File(path.trim()));
        }

        public FileResource(File file) throws ResourceException {
            this.file = file;
            try {
                this.canonicalPath = file.getCanonicalPath();
            } catch (IOException e) {
                throw new ResourceException(e);
            }
            this.name = file.getName();
        }

        public void createAsDirectory() throws ResourceException {
            if (!file.exists() && !file.mkdirs()) {
                throw new ResourceException(ResourceException.Code.COULD_NOT_CREATE,
                        "Could not create directory");
            }

        }

        public void createAsFile() throws ResourceException {
            try {
                if (!file.createNewFile()) {
                    throw new ResourceException(ResourceException.Code.COULD_NOT_CREATE,
                            "Could not create file");
                }
            } catch (IOException e) {
                throw new ResourceException(e);
            }
        }

        public void delete() throws ResourceException {
            if (exists()) {
                if (!isDirectory()) {
                    if (!getFile().delete())
                        throw new ResourceException(ResourceException.Code.COULD_NOT_DELETE,
                                "Could not create file");
                } else {
                    FileHelper.removeRecursive(getFile());
                }
            }
        }

        public boolean exists() {
            return file.exists();
        }

        public List<? extends FileResource> getChildren() throws ResourceException {
            List<FileResource> result = new ArrayList<FileResource>();
            File[] files = file.listFiles();
            for (File file : files) {
                result.add(create(file));
            }
            return result;
        }

        public FileResource getParent() throws ResourceException {
            // FIXME: Is reasonable, but would require reference to enclosing
            // class
            /*
             * if (getPath().equals(getRootPath())) return null;
             */
            File parent = file.getParentFile();
            if (parent == null)
                return null;
            return create(parent);
        }

        public String getPath() {
            return canonicalPath;
        }

        public boolean isDirectory() {
            return file.isDirectory();
        }

        public boolean isFile() {
            return file.isFile();
        }

        public void move(StreamableResource destination) throws ResourceException {
            if (!prepareMoveorCopy(destination))
                moveOrCopySaneCheck(destination);
            try {
                if (isFile()) {
                    FileHelper.move(file, getFileForResource(destination));
                } else {
                    FileHelper.moveRecursive(file, getFileForResource(destination));
                }
            } catch (IOException e) {
                throw new ResourceException(e);
            }
        }

        public void copy(StreamableResource destination) throws ResourceException {
            if (!prepareMoveorCopy(destination))
                moveOrCopySaneCheck(destination);
            try {
                if (isFile()) {
                    FileHelper.copy(file, getFileForResource(destination));
                } else {
                    FileHelper.copyRecursive(file, getFileForResource(destination));
                }
            } catch (IOException e) {
                throw new ResourceException(e);
            }
        }

        protected boolean prepareMoveorCopy(StreamableResource destination)
                throws ResourceException {
            if (!destination.exists()) {
                if (isDirectory()) {
                    destination.createAsDirectory();
                } else {
                    destination.createAsFile();
                }
                return true;
            }
            return false;
        }

        protected void moveOrCopySaneCheck(StreamableResource destination) throws ResourceException {

            File from = getFile();
            File to = getFileForResource(destination);
            if (!from.isDirectory()) {
                if (to.isDirectory()) {
                    throw new ResourceException(ResourceException.Code.CANT_MOVE_OR_COPY,
                            "Could not move file to directory");
                }
                // still need to check, as it can also be a link
            } else if (from.isDirectory()) {
                if (to.isFile()) {
                    throw new ResourceException(ResourceException.Code.CANT_MOVE_OR_COPY,
                            "Could not move directory to file");
                }
            }
        }

        public InputStream readStream() throws ResourceException {
            try {
                FileInputStream is = new FileInputStream(file);
                return is;
            } catch (IOException e) {
                throw new ResourceException(e);
            }
        }

        public OutputStream writeStream(boolean append) throws ResourceException {
            try {
                FileOutputStream os = new FileOutputStream(file, append);
                return os;
            } catch (IOException e) {
                throw new ResourceException(e);
            }
        }

        public void removeProperty(String name) {
            throw new UnsupportedOperationException("You can not remove properties from files!");
        }

        public void setProperty(String name, Object newValue) {
            throw new UnsupportedOperationException("You can not set properties on files!");
        }

        public Object getProperty(String name) {
            if (name.equals("lastModified")) {
                return file.lastModified();
            }
            if (name.equals("length")) {
                return file.length();
            }
            return null;
        }

        // XXX no op, only way to lock is using FileChannel#lock() and
        // FileChannel#tryLock()
        public void readLock() {
        }

        // XXX no op, only way to lock is using FileChannel#lock() and
        // FileChannel#tryLock()
        public void writeLock() {
        }

        protected File getFile() {
            return file;
        }

        protected FileResource create(File file) throws ResourceException {
            return new FileResource(file);
        }

        public FileResource getChild(String name) throws ResourceException {
            File child = new File(file, name);
            return create(child);
        }

        public String getName() {
            return name;
        }

    }
}
