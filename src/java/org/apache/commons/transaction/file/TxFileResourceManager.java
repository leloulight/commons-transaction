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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.Status;
import org.apache.commons.transaction.TransactionManager;

public class TxFileResourceManager implements FileResourceManager, TransactionManager {

    private Log logger = LogFactory.getLog(getClass());

    protected String contextFile = "transaction.log";

    protected ResourceIdToPathMapper idMapper;

    protected LockPolicy lockPolicy;

    public String getContextFile() {
        return contextFile;
    }

    public void setContextFile(String contextFile) {
        this.contextFile = contextFile;
    }

    public InputStream read(String id) throws IOException {
        try {
            getLockPolicy().readLock(id);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String path = getIdMapper().getPathForRead(id);
        return new FileInputStream(new File(path));
    }

    public OutputStream write(String id) throws IOException {
        try {
            getLockPolicy().writeLock(id);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String path = getIdMapper().getPathForRead(id);
        return new FileOutputStream(new File(path));
    }

    public boolean remove(String id) throws IOException {
        try {
            getLockPolicy().writeLock(id);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String path = getIdMapper().getDeletePath(id);
        return new File(path).delete();
    }

    public boolean create(String id) throws IOException {
        try {
            getLockPolicy().writeLock(id);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        String path = getIdMapper().getDeletePath(id);
        return new File(path).createNewFile();
    }

    public ResourceIdToPathMapper getIdMapper() {
        return idMapper;
    }

    public void setIdMapper(ResourceIdToPathMapper idMapper) {
        this.idMapper = idMapper;
    }

    public LockPolicy getLockPolicy() {
        return lockPolicy;
    }

    public void setLockPolicy(LockPolicy lockPolicy) {
        this.lockPolicy = lockPolicy;
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

    public void commitTransaction() {
        // TODO Auto-generated method stub
        
    }

    public Status getTransactionState() {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean isReadOnly() {
        // TODO Auto-generated method stub
        return false;
    }

    public boolean isTransactionMarkedForRollback() {
        // TODO Auto-generated method stub
        return false;
    }

    public void markTransactionForRollback() {
        // TODO Auto-generated method stub
        
    }

    public boolean prepareTransaction() {
        // TODO Auto-generated method stub
        return false;
    }

    public void rollbackTransaction() {
        // TODO Auto-generated method stub
        
    }

    public void setTransactionTimeout(long mSecs) {
        // TODO Auto-generated method stub
        
    }

    public void startTransaction() {
        // TODO Auto-generated method stub
        
    }
}
