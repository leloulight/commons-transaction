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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.transaction.locking.LockException;
import org.apache.commons.transaction.locking.LockManager;
import org.apache.commons.transaction.locking.RWLockManager;

/**
 * Map featuring transactional control.
 * 
 * <p>By using a naive optimistic transaction control
 * this implementation has better isolation than {@link BasicTxMap}, but may also fail
 * to commit.
 * 
 * <p><em>Caution:</em> This implementation might be slow when large amounts of
 * data is changed in a transaction as much references will need to be copied
 * around.
 * 
 * <p>This implementation wraps a map of type {@link ConcurrentHashMap}. 
 * 
 * <p>
 * This implementation is <em>thread-safe</em>.
 * 
 * @see BasicTxMap
 * @see PessimisticTxMap
 * @see ConcurrentHashMap
 */
public class OptimisticTxMap<K, V> extends BasicTxMap<K, V> implements TxMap<K, V> {

    private Set<CopyingTxContext> activeTransactions = Collections
            .synchronizedSet(new HashSet<CopyingTxContext>());

    private ReadWriteLock commitLock = new ReentrantReadWriteLock();

    private long commitTimeout = 1000 * 60; // 1 minute

    private long accessTimeout = 1000 * 30; // 30 seconds

    public OptimisticTxMap(String name) {
        this(name, new RWLockManager<Object, Object>());
    }

    public OptimisticTxMap(String name, LockManager<Object, Object> lm) {
        super(name, lm);
    }
    
    @Override
    public void startTransaction(long timeout, TimeUnit unit) {
        super.startTransaction(timeout, unit);
        MapTxContext txContext = getActiveTx();
        activeTransactions.add((CopyingTxContext)txContext);
    }
    
    @Override
    public void rollbackTransaction() {
        MapTxContext txContext = getActiveTx();
        super.rollbackTransaction();
        activeTransactions.remove(txContext);
    }

    @Override
    public boolean commitTransaction() throws LockException {
        return commitTransaction(false);
    }

    public boolean commitTransaction(boolean force) throws LockException {
        MapTxContext txContext = getActiveTx();

        if (txContext == null) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " not associated with a transaction!");
        }

        if (txContext.isMarkedForRollback()) {
            throw new IllegalStateException("Active thread " + Thread.currentThread()
                    + " is marked for rollback!");
        }

        try {
            // in this final commit phase we need to be the only one access the
            // map
            // to make sure no one adds an entry after we checked for conflicts
            commitLock.writeLock().tryLock(getCommitTimeout(), TimeUnit.MILLISECONDS);

            if (!force) {
                Object conflictKey = checkForConflicts();
                if (conflictKey != null) {
                    throw new LockException(LockException.Code.CONFLICT, conflictKey);
                }
            }

            activeTransactions.remove(txContext);
            copyChangesToConcurrentTransactions();
            return super.commitTransaction();
        } catch (InterruptedException e) {
            throw new LockException(e);
        } finally {
            commitLock.writeLock().unlock();
        }
    }

    // TODO: Shouldn't we return a collection rather than a single key here?
    public Object checkForConflicts() {
        CopyingTxContext txContext = (CopyingTxContext) getActiveTx();

        Set keys = txContext.changedKeys();
        Set externalKeys = txContext.externalChangedKeys();

        for (Iterator it2 = keys.iterator(); it2.hasNext();) {
            Object key = it2.next();
            if (externalKeys.contains(key)) {
                return key;
            }
        }
        return null;
    }

    protected void copyChangesToConcurrentTransactions() {
        CopyingTxContext thisTxContext = (CopyingTxContext) getActiveTx();

        synchronized (activeTransactions) {
            for (Iterator it = activeTransactions.iterator(); it.hasNext();) {
                CopyingTxContext otherTxContext = (CopyingTxContext) it.next();

                // no need to copy data if the other transaction does not access
                // global map anyway
                if (otherTxContext.cleared)
                    continue;

                if (thisTxContext.cleared) {
                    // we will clear everything, so we have to copy everything
                    // before
                    otherTxContext.externalChanges.putAll(wrapped);
                } else // no need to check if we have already copied everthing
                {
                    for (Iterator it2 = thisTxContext.changes.entrySet().iterator(); it2.hasNext();) {
                        Map.Entry<K, V> entry = (Map.Entry) it2.next();
                        V value = wrapped.get(entry.getKey());
                        if (value != null) {
                            // undo change
                            otherTxContext.externalChanges.put(entry.getKey(), value);
                        } else {
                            // undo add
                            otherTxContext.externalDeletes.add(entry.getKey());
                        }
                    }

                    for (Iterator it2 = thisTxContext.deletes.iterator(); it2.hasNext();) {
                        // undo delete
                        K key = (K) it2.next(); /* FIXME: This could crash */
                        V value = wrapped.get(key);
                        otherTxContext.externalChanges.put(key, value);
                    }
                }
            }
        }
    }

    @Override
    protected CopyingTxContext createContext() {
        return new CopyingTxContext();
    }

    public class CopyingTxContext extends MapTxContext {
        protected Map<K, V> externalChanges;

        protected Map<K, V> externalAdds;

        protected Set externalDeletes;

        protected CopyingTxContext() {
            super();
            externalChanges = new HashMap();
            externalDeletes = new HashSet();
            externalAdds = new HashMap();
        }

        protected Set externalChangedKeys() {
            Set keySet = new HashSet();
            keySet.addAll(externalDeletes);
            keySet.addAll(externalChanges.keySet());
            keySet.addAll(externalAdds.keySet());
            return keySet;
        }

        protected Set changedKeys() {
            Set keySet = new HashSet();
            keySet.addAll(deletes);
            keySet.addAll(changes.keySet());
            keySet.addAll(adds.keySet());
            return keySet;
        }

        protected Set keys() {
            try {
                commitLock.readLock().tryLock(getAccessTimeout(), TimeUnit.MILLISECONDS);
                Set keySet = super.keys();
                keySet.removeAll(externalDeletes);
                keySet.addAll(externalAdds.keySet());
                return keySet;
            } catch (InterruptedException e) {
                return null;
            } finally {
                commitLock.readLock().unlock();
            }
        }

        protected V get(Object key) {
            try {
                commitLock.readLock().tryLock(getAccessTimeout(), TimeUnit.MILLISECONDS);

                if (deletes.contains(key)) {
                    // reflects that entry has been deleted in this tx
                    return null;
                }

                V changed = changes.get(key);
                if (changed != null) {
                    return changed;
                }

                V added = adds.get(key);
                if (added != null) {
                    return added;
                }

                if (cleared) {
                    return null;
                } else {
                    if (externalDeletes.contains(key)) {
                        // reflects that entry has been deleted in this tx
                        return null;
                    }

                    changed = externalChanges.get(key);
                    if (changed != null) {
                        return changed;
                    }

                    added = externalAdds.get(key);
                    if (added != null) {
                        return added;
                    }

                    // not modified in this tx
                    return wrapped.get(key);
                }
            } catch (InterruptedException e) {
                return null;
            } finally {
                commitLock.readLock().unlock();
            }
        }

        protected void put(K key, V value) {
            try {
                commitLock.readLock().tryLock(getAccessTimeout(), TimeUnit.MILLISECONDS);
                super.put(key, value);
            } catch (InterruptedException e) {
            } finally {
                commitLock.readLock().unlock();
            }
        }

        protected void remove(Object key) {
            try {
                commitLock.readLock().tryLock(getAccessTimeout(), TimeUnit.MILLISECONDS);
                super.remove(key);
            } catch (InterruptedException e) {
            } finally {
                commitLock.readLock().unlock();
            }
        }

        protected int size() {
            try {
                commitLock.readLock().tryLock(getAccessTimeout(), TimeUnit.MILLISECONDS);
                int size = super.size();

                size -= externalDeletes.size();
                size += externalAdds.size();

                return size;
            } catch (InterruptedException e) {
                return -1;
            } finally {
                commitLock.readLock().unlock();
            }
        }

        protected void clear() {
            try {
                commitLock.readLock().tryLock(getAccessTimeout(), TimeUnit.MILLISECONDS);
                super.clear();
                externalDeletes.clear();
                externalChanges.clear();
                externalAdds.clear();
            } catch (InterruptedException e) {
            } finally {
                commitLock.readLock().unlock();
            }
        }

        public void commit() {
            try {
                commitLock.readLock().tryLock(getAccessTimeout(), TimeUnit.MILLISECONDS);
                super.commit();
            } catch (InterruptedException e) {
            } finally {
                commitLock.readLock().unlock();
            }
        }

    }

    public long getAccessTimeout() {
        return accessTimeout;
    }

    public void setAccessTimeout(long accessTimeout) {
        this.accessTimeout = accessTimeout;
    }

    public long getCommitTimeout() {
        return commitTimeout;
    }

    public void setCommitTimeout(long commitTimeout) {
        this.commitTimeout = commitTimeout;
    }

    @Override
    public boolean commitCanFail() {
        return true;
    }

}
