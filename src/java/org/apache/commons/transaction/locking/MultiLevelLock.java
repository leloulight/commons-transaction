package org.apache.commons.transaction.locking;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

public interface MultiLevelLock extends ReadWriteLock {
    Lock getLock(int level);
}
