package org.apache.commons.transaction.locking;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class GenericLockManager<K, L> implements LockManager<K, L> {

    protected ConcurrentHashMap<K, L> locks = new ConcurrentHashMap<K, L>();

    protected Map<Thread, Set<L>> threads = new ConcurrentHashMap<Thread, Set<L>>();

    @Override
    public L get(K key) {
        return locks.get(key);
    }

    @Override
    public L putIfAbsent(K key, L lock) {
        L existingLock = get(key);
        if (existingLock == null) {
            L concurrentlyInsertedLock = locks.putIfAbsent(key, lock);
            if (concurrentlyInsertedLock != null)
                lock = concurrentlyInsertedLock;
        }
        return lock;

    }

    @Override
    public L remove(K key) {
        return locks.remove(key);
    }

    @Override
    public Iterable<L> getAll() {
        return locks.values();
    }

    @Override
    public Iterable<L> getAllForCurrentThread() {
        return threads.get(Thread.currentThread());
    }

    public abstract L create();

}
