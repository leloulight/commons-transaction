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

/**
 * 
 * @author olli
 * 
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
    public void startWork(long timeout, TimeUnit unit);

    /**
     * Ends a block of work that has been started in
     * {@link #startWork(long, TimeUnit)}. All locks acquired will be released.
     * All registered locks will be unregistered from this lock manager.
     * 
     */
    public void endWork();

    /**
     * @param managedResource
     *            resource for on which this block of work shall be done
     */
    public void lock(M managedResource, K key, boolean exclusive) throws LockException;
    public boolean tryLock(M managedResource, K key, boolean exclusive);

}
