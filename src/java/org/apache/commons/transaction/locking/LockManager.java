package org.apache.commons.transaction.locking;


public interface LockManager<K, L> {
    public L getLock(K key);

    public L createLockIfAbsent(K key, L lock);

    public L removeLock(K key);
}
