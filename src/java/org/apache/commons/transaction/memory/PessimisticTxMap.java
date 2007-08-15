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

package org.apache.commons.transaction.memory;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.transaction.locking.DefaultLockManager;
import org.apache.commons.transaction.locking.LockManager;

/**
 * Map featuring transactional control.
 * 
 * <p>
 * By using pessimistic transaction control (blocking locks) this wrapper has
 * better isolation than {@link BasicTxMap}, but also has less possible
 * concurrency and may even deadlock. A commit, however, will never fail.
 * 
 * <p>
 * <em>Caution:</em>Some operations that would require global locks (e.g.
 * <code>size()</code> or <code>clear()</code> or not properly isolated as
 * this implementation does not support global locks.
 * 
 * <p>
 * This implementation wraps a map of type {@link ConcurrentHashMap}.
 * 
 * @see BasicTxMap
 * @see OptimisticTxMap
 * @see ConcurrentHashMap
 */
public class PessimisticTxMap<K, V> extends BasicTxMap<K, V> implements TxMap<K, V> {

    private ReadWriteLock globalLock = new ReentrantReadWriteLock();

    public PessimisticTxMap(String name) {
        this(name, new DefaultLockManager<Object, Object>());
    }

    public PessimisticTxMap(String name, LockManager<Object, Object> lm) {
        super(name, lm);
    }

    public Collection values() {
        return super.values();
    }

    public Set entrySet() {
        return super.entrySet();
    }

    public Set keySet() {
        return super.keySet();
    }

    public V remove(Object key) {
        // assure we get a write lock before super can get a read lock to avoid
        // lots
        // of deadlocks
        assureWriteLock(key);
        return super.remove(key);
    }

    public V put(K key, V value) {
        // assure we get a write lock before super can get a read lock to avoid
        // lots
        // of deadlocks
        assureWriteLock(key);
        return super.put(key, value);
    }

    protected void assureWriteLock(Object key) {
        LockingTxContext txContext = (LockingTxContext) getActiveTx();
        if (txContext != null) {
            txContext.writeLock(key);
            // XXX fake intention lock (prohibits global WRITE)
        }
    }

    @Override
    protected LockingTxContext createContext() {
        return new LockingTxContext();
    }

    public class LockingTxContext extends MapTxContext {

        protected Set keys() {
            return super.keys();
        }

        protected V get(Object key) {
            readLock(key);
            return super.get(key);
        }

        protected void put(K key, V value) {
            writeLock(key);
            super.put(key, value);
        }

        protected void remove(Object key) {
            writeLock(key);
            super.remove(key);
        }

        protected int size() {
            return super.size();
        }

        protected void clear() {
            super.clear();
        }

    }

    @Override
    public boolean commitCanFail() {
        return false;
    }

}
