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
import org.apache.commons.transaction.resource.ResourceException;
import org.apache.commons.transaction.resource.ResourceManager;
import org.apache.commons.transaction.resource.StreamableResource;
import org.apache.commons.transaction.util.FileHelper;

public class FileResourceManager implements ResourceManager<StreamableResource> {

    private Log logger = LogFactory.getLog(getClass());

    protected String rootPath;

    public FileResourceManager(String rootPath) {
        this.rootPath = rootPath;
    }

    public StreamableResource getResource(String path) throws ResourceException {
        return new FileResource(path);
    }

    public String getRootPath() {
        return rootPath;
    }

    protected static class FileResource implements StreamableResource {

        private File file;

        public FileResource(String path) {
            this.file = new File(path);
        }

        public FileResource(File file) {
            this.file = file;
        }

        public void createAsDirectory() throws ResourceException {
            if (!file.mkdirs()) {
                throw new ResourceException("Could not create directory");
            }

        }

        public void createAsFile() throws ResourceException {
            try {
                if (!file.createNewFile()) {
                    throw new ResourceException("Could not create file");
                }
            } catch (IOException e) {
                throw new ResourceException(e);
            }
        }

        public void delete() throws ResourceException {
            if (!file.delete())
                throw new ResourceException("Could not create file");

        }

        public boolean exists() {
            return file.exists();
        }

        public List<StreamableResource> getChildren() throws ResourceException {
            List<StreamableResource> result = new ArrayList<StreamableResource>();
            File[] files = file.listFiles();
            for (File file : files) {
                result.add(new FileResource(file));
            }
            return result;
        }

        public StreamableResource getParent() throws ResourceException {
            // FIXME: Is reasonable, but would require reference to enclosing
            // class
            /*
             * if (getPath().equals(getRootPath())) return null;
             */
            File parent = file.getParentFile();
            return new FileResource(parent);
        }

        public String getPath() throws ResourceException {
            try {
                return file.getCanonicalPath();
            } catch (IOException e) {
                throw new ResourceException(e);
            }
        }

        public boolean isDirectory() {
            return file.isDirectory();
        }

        public boolean isFile() {
            return file.isFile();
        }

        public void move(StreamableResource destination) throws ResourceException {
            if (!(destination instanceof FileResource)) {
                throw new ResourceException(
                        "Destination must be of created by FileResourceManager only!");

            }
            File to = ((FileResource) destination).getFile();
            try {
                FileHelper.moveUsingNIO(file, to);
            } catch (IOException e) {
                throw new ResourceException(e);
            }
        }

        public void copy(StreamableResource destination) throws ResourceException {
            if (!(destination instanceof FileResource)) {
                throw new ResourceException(
                        "Destination must be of created by FileResourceManager only!");

            }
            File to = ((FileResource) destination).getFile();
            try {
                FileHelper.copyUsingNIO(file, to);
            } catch (IOException e) {
                throw new ResourceException(e);
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
                FileOutputStream os = new FileOutputStream(file);
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
        public boolean tryReadLock() {
            return true;
        }

        // XXX no op, only way to lock is using FileChannel#lock() and
        // FileChannel#tryLock()
        public boolean tryWriteLock() {
            return true;
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

    }
}
