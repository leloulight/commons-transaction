package org.apache.commons.transaction.locking;

import java.util.concurrent.locks.Lock;

public interface MultiLevelLock {
    Lock getLock(int level);
}
