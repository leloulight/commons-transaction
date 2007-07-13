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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.AbstractTxContext;
import org.apache.commons.transaction.Status;
import org.apache.commons.transaction.TransactionalResource;
import org.apache.commons.transaction.TxContext;
import org.apache.commons.transaction.locking.LockException;
import org.apache.commons.transaction.locking.LockPolicy;
import org.apache.commons.transaction.util.FileHelper;

public class TxFileResourceManager implements FileResourceManager {

    private Log logger = LogFactory.getLog(getClass());

    protected String contextFileName = "transaction.log";

    protected PathManager idMapper;
    
    protected TransactionalResource tm;

    public static void applyDeletes(File removeDir, File targetDir, File rootDir) throws IOException {
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

    public InputStream read(String id) throws IOException {
            getLockPolicy().readLock(id, null);
        String path = getIdMapper().getPathForRead(id);
        return new FileInputStream(new File(path));
    }

    public OutputStream write(String id) throws IOException {
            getLockPolicy().writeLock(id, null);
        String path = getIdMapper().getPathForRead(id);
        return new FileOutputStream(new File(path));
    }

    public boolean remove(String id) throws IOException {
            getLockPolicy().writeLock(id, null);
        String path = getIdMapper().getPathForDelete(id);
        return new File(path).delete();
    }

    public boolean create(String id) throws IOException {
            getLockPolicy().writeLock(id, null);
        String path = getIdMapper().getPathForDelete(id);
        return new File(path).createNewFile();
    }

    public PathManager getIdMapper() {
        return idMapper;
    }

    public void setIdMapper(PathManager idMapper) {
        this.idMapper = idMapper;
    }

    public boolean copy(String sourceId, String destinationId) throws IOException {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean createDir(String id) throws IOException {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean move(String sourceId, String destinationId) throws IOException {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean removeDir(String id) throws IOException {
        // TODO Auto-generated method stub
        return false;
    }
    
    // TODO resource manager needs to forward requests to this context, locking will happen here
    public class FileTxContext extends AbstractTxContext implements TxContext, FileResourceManager {
        
        // list of streams participating in this tx
        private List openResources = new ArrayList();

        public FileTxContext() {
            super();
            String changeDir = getIdMapper().getChangeBaseDir();
            String deleteDir = getIdMapper().getDeleteBaseDir();

            new File(changeDir).mkdirs();
            new File(deleteDir).mkdirs();

            saveState();
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

        public void saveState() {
            String statePath = getIdMapper().getTransactionBaseDir() + "/" + getContextFileName();
            File file = new File(statePath);
            BufferedWriter writer = null;
            try {
                OutputStream os = new FileOutputStream(file);
                writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
                writer.write(toString());
            } catch (FileNotFoundException e) {
                String msg = "Saving status information to '" + statePath + "' failed! Could not create file";
                logger.fatal(msg, e);
                throw new Error(msg, e);
            } catch (IOException e) {
                String msg = "Saving status information to '" + statePath + "' failed";
                logger.fatal(msg, e);
                throw new Error(msg, e);
            } finally {
                if (writer != null) {
                    try {
                        writer.close();
                    } catch (IOException e) {
                    }

                }
            }
        }

        public void recoverState() {
            String statePath = getIdMapper().getTransactionBaseDir() + "/" + getContextFileName();
            File file = new File(statePath);
            BufferedReader reader = null;
            try {
                InputStream is = new FileInputStream(file);

                reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                setStatus(Status.valueOf(reader.readLine()));
                setTimeout(Long.parseLong(reader.readLine()));
                setStartTime(Long.parseLong(reader.readLine()));
            } catch (FileNotFoundException e) {
                String msg = "Recovering status information from '" + statePath + "' failed! Could not find file";
                logger.fatal(msg, e);
                throw new Error(msg, e);
            } catch (IOException e) {
                String msg = "Recovering status information from '" + statePath + "' failed";
                logger.fatal(msg, e);
                throw new Error(msg, e);
            } catch (Throwable t) {
                String msg = "Recovering status information from '" + statePath + "' failed";
                logger.fatal(msg, t);
                throw new Error(msg, t);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                    }

                }
            }
        }

        public String toString() {
            StringBuffer buf = new StringBuffer();
            buf.append(getStatus().name()).append('\n');
            buf.append(Long.toString(getTimeout())).append('\n');
            buf.append(Long.toString(getStartTime())).append('\n');
            return buf.toString();
        }

    }
}
