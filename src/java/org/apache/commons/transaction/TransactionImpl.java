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
package org.apache.commons.transaction;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.transaction.locking.LockManager;

/**
 * A managed transaction meant as interface to the user. Meant to operate on
 * more than one resource manager. This is a light weight replacement for a
 * complex 2PC xa transaction.
 * 
 * @author olli
 * 
 * 
 */
public class TransactionImpl implements Transaction {

    protected LockManager lm;

    protected List<ManageableResourceManager> rms;

    public TransactionImpl(LockManager lm) {
        this.lm = lm;
        this.rms = new LinkedList<ManageableResourceManager>();
    }

    public void commit() throws TransactionException {
        lm.endWork();
        if (isRollbackOnly()) {
            throw new TransactionException(TransactionException.Code.ROLLBACK_ONLY);
        }
        if (!prepare()) {
            throw new TransactionException(TransactionException.Code.PREPARE_FAILED);
        }

        for (ManageableResourceManager manager : rms) {
            if (!manager.isReadOnlyTransaction()) {
                try {
                    if (!manager.commitTransaction()) {
                        throw new TransactionException(TransactionException.Code.COMMIT_FAILED);
                    }
                } catch (Exception e) {
                    throw new TransactionException(e, TransactionException.Code.COMMIT_FAILED);
                } catch (Error e) {
                    // XXX is this really a good idea?
                    rollback();
                    throw e;
                }
            }
        }
    }

    public void enlistResourceManager(ManageableResourceManager resourceManager) {
        // if the manager might fail upon commit, tried it as early as possible
        if (resourceManager.commitCanFail()) {
            rms.add(0, resourceManager);
        } else {
            rms.add(resourceManager);
        }
    }

    public boolean isRollbackOnly() {
        for (ManageableResourceManager manager : rms) {
            if (manager.isTransactionMarkedForRollback())
                return true;
        }
        return false;
    }

    public void rollback() {
        lm.endWork();
        for (ManageableResourceManager manager : rms) {
            if (!manager.isReadOnlyTransaction()) {
                manager.rollbackTransaction();
            }
        }
    }

    public void start(long timeout, TimeUnit unit) {
        for (ManageableResourceManager manager : rms) {
            manager.joinTransaction(lm);
        }
        lm.startWork(timeout, unit);
    }

    protected boolean prepare() {
        for (ManageableResourceManager manager : rms) {
            if (!manager.prepareTransaction())
                return false;
        }
        return true;
    }
}
