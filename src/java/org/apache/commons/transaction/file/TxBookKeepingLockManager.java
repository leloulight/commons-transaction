package org.apache.commons.transaction.file;

import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.commons.transaction.locking.BookKeepingLockManager;
import org.apache.commons.transaction.locking.GenericLockManager;

public class TxBookKeepingLockManager extends GenericLockManager<String, ReadWriteLock> implements BookKeepingLockManager<String, ReadWriteLock>{
    
    public Set<ReadWriteLock> getAllLocksForCurrentThread() {
        // TODO Auto-generated method stub
        return null;
    }

    public ReadWriteLock create() {
        // TODO Auto-generated method stub
        return null;
    }
}
