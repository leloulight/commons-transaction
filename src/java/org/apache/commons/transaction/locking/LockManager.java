/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//transaction/src/java/org/apache/commons/transaction/locking/LockManager.java,v 1.2 2004/12/14 12:12:46 ozeigermann Exp $
 * $Revision: 1.2 $
 * $Date: 2004/12/14 12:12:46 $
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

import java.util.Set;

/**
 * 
 * A manager for multi level locks on resources. Encapsulates creation, removal, and retrieval of locks.
 * Each resource can have at most a single lock. However, it may be possible for more than one
 * accessing entity to have influence on this lock via different lock levels that may be 
 * provided by the according implementation of {@link MultiLevelLock}. 
 * 
 * @version $Revision: 1.2 $
 * @see MultiLevelLock
 */
public interface LockManager {

    /**
     * Tries to acquire a lock on a resource. <br>
     * <br>
     * This method does not block, but immediatly returns. If a lock is not
     * available <code>false</code> will be returned.
     * 
     * @param ownerId
     *            a unique id identifying the entity that wants to acquire this
     *            lock
     * @param resourceId
     *            the resource to get the level for
     * @param targetLockLevel
     *            the lock level to acquire
     * @param reentrant
     *            <code>true</code> if this request shall not be blocked by
     *            other locks held by the same owner
     * @return <code>true</code> if the lock has been acquired, <code>false</code> otherwise
     *  
     */
    public boolean tryLock(Object ownerId, Object resourceId, int targetLockLevel, boolean reentrant);

    /**
     * Tries to acquire a lock on a resource. <br>
     * <br>
     * This method blocks and waits for the lock in case it is not avaiable. If
     * there is a timeout or a deadlock or the thread is interrupted a
     * LockException is thrown.
     * 
     * @param ownerId
     *            a unique id identifying the entity that wants to acquire this
     *            lock
     * @param resourceId
     *            the resource to get the level for
     * @param targetLockLevel
     *            the lock level to acquire
     * @param reentrant
     *            <code>true</code> if this request shall not be blocked by
     *            other locks held by the same owner
     * @throws LockException
     *             will be thrown when the lock can not be acquired
     */
    public void lock(Object ownerId, Object resourceId, int targetLockLevel, boolean reentrant)
            throws LockException;

    /**
     * Tries to acquire a lock on a resource. <br>
     * <br>
     * This method blocks and waits for the lock in case it is not avaiable. If
     * there is a timeout or a deadlock or the thread is interrupted a
     * LockException is thrown.
     * 
     * @param ownerId
     *            a unique id identifying the entity that wants to acquire this
     *            lock
     * @param resourceId
     *            the resource to get the level for
     * @param targetLockLevel
     *            the lock level to acquire
     * @param reentrant
     *            <code>true</code> if this request shall not be blocked by
     *            other locks held by the same owner
     * @param timeoutMSecs
     *            specifies the maximum wait time in milliseconds
     * @throws LockException
     *             will be thrown when the lock can not be acquired
     */
    public void lock(Object ownerId, Object resourceId, int targetLockLevel, boolean reentrant,
            long timeoutMSecs) throws LockException;

    /**
     * Gets the lock level held by certain owner on a certain resource.
     * 
     * @param ownerId the id of the owner of the lock
     * @param resourceId the resource to get the level for
     */
    public int getLevel(Object ownerId, Object resourceId);

    /**
     * Releases all locks for a certain resource held by a certain owner.
     * 
     * @param ownerId the id of the owner of the lock
     * @param resourceId the resource to releases the lock for
     */
    public void release(Object ownerId, Object resourceId);

    /**
     * Releases all locks (partially) held by an owner.
     * 
     * @param ownerId the id of the owner
     */
    public void releaseAll(Object ownerId);
    
    /**
     * Gets all locks (partially) held by an owner.
     * 
     * @param ownerId the id of the owner
     * @return all locks held by ownerId
     */
    public Set getAll(Object ownerId);

    
    /**
     * Either gets an existing lock on the specified resource or creates one if none exists. 
     * This methods guarantees to do this atomically. 
     * 
     * @param resourceId the resource to get or create the lock on
     * @return the lock for the specified resource
     * 
     * @deprecated Direct access to locks is discouraged as dead lock detection and lock
     * maintenance now relies on the call to {@link #lock(Object, Object, int, int, long)}, 
     * {@link #tryLock(Object, Object, int, int)}, {@link #release(Object, Object)} and 
     * {@link #releaseAll(Object)}. This method will be deleted. 
     */
    public MultiLevelLock atomicGetOrCreateLock(Object resourceId);

    /**
     * Gets an existing lock on the specified resource. If none exists it returns <code>null</code>. 
     * 
     * @param resourceId the resource to get the lock for
     * @return the lock on the specified resource
     * 
     * @deprecated Direct access to locks is discouraged as dead lock detection and lock
     * maintenance now relies on the call to {@link #lock(Object, Object, int, int, long)}, 
     * {@link #tryLock(Object, Object, int, int)}, {@link #release(Object, Object)} and 
     * {@link #releaseAll(Object)}. This method will be deleted. 
     */
    public MultiLevelLock getLock(Object resourceId);

    /**
     * Removes the specified lock from the associated resource. 
     * 
     * <em>Caution:</em> This does not release the lock, but only moves it out
     * of the scope of this manager. Use {@link #release(Object, Object)} for that.
     * 
     * @param lock the lock to be removed
     */
    public void removeLock(MultiLevelLock lock);
}
