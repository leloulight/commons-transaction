package org.apache.commons.transaction.locking;

import java.util.Set;

public interface BookKeepingLockManager<K, L> extends LockManager<K, L> {
    public Set<L> getAllLocksForCurrentThread();
    
    // TODO: We need a means for a global timeout or at least
    // something to demarcate transaction boundaries

}
