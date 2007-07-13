package org.apache.commons.transaction.locking;


public class NativeLockManager extends GenericLockManager<Object, Object> implements LockManager<Object, Object> {

    public Object create() {
        return new Object();
    }

}
