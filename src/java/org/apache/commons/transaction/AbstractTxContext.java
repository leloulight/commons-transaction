package org.apache.commons.transaction;

import org.apache.commons.transaction.locking.LockException;
import org.apache.commons.transaction.locking.LockPolicy;


public abstract class AbstractTxContext implements TxContext {
    private Status status = Status.ACTIVE;

    private long timeout = -1L;

    private long startTime = -1L;

    private long commitTime = -1L;

    private boolean readOnly = true;
    
    private LockPolicy lockPolicy;

    public AbstractTxContext() {
        startTime = System.currentTimeMillis();
    }

    protected long getRemainingTimeout() {
        long now = System.currentTimeMillis();
        return (getStartTime()- now + timeout);
    }

    public void commit() {
        commitTime = System.currentTimeMillis();
    }

    public void dispose() {
        status = Status.NO_TRANSACTION;
        getLockPolicy().releaseAll();
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean prepare() {
        return true;
    }

    public long getCommitTime() {
        return commitTime;
    }

    public void setCommitTime(long commitTimeMSecs) {
        this.commitTime = commitTimeMSecs;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTimeMSecs) {
        this.startTime = startTimeMSecs;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeoutMSecs) {
        this.timeout = timeoutMSecs;
    }
    
    private LockPolicy getLockPolicy() {
        return lockPolicy;
    }

    public void setLockPolicy(LockPolicy lockPolicy) {
        this.lockPolicy = lockPolicy;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    public void readLock(Object id) throws LockException {
        getLockPolicy().readLock(id, getRemainingTimeout());
    }

    public void writeLock(Object id)throws LockException  {
        getLockPolicy().writeLock(id, getRemainingTimeout());
    }
}
