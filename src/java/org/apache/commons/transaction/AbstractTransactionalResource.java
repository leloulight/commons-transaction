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

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.transaction.locking.LockPolicy;

/**
 * Not thread-safe. FIXME: Should it be?
 * 
 * @author olli
 *
 * @param <T>
 */
public abstract class AbstractTransactionalResource<T extends TxContext> implements TransactionalResource {
    protected ThreadLocal<T> activeTx = new ThreadLocal<T>();

    protected Set<T> activeTransactions = new HashSet<T>();

    protected Set<T> suspendedTransactions = new HashSet<T>();

    protected abstract T createContext();

    @Override
    public boolean isReadOnly() {
        TxContext txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }

        return txContext.isReadOnly();
    }

    @Override
    public boolean isTransactionMarkedForRollback() {
        TxContext txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }

        return (txContext.getStatus() == Status.MARKED_ROLLBACK);
    }

    @Override
    public void markTransactionForRollback() {
        TxContext txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }

        txContext.setStatus(Status.MARKED_ROLLBACK);
    }

    /**
     * Suspends the transaction associated to the current thread. I.e. the
     * associated between the current thread and the transaction is deleted.
     * This is useful when you want to continue the transaction in another
     * thread later. Call {@link #resumeTransaction(TxContext)} - possibly in
     * another thread than the current - to resume work on the transaction. <br>
     * <br>
     * <em>Caution:</em> When calling this method the returned identifier for
     * the transaction is the only remaining reference to the transaction, so be
     * sure to remember it or the transaction will be eventually deleted (and
     * thereby rolled back) as garbage.
     * 
     * @return an identifier for the suspended transaction, will be needed to
     *         later resume the transaction by
     *         {@link #resumeTransaction(TxContext)}
     * 
     * @see #resumeTransaction(TxContext)
     */
    public TxContext suspendTransaction() {
        T txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }

        suspendedTransactions.add(txContext);
        setActiveTx(null);
        return txContext;
    }

    /**
     * Resumes a transaction in the current thread that has previously been
     * suspened by {@link #suspendTransaction()}.
     * 
     * @param suspendedTx
     *            the identifier for the transaction to be resumed, delivered by
     *            {@link #suspendTransaction()}
     * 
     * @see #suspendTransaction()
     */
    public void resumeTransaction(T suspendedTx) {
        T txContext = getActiveTx();

        if (txContext != null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " already associated with a transaction!");
        }

        if (suspendedTx == null) {
            throw new IllegalStateException("No transaction to resume!");
        }

        if (!suspendedTransactions.contains(suspendedTx)) {
            throw new IllegalStateException("Transaction to resume needs to be suspended!");
        }

        suspendedTransactions.remove(txContext);
        setActiveTx(suspendedTx);
    }

    @Override
    public Status getTransactionState() {
        TxContext txContext = getActiveTx();

        if (txContext == null) {
            return Status.NO_TRANSACTION;
        }
        return txContext.getStatus();
    }

    @Override
    public void startTransaction() {
        if (getActiveTx() != null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " already associated with a transaction!");
        }
        T txContent = createContext();
        setActiveTx(txContent);
        activeTransactions.add(txContent);

    }

    @Override
    public void rollbackTransaction() {
        TxContext txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }

        txContext.dispose();
        setActiveTx(null);
        activeTransactions.remove(txContext);
    }

    @Override
    public void commitTransaction() {
        TxContext txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }

        if (txContext.getStatus() == Status.MARKED_ROLLBACK) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " is marked for rollback!");
        }

        txContext.commit();
        txContext.dispose();
        setActiveTx(null);
    }

    @Override
    public boolean prepareTransaction() {
        TxContext txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }
        return txContext.prepare();
    }

    @Override
    public void setTransactionTimeout(long mSecs) {
        TxContext txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }
        
        txContext.setTimeout(mSecs);
    }

    protected T getActiveTx() {
        return activeTx.get();
    }

    protected void setActiveTx(T txContext) {
        activeTx.set(txContext);
    }

}
