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
package org.apache.commons.transaction.file;

import java.util.concurrent.locks.ReadWriteLock;

import org.apache.commons.transaction.locking.BookKeepingLockManager;

public class BlockingLockPolicy implements LockPolicy {
    protected BookKeepingLockManager<String, ReadWriteLock> lm;

    public BookKeepingLockManager<String, ReadWriteLock> getLm() {
        return lm;
    }

    public void setLm(BookKeepingLockManager<String, ReadWriteLock> lm) {
        this.lm = lm;
    }

    protected ReadWriteLock initLock(String id) {
        return getLm().putIfAbsent(id, getLm().create());
    }

    public boolean readLock(String id) throws InterruptedException {
        initLock(id).readLock().lock();
        return true;
    }

    public boolean writeLock(String id) throws InterruptedException {
        initLock(id).writeLock().lock();
        return true;

    }
}
