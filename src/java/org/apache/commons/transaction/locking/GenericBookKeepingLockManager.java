package org.apache.commons.transaction.locking;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class GenericBookKeepingLockManager<K, L> extends GenericLockManager<K, L> implements BookKeepingLockManager<K, L>{
    
    protected final ConcurrentHashMap<K, L> globalOwners = new ConcurrentHashMap<K, L>();

    public Set<L> getAllLocksForCurrentThread() {
        // TODO Auto-generated method stub
        return null;
    }

}
