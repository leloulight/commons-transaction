package org.apache.commons.transaction.locking;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class ReadWriteLockManager extends GenericLockManager<Object, ReadWriteLock> implements LockManager<Object, ReadWriteLock> {

    public ReadWriteLock create() {
        return new ReentrantReadWriteLock();
    }

}
