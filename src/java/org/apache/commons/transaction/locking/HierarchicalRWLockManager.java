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

public class HierarchicalRWLockManager<M> implements HierarchicalLockManager<Object, M> {

    private final String rootPath;

    private final LockManager<Object, M> lm;

    public HierarchicalRWLockManager(String rootPath, LockManager<Object, M> lm) {
        this.rootPath = rootPath;
        this.lm = lm;
    }

    public void lockInHierarchy(M managedResource, String path, boolean exclusive)
            throws LockException {
        // strip off root path
        // TODO misses sane checks
        if (!path.startsWith(rootPath)) {
            throw new LockException("Could not lock a path (" + path
                    + ") that is not under the rootPath (" + rootPath + ")");
        }
        String relativePath = path.substring(rootPath.length());

        // this is the root path we want to lock
        if (relativePath.length() == 0) {
            lock(managedResource, "/", exclusive);
            return;
        }

        // always read lock root
        lock(managedResource, "/", false);

        String[] segments = relativePath.split("\\\\");
        StringBuffer currentPath = new StringBuffer(relativePath.length());
        // for root path
        currentPath.append('/');

        // skip first segment which is just empty
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];

            currentPath.append(segment).append('/');
            String key = currentPath.toString();

            if (i == segments.length - 1) {
                // this is the resource itself
                lock(managedResource, key, exclusive);
            } else {
                // this is one of the parent path segments
                lock(managedResource, key, false);
            }
        }
    }

    public void endWork() {
        lm.endWork();
    }

    public void lock(M managedResource, Object key, boolean exclusive) throws LockException {
        lm.lock(managedResource, key, exclusive);
    }

    public void startWork(long timeout, TimeUnit unit) {
        lm.startWork(timeout, unit);

    }

    public boolean tryLock(M managedResource, Object key, boolean exclusive) {
        return lm.tryLock(managedResource, key, exclusive);
    }
}
