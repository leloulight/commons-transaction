/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//transaction/src/java/org/apache/commons/transaction/locking/GenericLockManager.java,v 1.7 2004/12/19 10:10:13 ozeigermann Exp $
 * $Revision: 1.7 $
 * $Date: 2004/12/19 10:10:13 $
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
 * @version $Revision: 1.7 $
 */
public class GenericLockManager implements LockManager {

    public static final long DEFAULT_TIMEOUT = 30000;
    public static final long DEFAULT_CHECK_THRESHHOLD = 500;
    
    /** Maps lock to ownerIds waiting for it. */
    protected Map waitsForLock = Collections.synchronizedMap(new HashMap());

    /** Maps onwerId to locks it (partially) owns. */
    protected Map globalOwners = Collections.synchronizedMap(new HashMap());

    /** Maps resourceId to lock. */
    protected Map globalLocks = new HashMap();
    
    /** Maps onwerId to global time outs. */
    protected Map globalTimeouts = Collections.synchronizedMap(new HashMap());

    protected Set timedOutOwners = Collections.synchronizedSet(new HashSet());
    
    protected int maxLockLevel = -1;
    protected LoggerFacade logger;
    protected long globalTimeoutMSecs;
    protected long checkThreshhold;
    
    /**
     * Creates a new generic lock manager.
     * 
     * @param maxLockLevel
     *            highest allowed lock level as described in {@link GenericLock}
     *            's class intro
     * @param logger
     *            generic logger used for all kind of debug logging
     * @param timeoutMSecs
     *            specifies the maximum time to wait for a lock in milliseconds
     * @param checkThreshholdMSecs
     *            specifies a special wait threshhold before deadlock and
     *            timeout detection come into play or <code>-1</code> switch
     *            it off and check for directly
     * @throws IllegalArgumentException
     *             if maxLockLevel is less than 1
     */
    public GenericLockManager(int maxLockLevel, LoggerFacade logger, long timeoutMSecs,
            long checkThreshholdMSecs) throws IllegalArgumentException {
        if (maxLockLevel < 1)
            throw new IllegalArgumentException("The maximum lock level must be at least 1 ("
                    + maxLockLevel + " was specified)");
        this.maxLockLevel = maxLockLevel;
        this.logger = logger.createLogger("Locking");
        this.globalTimeoutMSecs = timeoutMSecs;
        this.checkThreshhold = checkThreshholdMSecs;
    }

    public GenericLockManager(int maxLockLevel, LoggerFacade logger, long timeoutMSecs)
            throws IllegalArgumentException {
        this(maxLockLevel, logger, timeoutMSecs, DEFAULT_CHECK_THRESHHOLD);
    }

    public GenericLockManager(int maxLockLevel, LoggerFacade logger)
            throws IllegalArgumentException {
        this(maxLockLevel, logger, DEFAULT_TIMEOUT);
    }


    public void setGlobalTimeout(Object ownerId, long timeoutMSecs) {
        long now = System.currentTimeMillis();
        long timeout = now + timeoutMSecs;
        globalTimeouts.put(ownerId, new Long(timeout));
    }
    
    public long getGlobalTimeoutTime(Object ownerId) {
        Long timeout = (Long) globalTimeouts.get(ownerId);
        return timeout.longValue();
    }
    
    /**
     * @see LockManager#tryLock(Object, Object, int, boolean)
     */
    public boolean tryLock(Object ownerId, Object resourceId, int targetLockLevel, boolean reentrant) {
        timeoutCheck(ownerId);

        GenericLock lock = (GenericLock) atomicGetOrCreateLock(resourceId);
        boolean acquired = lock.tryLock(ownerId, targetLockLevel,
                reentrant ? GenericLock.COMPATIBILITY_REENTRANT : GenericLock.COMPATIBILITY_NONE,
                false);
        
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

        timeoutCheck(ownerId);
        
        long now = System.currentTimeMillis();
        long waitEnd = now + timeoutMSecs;

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
            boolean acquired = false;
            
            // detection for deadlocks and time outs is rather expensive, 
            // so we wait for the lock for a  
            // short time (<5 seconds) to see if we get it without checking;
            // if not we still can check what the reason for this is
            if (checkThreshhold != -1 && timeoutMSecs > checkThreshhold) {
                acquired = lock
                        .acquire(ownerId, targetLockLevel, true, reentrant, checkThreshhold);
                if (acquired) {
                    addOwner(ownerId, lock);
                    return;
                } else {
                    timeoutMSecs -= checkThreshhold;
                }
            }
            
            while (!acquired && waitEnd > now) {
            
                // first be sure all locks are stolen from owners that have already timed out
                releaseTimedOutOwners();

                // (a) while we are checking if we can have this lock, no one else must apply for it
                // and possibly change the data
                synchronized (lock) {
                    
                    // let's see if any of the conflicting owners waits for us, if so we
                    // have a deadlock
    
                    Set conflicts = lock.getConflictingOwners(ownerId, targetLockLevel,
                            reentrant ? GenericLock.COMPATIBILITY_REENTRANT
                                    : GenericLock.COMPATIBILITY_NONE);

                    boolean deadlock = wouldDeadlock(ownerId, lock, targetLockLevel,
                            reentrant ? GenericLock.COMPATIBILITY_REENTRANT
                                    : GenericLock.COMPATIBILITY_NONE, conflicts);
                    if (deadlock) {
                        throw new LockException("Lock would cause deadlock",
                                LockException.CODE_DEADLOCK_VICTIM, resourceId);
                    }

                    // if there are owners we conflict with lets see if one of them globally times
                    // out earlier than this lock, if so we will wake up then to check again
                    long nextConflictTimeout = getNextGlobalConflictTimeout(conflicts);
                    if (nextConflictTimeout != -1 && nextConflictTimeout < waitEnd) {
                        timeoutMSecs = nextConflictTimeout - now;
                        // XXX add 10% to ensure the lock really is timed out
                        timeoutMSecs += timeoutMSecs / 10;
                    } else {
                        timeoutMSecs = waitEnd - now;
                    }
            
                    acquired = lock
                            .acquire(ownerId, targetLockLevel, true, reentrant, timeoutMSecs);
                    
                }
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
        timeoutCheck(ownerId);
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
        timeoutCheck(ownerId);
        GenericLock lock = (GenericLock) atomicGetOrCreateLock(resourceId);
        lock.release(ownerId);
        removeOwner(ownerId, lock);
    }

    /**
     * @see LockManager#releaseAll(Object)
     */
    public void releaseAll(Object ownerId) {
        // reset time out status for this owner
        if (timedOutOwners.remove(ownerId)) {
            // short cut if we were timed out there are no more locks
            return;
        }
        Set locks = (Set) globalOwners.get(ownerId);
        if (locks != null) {
            synchronized (locks) {
                for (Iterator it = locks.iterator(); it.hasNext();) {
                    GenericLock lock = (GenericLock) it.next();
                    lock.release(ownerId);
                    it.remove();
                }
            }
        }
    }

    /**
     * @see LockManager#getAll(Object)
     */
    public Set getAll(Object ownerId) {
        Set locks = (Set) globalOwners.get(ownerId);
        if (locks == null) {
            return new HashSet();
        } else {
            return locks;
        }
    }

    protected void addOwner(Object ownerId, GenericLock lock) {
        synchronized (globalOwners) {
            Set locks = (Set) globalOwners.get(ownerId);
            if (locks == null) {
                locks = Collections.synchronizedSet(new HashSet());
                globalOwners.put(ownerId, locks);
            }
            locks.add(lock);
        }
    }

    protected void removeOwner(Object ownerId, GenericLock lock) {
        Set locks = (Set) globalOwners.get(ownerId);
        if (locks != null) {
            locks.remove(lock);
        }
    }

    protected void addWaiter(GenericLock lock, Object ownerId) {
        synchronized (waitsForLock) {
            Set waiters = (Set) waitsForLock.get(lock);
            if (waiters == null) {
                waiters = Collections.synchronizedSet(new HashSet());
                waitsForLock.put(lock, waiters);
            }
            waiters.add(ownerId);
        }
    }

    protected void removeWaiter(GenericLock lock, Object ownerId) {
        Set waiters = (Set) waitsForLock.get(lock);
        if (waiters != null) {
            waiters.remove(ownerId);
        }
    }

    protected boolean wouldDeadlock(Object ownerId, GenericLock lock, int targetLockLevel,
            int compatibility, Set conflicts) {
        if (conflicts != null) {
            // these are our locks
            Set locks = (Set) globalOwners.get(ownerId);
            if (locks != null) {
                for (Iterator i = locks.iterator(); i.hasNext();) {
                    GenericLock mylock = (GenericLock) i.next();
                    // these are the ones waiting for one of our locks
                    Set waiters = (Set) waitsForLock.get(mylock);
                    if (waiters != null) {
                        synchronized (waiters) {
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
        }
        return false;
    }

    protected boolean releaseTimedOutOwners() {
        boolean released = false;
        synchronized (globalTimeouts) {
            for (Iterator it = globalTimeouts.entrySet().iterator(); it.hasNext();) {
                Map.Entry entry = (Map.Entry) it.next();
                Object ownerId = entry.getKey();
                long timeout = ((Long)entry.getValue()).longValue();
                long now = System.currentTimeMillis();
                if (timeout < now) {
                    releaseAll(ownerId);
                    timedOutOwners.add(ownerId);
                    it.remove();
                    released = true;
                }
            }
        }
        return released;
    }
    
    protected long getNextGlobalConflictTimeout(Set conflicts) {
        long minTimeout = -1;
        long now = System.currentTimeMillis();
        if (conflicts != null) {
            synchronized (globalTimeouts) {
                for (Iterator it = globalTimeouts.entrySet().iterator(); it.hasNext();) {
                    Map.Entry entry = (Map.Entry) it.next();
                    Object ownerId = entry.getKey();
                    if (conflicts.contains(ownerId)) {
                        long timeout = ((Long) entry.getValue()).longValue();
                        if (minTimeout == -1 || timeout < minTimeout) {
                            minTimeout = timeout;
                        }
                    }
                }
            }
        }
        return minTimeout;
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
    
    protected void timeoutCheck(Object ownerId) throws LockException {
        if (timedOutOwners.contains(ownerId)) {
            throw new LockException(
                    "All locks of owner "
                            + ownerId
                            + " have globally timed out."
                            + " You will not be able to to continue with this owner until you call releaseAll.",
                    LockException.CODE_TIMED_OUT, null);
        }
    }
}
