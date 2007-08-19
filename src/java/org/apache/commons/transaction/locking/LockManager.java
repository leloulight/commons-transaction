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
package org.apache.commons.transaction.locking;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * Main interface to acquire and manage locks.
 * 
 * <p>
 * The idea is that a block of work is done between the calls of
 * {@link #startWork(long, TimeUnit)} and {@link #endWork()}. Each resource
 * touched can be locked either by {@link #lock(Object, Object, boolean)} or
 * {@link #tryLock(Object, Object, boolean)}. In case the timeout given in
 * {@link #startWork(long, TimeUnit)} is exceeded locking requests are
 * terminated with a {@link LockException}. Finally, {@link #endWork()}
 * releases all locks in a bulk.
 * 
 * <p>
 * The benefit of such an implementation is that you can no longer forget to
 * release locks that would otherwise lurk around forever. Additionally, as
 * there is a central manager for all locks implementations can perform
 * additional checks like, for example, a deadlock detection.
 * 
 * <p>
 * Implementations are free to decide whether they want to make use of the
 * <code>exclusive</code> flag passed in
 * {@link #lock(Object, Object, boolean)} and
 * {@link #tryLock(Object, Object, boolean)}.
 * 
 * <p>
 * You can plug in your own lock manager version most easily. However, for
 * advanced features this will most likely require a custom implementation of
 * {@link Lock} as well.
 * 
 * @see RWLockManager
 * @see HierarchicalLockManager
 * @see DefaultHierarchicalLockManager
 * @see AbstractLockManager
 * @see SimpleLockManager
 */
public interface LockManager<K, M> {
    /**
     * Starts a block of work for which a certain set of locks is required.
     * 
     * @param timeout
     *            the maximum time for the whole work to take before it times
     *            out
     * @param unit
     *            the time unit of the {@code timeout} argument
     */
    void startWork(long timeout, TimeUnit unit);

    /**
     * Ends a block of work that has been started in
     * {@link #startWork(long, TimeUnit)}. All locks acquired will be released.
     * All registered locks will be unregistered from this lock manager.
     * 
     */
    void endWork();

    /**
     * Locks a resource denoted by a key and a resource manager.
     * 
     * @param resourceManager
     *            resource manager that tries to acquire a lock
     * @param key
     *            the key for the resource to be locked
     * @param exclusive
     *            <code>true</code> if this lock shall be acquired in
     *            exclusive mode, <code>false</code> if it can be shared by
     *            other threads
     * @throws LockException
     *             if the lock could not be acquired, possibly because of a
     *             timeout or a deadlock
     */
    void lock(M resourceManager, K key, boolean exclusive) throws LockException;

    /**
     * Tries to acquire a lock on a resource denoted by a key and a resource
     * manager.
     * 
     * @param resourceManager
     *            resource manager that tries to acquire a lock
     * @param key
     *            the key for the resource to be locked
     * @param exclusive
     *            <code>true</code> if this lock shall be acquired in
     *            exclusive mode, <code>false</code> if it can be shared by
     *            other threads
     * @return <code>true</code> if the lock was acquired, <code>false</code>
     *         otherwise
     */
    boolean tryLock(M resourceManager, K key, boolean exclusive);

}
