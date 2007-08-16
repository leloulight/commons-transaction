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
 * 
 * <p>
 * Implementations are free to decide whether they want to make use of the
 * <code>exclusive</code> flag passed in
 * {@link #lock(Object, Object, boolean)} and
 * {@link #tryLock(Object, Object, boolean)}.
 * 
 * <p>
 * You can plug in your own lock manager version most easily. However, for
 * advanced features this will most likely require a custom implementation of {@link Lock} as well.
 * 
 * 
 * @param <K>
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
     * @param managedResource
     *            resource for on which this block of work shall be done
     */
    void lock(M managedResource, K key, boolean exclusive) throws LockException;

    boolean tryLock(M managedResource, K key, boolean exclusive);

}
