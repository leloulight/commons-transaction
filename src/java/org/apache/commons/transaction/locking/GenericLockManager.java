package org.apache.commons.transaction.locking;

import java.util.concurrent.ConcurrentHashMap;

public abstract class GenericLockManager<K, L> implements LockManager<K, L> {
    
    protected final ConcurrentHashMap<K, L> globalLocks = new ConcurrentHashMap<K, L>();

    @Override
    public L get(K key) {
        return globalLocks.get(key);
    }
    
    @Override
    public L putIfAbsent(K key, L lock) {
        L existingLock = get(key);
        if (existingLock == null) {
            L concurrentlyInsertedLock = globalLocks.putIfAbsent(key, lock);
            if (concurrentlyInsertedLock != null)
                lock = concurrentlyInsertedLock;
        }
        return lock;
        
    }
    
    @Override
    public L remove(K key) {
        return globalLocks.remove(key);
    }

}
