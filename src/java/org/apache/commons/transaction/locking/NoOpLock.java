package org.apache.commons.transaction.locking;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class NoOpLock implements Lock {

    private final transient NoOpCondition internalCondition = new NoOpCondition();

    @Override
    public void lock() {
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();
    }

    @Override
    public Condition newCondition() {
        return internalCondition;
    }

    @Override
    public boolean tryLock() {
        return true;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return true;
    }

    @Override
    public void unlock() {
    }

}
