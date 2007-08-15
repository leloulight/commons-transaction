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
 * Abstract base class for transactional resource managers.
 * <p>
 * This implementation takes care of most administrative tasks a transactional
 * resource manager has to perform. Sublcass
 * {@link AbstractTransactionalResourceManager.AbstractTxContext} to hold all
 * information necessary for each transaction. Additionally, you have to
 * implement {@link #createContext()} to create an object of that type, and
 * {@link #commitCanFail()}.
 * 
 * @param <T>
 */
public abstract class AbstractTransactionalResourceManager<T extends AbstractTransactionalResourceManager.AbstractTxContext>
        implements ManageableResourceManager {
    protected ThreadLocal<T> activeTx = new ThreadLocal<T>();

    private LockManager<Object, Object> lm;

    private String name;

    protected abstract T createContext();

    public AbstractTransactionalResourceManager() {
    }

    public AbstractTransactionalResourceManager(String name) {
        this.name = name;
    }

    // can be used to share a lock manager with other transactional resource
    // managers
    public AbstractTransactionalResourceManager(String name, LockManager<Object, Object> lm) {
        this.name = name;
        this.lm = lm;
    }

    @Override
    public boolean isRollbackOnly() {
        T txContext = getCheckedActiveTx();

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
        T txContext = getCheckedActiveTx();

        txContext.rollback();
        forgetTransaction();
    }

    @Override
    public void forgetTransaction() {
        T txContext = getCheckedActiveTx();

        txContext.dispose();
        setActiveTx(null);
    }

    @Override
    public boolean commitTransaction() {
        T txContext = getCheckedActiveTx();

        if (txContext.isMarkedForRollback()) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " is marked for rollback!");
        }

        txContext.commit();
        forgetTransaction();
        return true;
    }

    protected T getActiveTx() {
        return activeTx.get();
    }

    protected T getCheckedActiveTx() {
        T txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }
        return txContext;
    }

    protected void setActiveTx(T txContext) {
        activeTx.set(txContext);
    }

    public boolean isReadOnly() {
        T txContext = getCheckedActiveTx();
        return (txContext.isReadOnly());
    }

    public abstract class AbstractTxContext {
        private boolean readOnly = true;

        private boolean markedForRollback = false;

        public void join() {
        }

        public void start(long timeout, TimeUnit unit) {
            getLm().startWork(timeout, unit);
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

        public void dispose() {
            getLm().endWork();
        }

        public void commit() {

        }

        public void rollback() {

        }

        public boolean prepare() {
            return isMarkedForRollback();
        }

    }

    protected LockManager<Object, Object> getLm() {
        return lm;
    }

    public void setLm(LockManager<Object, Object> lm) {
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
    public void joinTransaction(LockManager<Object, Object> lm) {
        if (getActiveTx() != null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " already associated with a transaction!");
        }
        setLm(lm);
        T txContext = createContext();
        txContext.join();
        setActiveTx(txContext);

    }

    @Override
    public boolean prepareTransaction() {
        T txContext = getCheckedActiveTx();
        return txContext.prepare();

    }

}