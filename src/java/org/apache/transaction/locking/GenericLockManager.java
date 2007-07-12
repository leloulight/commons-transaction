package org.apache.transaction.locking;

import java.util.concurrent.ConcurrentHashMap;

public class GenericLockManager<K, L> implements LockManager<K, L> {
    
    private final ConcurrentHashMap<K, L> globalLocks = new ConcurrentHashMap<K, L>();

    @Override
    public L getLock(K key) {
        return globalLocks.get(key);
    }
    
    @Override
    public L createLockIfAbsent(K key, L lock) {
        L existingLock = getLock(key);
        if (existingLock == null) {
            L concurrentlyInsertedLock = globalLocks.putIfAbsent(key, lock);
            if (concurrentlyInsertedLock != null)
                lock = concurrentlyInsertedLock;
        }
        return lock;
        
    }
    
    @Override
    public L removeLock(K key) {
        return globalLocks.remove(key);
    }

}
