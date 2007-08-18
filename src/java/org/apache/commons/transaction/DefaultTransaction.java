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

import org.apache.commons.transaction.locking.RWLockManager;
import org.apache.commons.transaction.locking.LockManager;

/**
 * Default implementation for the {@link Transaction} interface. Needs a common
 * lock manager shared by all resource managers to make detection of distributed
 * deadlocks possible.
 * 
 * <p>
 * Sample usage:
 * 
 * <pre><tt>
 * LockManager&lt;Object, Object&gt; lm = new RWLockManager&lt;Object, Object&gt;();
 * Transaction t = new DefaultTransaction(lm);
 * TxMap&lt;String, Object&gt; txMap1 = new PessimisticTxMap&lt;String, Object&gt;(&quot;TxMap1&quot;);
 * t.enlistResourceManager(txMap1);
 * TxMap&lt;String, Object&gt; txMap2 = new PessimisticTxMap&lt;String, Object&gt;(&quot;TxMap2&quot;);
 * t.enlistResourceManager(txMap2);
 * 
 * try {
 *     t.start(60, TimeUnit.SECONDS);
 *     txMap1.put(&quot;Olli&quot;, &quot;Huhu&quot;);
 *     txMap2.put(&quot;Olli&quot;, &quot;Haha&quot;);
 *     t.commit();
 * } catch (Throwable throwable) {
 *     t.rollback();
 * }
 * </tt></pre>
 * 
 * <p>
 * This implementation is <em>thread-safe</em>.
 */
public class DefaultTransaction implements Transaction {

    protected LockManager<Object, Object> lm;

    protected boolean started = false;

    protected List<ManageableResourceManager> rms;

    /**
     * Creates a new transaction implementation.
     * 
     * @param lm
     *            the lock manager shared by all resource managers
     */
    public DefaultTransaction(LockManager<Object, Object> lm) {
        this.lm = lm;
        this.rms = new LinkedList<ManageableResourceManager>();
    }

    /**
     * Creates a new transaction implementation using the default lock manager.
     * 
     */
    public DefaultTransaction() {
        this(new RWLockManager<Object, Object>());
    }

    public synchronized void commit() throws TransactionException {
        if (isRollbackOnly()) {
            throw new TransactionException(TransactionException.Code.ROLLBACK_ONLY);
        }
        if (!prepare()) {
            throw new TransactionException(TransactionException.Code.PREPARE_FAILED);
        }

        for (ManageableResourceManager manager : rms) {
            if (!manager.isReadOnly()) {
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
        lm.endWork();
        started = false;
    }

    public synchronized void enlistResourceManager(ManageableResourceManager resourceManager) {
        // if the manager might fail upon commit, tried it as early as possible
        if (resourceManager.commitCanFail()) {
            rms.add(0, resourceManager);
        } else {
            rms.add(resourceManager);
        }
        if (started) {
            resourceManager.joinTransaction(lm);
        }
    }

    public synchronized boolean isRollbackOnly() {
        for (ManageableResourceManager manager : rms) {
            if (manager.isRollbackOnly())
                return true;
        }
        return false;
    }

    public synchronized void rollback() {
        for (ManageableResourceManager manager : rms) {
            if (!manager.isReadOnly()) {
                manager.rollbackTransaction();
            }
        }
        lm.endWork();
        started = false;
    }

    public synchronized void start(long timeout, TimeUnit unit) {
        started = true;
        for (ManageableResourceManager manager : rms) {
            manager.joinTransaction(lm);
        }
        lm.startWork(timeout, unit);
    }

    protected synchronized boolean prepare() {
        for (ManageableResourceManager manager : rms) {
            if (!manager.prepareTransaction())
                return false;
        }
        return true;
    }
}
