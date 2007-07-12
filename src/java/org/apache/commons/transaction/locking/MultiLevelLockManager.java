package org.apache.commons.transaction.locking;


public class MultiLevelLockManager extends GenericLockManager<Object, MultiLevelLock> implements LockManager<Object, MultiLevelLock> {

    public MultiLevelLock create() {
        // FIXME: Needs maximum level
        return new MultiLevelLockImpl();
    }
}
