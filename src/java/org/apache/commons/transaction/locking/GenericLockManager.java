/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//transaction/src/java/org/apache/commons/transaction/locking/GenericLockManager.java,v 1.4 2004/12/16 23:26:31 ozeigermann Exp $
 * $Revision: 1.4 $
 * $Date: 2004/12/16 23:26:31 $
 *
 * ====================================================================
 *
 * Copyright 1999-2004 The Apache Software Foundation 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.commons.transaction.locking;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.transaction.util.LoggerFacade;

/**
 * Manager for {@link GenericLock}s on resources.   
 * 
 * @version $Revision: 1.4 $
 */
public class GenericLockManager implements LockManager {

    public static final long DEFAULT_TIMEOUT = 30000;
    
    /** Maps lock to ownerIds waiting for it. */
    protected Map waitsForLock = Collections.synchronizedMap(new HashMap());

    /** Maps onwerId to locks it (partially) owns. */
    protected Map globalOwners = Collections.synchronizedMap(new HashMap());

    /** Maps resourceId to lock. */
    protected Map globalLocks = new HashMap();
    
    protected int maxLockLevel = -1;
    protected LoggerFacade logger;
    protected long globalTimeoutMSecs;
    
    /**
     * Creates a new generic lock manager.
     * 
     * @param maxLockLevel
     *            highest allowed lock level as described in {@link GenericLock}'s class intro
     * @param logger
     *            generic logger used for all kind of debug logging
     * @param timeoutMSecs
     *            specifies the maximum time to wait for a lock in milliseconds
     * @throws IllegalArgumentException
     *             if maxLockLevel is less than 1
     */
    public GenericLockManager(int maxLockLevel, LoggerFacade logger, long timeoutMSecs)
            throws IllegalArgumentException {
        if (maxLockLevel < 1)
            throw new IllegalArgumentException("The maximum lock level must be at least 1 ("
                    + maxLockLevel + " was specified)");
        this.maxLockLevel = maxLockLevel;
        this.logger = logger.createLogger("Locking");
        this.globalTimeoutMSecs = timeoutMSecs;
    }

    public GenericLockManager(int maxLockLevel, LoggerFacade logger)
            throws IllegalArgumentException {
        this(maxLockLevel, logger, DEFAULT_TIMEOUT);
    }

    /**
     * @see LockManager#tryLock(Object, Object, int, boolean)
     */
    public boolean tryLock(Object ownerId, Object resourceId, int targetLockLevel, boolean reentrant) {
        GenericLock lock = (GenericLock) atomicGetOrCreateLock(resourceId);
        boolean acquired = lock.tryLock(ownerId, targetLockLevel,
                reentrant ? GenericLock.COMPATIBILITY_REENTRANT : GenericLock.COMPATIBILITY_NONE);
        if (acquired) {
            addOwner(ownerId, lock);
        }
        return acquired;
    }

    /**
     * @see LockManager#lock(Object, Object, int, boolean)
     */
    public void lock(Object ownerId, Object resourceId, int targetLockLevel, boolean reentrant)
            throws LockException {
        lock(ownerId, resourceId, targetLockLevel, reentrant, globalTimeoutMSecs);
    }

    /**
     * @see LockManager#lock(Object, Object, int, boolean, long)
     */
    public void lock(Object ownerId, Object resourceId, int targetLockLevel, boolean reentrant,
            long timeoutMSecs) throws LockException {

        GenericLock lock = (GenericLock) atomicGetOrCreateLock(resourceId);

        // we need to be careful that we the detected deadlock status is still valid when actually
        // applying for the lock
        // we have to take care that 
        // (a) no one else acquires the lock after we have done deadlock checking as this would
        //    invalidate our checking result
        // (b) other threads that might concurrently apply for locks we are holding need to know
        //    we are applying for this special lock before we check for deadlocks ourselves; this
        //    is important as the other thread might be the one to discover the deadlock
        
        // (b) register us as a waiter before actually trying, so other threads take us into account
        addWaiter(lock, ownerId);

        try {
            boolean acquired;
            // (a) while we are checking if we can have this lock, no one else must apply for it
            // and possibly change the data
            synchronized (lock) {
                
                // TODO: detection is rather expensive, would be an idea to wait for a 
                // short time (<5 seconds) to see if we get the lock, after that we can still check
                // for the deadlock and if not try with the remaining timeout time
                boolean deadlock = wouldDeadlock(ownerId, lock, targetLockLevel,
                        reentrant ? GenericLock.COMPATIBILITY_REENTRANT : GenericLock.COMPATIBILITY_NONE);
                if (deadlock) {
                    throw new LockException("Lock would cause deadlock",
                            LockException.CODE_DEADLOCK_VICTIM, resourceId);
                }
        
                acquired = lock
                        .acquire(ownerId, targetLockLevel, true, reentrant, timeoutMSecs);
            }
            if (!acquired) {
                throw new LockException("Lock wait timed out", LockException.CODE_TIMED_OUT,
                        resourceId);
            } else {
                addOwner(ownerId, lock);
            }
        } catch (InterruptedException e) {
            throw new LockException("Interrupted", LockException.CODE_INTERRUPTED, resourceId);
        } finally {
            removeWaiter(lock, ownerId);
        }
    }

    /**
     * @see LockManager#getLevel(Object, Object)
     */
    public int getLevel(Object ownerId, Object resourceId) {
        GenericLock lock = (GenericLock) getLock(resourceId);
        if (lock != null) {
            return lock.getLockLevel(ownerId);
        } else {
            return 0;
        }
    }

    /**
     * @see LockManager#release(Object, Object)
     */
    public void release(Object ownerId, Object resourceId) {
        GenericLock lock = (GenericLock) atomicGetOrCreateLock(resourceId);
        lock.release(ownerId);
        removeOwner(ownerId, lock);
    }

    /**
     * @see LockManager#releaseAll(Object)
     */
    public void releaseAll(Object ownerId) {
        Set locks = (Set) globalOwners.get(ownerId);
        if (locks != null) {
            for (Iterator it = locks.iterator(); it.hasNext();) {
                GenericLock lock = (GenericLock) it.next();
                lock.release(ownerId);
                it.remove();
            }
        }
    }

    /**
     * @see LockManager#getAll(Object)
     */
    public Set getAll(Object ownerId) {
        Set locks = (Set) globalOwners.get(ownerId);
        if (locks == null) {
            locks = new HashSet();
            globalOwners.put(ownerId, locks);
        }
        return locks;
    }

    protected void addOwner(Object ownerId, GenericLock lock) {
        Set locks = (Set) globalOwners.get(ownerId);
        if (locks == null) {
            locks = new HashSet();
            globalOwners.put(ownerId, locks);
        }
        locks.add(lock);
    }

    protected void removeOwner(Object ownerId, GenericLock lock) {
        Set locks = (Set) globalOwners.get(ownerId);
        if (locks != null) {
            locks.remove(lock);
        }
    }

    protected void addWaiter(GenericLock lock, Object ownerId) {
        Set waiters = (Set) waitsForLock.get(lock);
        if (waiters == null) {
            waiters = new HashSet();
            waitsForLock.put(lock, waiters);
        }
        waiters.add(ownerId);
    }

    protected void removeWaiter(GenericLock lock, Object ownerId) {
        Set waiters = (Set) waitsForLock.get(lock);
        if (waiters != null) {
            waiters.remove(ownerId);
        }
    }

    protected boolean wouldDeadlock(Object ownerId, GenericLock lock, int targetLockLevel,
            int compatibility) {
        // let's see if any of the conflicting owners waits for us, if so we
        // have a deadlock

        Set conflicts = lock.getConflictingOwners(ownerId, targetLockLevel, compatibility);
        if (conflicts != null) {
            // these are our locks
            Set locks = (Set) globalOwners.get(ownerId);
            if (locks != null) {
                for (Iterator i = locks.iterator(); i.hasNext();) {
                    GenericLock mylock = (GenericLock) i.next();
                    // these are the ones waiting for one of our locks
                    Set waiters = (Set) waitsForLock.get(mylock);
                    if (waiters != null) {
                        for (Iterator j = waiters.iterator(); j.hasNext();) {
                            Object waitingOwnerId = j.next();
                            // if someone waiting for one of our locks would make us wait
                            // this is a deadlock
                            if (conflicts.contains(waitingOwnerId))
                                return true;

                        }
                    }
                }
            }
        }
        return false;
    }

    
    public MultiLevelLock getLock(Object resourceId) {
        synchronized (globalLocks) {
            return (MultiLevelLock) globalLocks.get(resourceId);
        }
    }

    public MultiLevelLock atomicGetOrCreateLock(Object resourceId) {
        synchronized (globalLocks) {
            MultiLevelLock lock = getLock(resourceId);
            if (lock == null) {
                lock = createLock(resourceId);
            }
            return lock;
        }
    }

    public void removeLock(MultiLevelLock lock) {
        synchronized (globalLocks) {
            globalLocks.remove(lock);
        }
    }
    
    /**
     * Gets all locks as orignials, <em>no copies</em>.
     * 
     * @return collection holding all locks.
     */
    public Collection getLocks() {
        synchronized (globalLocks) {
           return globalLocks.values();
        }
    }

    protected GenericLock createLock(Object resourceId) {
        synchronized (globalLocks) {
            GenericLock lock = new GenericLock(resourceId, maxLockLevel, logger);
            globalLocks.put(resourceId, lock);
            return lock;
        }
    }
}
