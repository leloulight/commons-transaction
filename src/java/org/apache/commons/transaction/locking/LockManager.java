package org.apache.commons.transaction.locking;



public interface LockManager<K, L> {
    public L get(K key);

    public L putIfAbsent(K key, L lock);

    public L remove(K key);
    
    public L create();
    
    public Iterable<L> getAll();
    
    public Iterable<L> getAllForCurrentThread();

}
