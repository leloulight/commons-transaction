package org.apache.commons.transaction.locking;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// TODO: Add wrappers to include failfast deadlock check
// possible actions after deadlock detected (done by user)
// (I) rollback
// (II) do not acquire the lock, but wait for a while (would that do any good? hmmm?)
// TODO: Synchronize properly
public class ReadWriteLockManager extends GenericLockManager<Object, ReadWriteLock> implements
        LockManager<Object, ReadWriteLock> {

    protected Map<Thread, Long> effectiveGlobalTimeouts = new ConcurrentHashMap<Thread, Long>();

    // FIXME: This is Java 1.6 only!
    protected Set<Thread> timedOutThreads = new ConcurrentSkipListSet<Thread>();;

    public void begin(long timeoutMSecs) {
        startTimeoutTimer(timeoutMSecs);
    }

    public void end() {
        releaseAll();
    }

    protected void releaseAll() {
        Iterable<ReadWriteLock> locks = getAllForCurrentThread();
        for (ReadWriteLock lock : locks) {
            lock.readLock().unlock();
            lock.writeLock().unlock();
        }

    }

    protected void startTimeoutTimer(long timeoutMSecs) {
        long now = System.currentTimeMillis();
        long timeout = now + timeoutMSecs;
        effectiveGlobalTimeouts.put(Thread.currentThread(), new Long(timeout));
    }

    @Override
    public ReadWriteLock create() {
        return new TrackingReentrantReadWriteLock();
    }

    // returns a list of threads that could be rolledback to prevent the deadlock
    // that does not mean you actually have to, though.
    protected Collection<Thread> wouldDeadlock(Lock lock) {
        return null;
    }

    protected boolean checkForTimeout(Thread thread) throws LockException {
        Long timeout = effectiveGlobalTimeouts.get(thread);
        long now = System.currentTimeMillis();
        return (timeout != null && now > timeout.longValue());
    }

    protected void clearAllTimedOutThreads() {
        
    }
    
    public class TrackingReentrantReadWriteLock extends ReentrantReadWriteLock {

    }

    public class TrackingReadLock extends ReentrantReadWriteLock.ReadLock {

        protected TrackingReadLock(ReentrantReadWriteLock lock) {
            super(lock);
            // TODO Auto-generated constructor stub
        }

    }

    public class TrackingWriteLock extends ReentrantReadWriteLock.WriteLock {

        protected TrackingWriteLock(ReentrantReadWriteLock lock) {
            super(lock);
            // TODO Auto-generated constructor stub
        }
    }

    public class LockWrapper implements Lock {

        private final Lock wrappedLock;

        public LockWrapper(Lock wrappedLock) {
            this.wrappedLock = wrappedLock;
        }

        public void lock() throws LockException {
            // XXX
            // we do not allow for uninterruptable operation, so delegate
            // this to #lockInterruptibly() and rethrow the exception to a
            // lockexception
            try {
                lockInterruptibly();
            } catch (InterruptedException e) {
                throw new LockException(e, LockException.Code.INTERRUPTED);
            }
            // TODO: alternative what be this:
            // throw new UnsupportedOperationException("Uninterruptable
            // operation are not supported!");
            // Can't decide which is the better option...
        }

        public void lockInterruptibly() throws InterruptedException, LockException {
            wrappedLock.lockInterruptibly();
        }

        public Condition newCondition() {
            return wrappedLock.newCondition();
        }

        public boolean tryLock() {
            return wrappedLock.tryLock();
        }

        public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
            return wrappedLock.tryLock(time, unit);
        }

        public void unlock() {
            wrappedLock.unlock();
        }
    }
}
