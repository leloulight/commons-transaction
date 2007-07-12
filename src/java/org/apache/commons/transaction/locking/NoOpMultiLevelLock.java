package org.apache.commons.transaction.locking;

import java.util.concurrent.locks.Lock;

public class NoOpMultiLevelLock implements MultiLevelLock {
    private final transient NoOpLock internalLock = new NoOpLock();

    
    public Lock getLock(int level) {
        return internalLock;
    }


    public Lock readLock() {
        return internalLock;
    }


    public Lock writeLock() {
        return internalLock;
    }

}
