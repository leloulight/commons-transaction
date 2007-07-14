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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.AbstractTransactionalResource;
import org.apache.commons.transaction.TransactionalResource;
import org.apache.commons.transaction.AbstractTransactionalResource.AbstractTxContext;
import org.apache.commons.transaction.locking.LockException;
import org.apache.commons.transaction.util.FileHelper;

public class TxFileResourceManager extends
        AbstractTransactionalResource<TxFileResourceManager.FileTxContext> implements
        FileResourceManager {

    private Log logger = LogFactory.getLog(getClass());

    protected String contextFileName = "transaction.log";

    protected PathManager idMapper;

    protected TransactionalResource tm;

    public static void applyDeletes(File removeDir, File targetDir, File rootDir)
            throws IOException {
        if (removeDir.isDirectory() && targetDir.isDirectory()) {
            File[] files = removeDir.listFiles();
            for (int i = 0; i < files.length; i++) {
                File removeFile = files[i];
                File targetFile = new File(targetDir, removeFile.getName());
                if (!removeFile.isDirectory()) {
                    if (targetFile.exists()) {
                        if (!targetFile.delete()) {
                            throw new IOException("Could not delete file " + removeFile.getName()
                                    + " in directory targetDir");
                        }
                    } else if (!targetFile.isFile()) {
                        // this is likely a dangling link
                        targetFile.delete();
                    }
                    // indicate, this has been done
                    removeFile.delete();
                } else {
                    applyDeletes(removeFile, targetFile, rootDir);
                }
                // delete empty target directories, except root dir
                if (!targetDir.equals(rootDir) && targetDir.list().length == 0) {
                    targetDir.delete();
                }
            }
        }
    }

    public String getContextFileName() {
        return contextFileName;
    }

    public void setContextFileName(String contextFile) {
        this.contextFileName = contextFile;
    }

    public PathManager getIdMapper() {
        return idMapper;
    }

    public void setIdMapper(PathManager idMapper) {
        this.idMapper = idMapper;
    }

    @Override
    protected FileTxContext createContext() {
        return new FileTxContext();
    }

    // TODO resource manager needs to forward requests to this context, locking
    // will happen here
    public class FileTxContext extends AbstractTxContext implements FileResourceManager {

        // list of streams participating in this tx
        private Collection<Closeable> openStreams = new ArrayList<Closeable>();

        public FileTxContext() {
            super();
            String changeDir = getIdMapper().getChangeBaseDir();
            String deleteDir = getIdMapper().getDeleteBaseDir();

            new File(changeDir).mkdirs();
            new File(deleteDir).mkdirs();
        }

        public void commit() {
            super.commit();
            String changeDir = getIdMapper().getChangeBaseDir();
            String deleteDir = getIdMapper().getDeleteBaseDir();
            String storeDir = getIdMapper().getStoreDir();

            try {
                applyDeletes(new File(deleteDir), new File(storeDir), new File(storeDir));
                FileHelper.moveRec(new File(changeDir), new File(storeDir));
            } catch (IOException e) {
                throw new LockException(LockException.Code.COMMIT_FAILED, e);
            }
        }

        public void cleanUp() {
            String baseDir = getIdMapper().getTransactionBaseDir();
            FileHelper.removeRec(new File(baseDir));
        }

        public boolean copy(String sourceId, String destinationId) throws IOException,
                LockException {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean createDir(String id) throws IOException, LockException {
            // TODO Auto-generated method stub
            return false;
        }

        public boolean move(String sourceId, String destinationId) throws IOException,
                LockException {
            // TODO Auto-generated method stub
            return false;
        }

        public InputStream read(String id) throws IOException, LockException {
            readLock(id);
            String path = getIdMapper().getPathForRead(id);
            InputStream is = new FileInputStream(new File(path));
            registerStream(is);
            return is;
        }

        public OutputStream write(String id) throws IOException {
            writeLock(id);
            String path = getIdMapper().getPathForRead(id);
            return new FileOutputStream(new File(path));
        }

        public boolean remove(String id) throws IOException {
            writeLock(id);
            String path = getIdMapper().getPathForDelete(id);
            return new File(path).delete();
        }

        public boolean create(String id) throws IOException {
            writeLock(id);
            String path = getIdMapper().getPathForDelete(id);
            return new File(path).createNewFile();
        }

        public boolean removeDir(String id) throws IOException, LockException {
            // TODO Auto-generated method stub
            return false;
        }

        protected void registerStream(Closeable stream) {
            openStreams.add(stream);
        }

    }

}
