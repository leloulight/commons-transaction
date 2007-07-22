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

import java.util.concurrent.TimeUnit;

import org.apache.commons.transaction.locking.LockException;
import org.apache.commons.transaction.locking.LockManager;

/**
 * Not thread-safe. FIXME: Should it be?
 * 
 * @author olli
 * 
 * @param <T>
 */
public abstract class AbstractTransactionalResourceManager<T extends AbstractTransactionalResourceManager.AbstractTxContext>
        implements ManageableResourceManager {
    protected ThreadLocal<T> activeTx = new ThreadLocal<T>();

    private LockManager<Object, String> lm;

    private String name;

    protected abstract T createContext();

    public AbstractTransactionalResourceManager() {
    }

    public AbstractTransactionalResourceManager(String name) {
        this.name = name;
    }

    // can be used to share a lock manager with other transactinal resource
    // managers
    public AbstractTransactionalResourceManager(String name, LockManager<Object, String> lm) {
        this.name = name;
        this.lm = lm;
    }

    @Override
    public boolean isTransactionMarkedForRollback() {
        T txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }

        return (txContext.isMarkedForRollback());
    }

    @Override
    public void startTransaction(long timeout, TimeUnit unit) {
        if (getActiveTx() != null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " already associated with a transaction!");
        }
        T txContext = createContext();
        txContext.start(timeout, unit);
        setActiveTx(txContext);

    }

    @Override
    public void rollbackTransaction() {
        T txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }

        txContext.dispose();
        setActiveTx(null);
    }

    @Override
    public boolean commitTransaction() {
        T txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }

        if (txContext.isMarkedForRollback()) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " is marked for rollback!");
        }

        txContext.commit();
        txContext.dispose();
        setActiveTx(null);
        return true;
    }

    protected T getActiveTx() {
        return activeTx.get();
    }

    protected void setActiveTx(T txContext) {
        activeTx.set(txContext);
    }

    public boolean isReadOnlyTransaction() {
        T txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }

        return (txContext.isReadOnly());
    }

    public abstract class AbstractTxContext {
        private boolean readOnly = true;

        private boolean markedForRollback = false;

        private LockManager<Object, String> lm;
        
        public AbstractTxContext() {
        }
        
        public LockManager<Object, String> getLm() {
            if (this.lm != null) return this.lm;
            else return AbstractTransactionalResourceManager.this.lm;
        }


        public void join(LockManager lm) {
            this.lm = lm;
        }

        public void start(long timeout, TimeUnit unit) {
            getLm().startWork(timeout, unit);
        }

        public void dispose() {
            getLm().endWork();
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        public void setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
        }

        public void readLock(Object id) throws LockException {
            getLm().lock(getName(), id, false);
        }

        public void writeLock(Object id) throws LockException {
            getLm().lock(getName(), id, true);
        }

        public boolean isMarkedForRollback() {
            return markedForRollback;
        }

        public void markForRollback() {
            markedForRollback = true;
        }

        public void commit() {

        }
    }

    public LockManager<Object, String> getLm() {
        return lm;
    }

    public void setLm(LockManager<Object, String> lm) {
        this.lm = lm;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public abstract boolean commitCanFail();

    @Override
    public void joinTransaction(LockManager lm) {
        if (getActiveTx() != null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " already associated with a transaction!");
        }
        T txContext = createContext();
        txContext.join(lm);
        setActiveTx(txContext);

    }

    public void setRollbackOnly() {
        T txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }
        txContext.markForRollback();

    }


}
