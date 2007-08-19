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

/**
 * Interface to manage locks on hierarchically organized resources.
 * 
 * <p>
 * Instead of a single key specifying a resource this manager expects a complete
 * path to it. This path can be used to perform a number of lock requests to
 * ensure the resource is properly locked inside the hierarchy. Which lock
 * requests are performed is determined by the specific implementation.
 * 
 * @see DefaultHierarchicalLockManager
 */
public interface HierarchicalLockManager<K, M> extends LockManager<K, M> {

    /**
     * Locks a specific resource denoted by a resource manager it holds and a
     * path. This requests ensures that the resource is properly locked in its
     * hierarchy, possibly by issuing a number of additional lock requests.
     * 
     * @param resourceManager
     *            resource manager that tries to acquire a lock
     * @param path
     *            the complete path to the resource to be locked
     * @param exclusive
     *            <code>true</code> if this lock shall be acquired in
     *            exclusive mode, <code>false</code> if it can be shared by
     *            other threads
     * @throws LockException
     *             if the lock could not be acquired, possibly because of a
     *             timeout or a deadlock
     */
    public void lockInHierarchy(M resourceManager, String path, boolean exclusive)
            throws LockException;
}
