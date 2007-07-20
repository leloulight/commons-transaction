package org.apache.commons.transaction.locking;


public class IntrinsicLockManager extends GenericLockManager<Object, Object> implements LockManager<Object, Object> {

    @Override
    public Object create() {
        return new Object();
    }

}
