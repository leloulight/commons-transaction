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

import java.util.concurrent.locks.ReadWriteLock;


// Needs a timeout, do not do nasty "real" block
public class BlockingReadWriteLockPolicy implements LockPolicy {
    protected LockManager<Object, ReadWriteLock> lm;

    public LockManager<Object, ReadWriteLock> getLm() {
        return lm;
    }

    public void setLm(LockManager<Object, ReadWriteLock> lm) {
        this.lm = lm;
    }

    protected ReadWriteLock initLock(Object id) {
        return getLm().putIfAbsent(id, getLm().create());
    }

    public boolean readLock(Object id, long timeoutMSecs) throws LockException {
        initLock(id).readLock().lock();
        return true;
    }

    // FIXME needs to be interruptable
    public boolean writeLock(Object id, long timeoutMSecs) throws LockException {
        initLock(id).writeLock().lock();
        return true;

    }

    public boolean releaseAll() {
        Iterable<ReadWriteLock> locks = getLm().getAllForCurrentThread();

        for (ReadWriteLock lock : locks) {
            lock.readLock().unlock();
            lock.writeLock().unlock();
        }
        return true;
    }
}
