package org.apache.commons.transaction.locking;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// TODO: Add wrappers to include failfast deadlock check
// possible actions after deadlock detected (done by user)
// I rollback
// II do not acquire the lock, but wait for a while
public class ReadWriteLockManager extends GenericLockManager<Object, ReadWriteLock> implements LockManager<Object, ReadWriteLock> {

    public ReadWriteLock create() {
        return new ReentrantReadWriteLock();
    }

}
