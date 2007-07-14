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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.transaction.locking.LockException;

/**
 * Wrapper that adds transactional control to all kinds of maps that implement
 * the {@link Map} interface. By using a naive optimistic transaction control
 * this wrapper has better isolation than {@link TransactionalMapWrapper}, but
 * may also fail to commit.
 * 
 * <br>
 * Start a transaction by calling {@link #startTransaction()}. Then perform the
 * normal actions on the map and finally either call
 * {@link #commitTransaction()} to make your changes permanent or
 * {@link #rollbackTransaction()} to undo them. <br>
 * <em>Caution:</em> Do not modify values retrieved by {@link #get(Object)} as
 * this will circumvent the transactional mechanism. Rather clone the value or
 * copy it in a way you see fit and store it back using
 * {@link #put(Object, Object)}. <br>
 * <em>Note:</em> This wrapper guarantees isolation level
 * <code>SERIALIZABLE</code>. <br>
 * <em>Caution:</em> This implementation might be slow when large amounts of
 * data is changed in a transaction as much references will need to be copied
 * around.
 * 
 * @version $Id: OptimisticMapWrapper.java 493628 2007-01-07 01:42:48Z joerg $
 * @see TransactionalMapWrapper
 * @see PessimisticMapWrapper
 */
public class OptimisticMapWrapper extends TransactionalMapWrapper {

    private ReadWriteLock commitLock;

    private long commitTimeout = 1000 * 60; // 1 minute

    private long accessTimeout = 1000 * 30; // 30 seconds

    /**
     * Creates a new optimistic transactional map wrapper. Temporary maps and
     * sets to store transactional data will be instances of
     * {@link java.util.HashMap} and {@link java.util.HashSet}.
     * 
     * @param wrapped
     *            map to be wrapped
     */
    public OptimisticMapWrapper(Map wrapped) {
        super(wrapped);
        commitLock = new ReentrantReadWriteLock();
    }

    public void rollbackTransaction() {
        MapTxContext txContext = getActiveTx();
        super.rollbackTransaction();
        activeTransactions.remove(txContext);
    }

    public void commitTransaction() throws LockException {
        commitTransaction(false);
    }

    public void commitTransaction(boolean force) throws LockException {
        MapTxContext txContext = getActiveTx();
        
        if (txContext == null) {
            throw new IllegalStateException(
                "Active thread " + Thread.currentThread() + " not associated with a transaction!");
        }

        if (txContext.isMarkedForRollback()) {
            throw new IllegalStateException("Active thread " + Thread.currentThread() + " is marked for rollback!");
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
            super.commitTransaction();
            
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
                        Map.Entry entry = (Map.Entry) it2.next();
                        Object value = wrapped.get(entry.getKey());
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
                        Object key = it2.next();
                        Object value = wrapped.get(key);
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
        protected Map externalChanges;

        protected Map externalAdds;

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

        protected Object get(Object key) {
            try {
                commitLock.readLock().tryLock(getAccessTimeout(), TimeUnit.MILLISECONDS);

                if (deletes.contains(key)) {
                    // reflects that entry has been deleted in this tx
                    return null;
                }

                Object changed = changes.get(key);
                if (changed != null) {
                    return changed;
                }

                Object added = adds.get(key);
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

        protected void put(Object key, Object value) {
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
}
