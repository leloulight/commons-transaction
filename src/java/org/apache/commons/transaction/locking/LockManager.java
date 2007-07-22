package org.apache.commons.transaction.locking;

import java.util.concurrent.TimeUnit;

/**
 * 
 * @author olli
 * 
 * 
 * 
 * @param <K>
 */
public interface LockManager<K, M> {
    /**
     * Starts a block of work for which a certain set of locks is required.
     * 
     * @param timeout
     *            the maximum time for the whole work to take before it times
     *            out
     * @param unit
     *            the time unit of the {@code timeout} argument
     */
    public void startWork(long timeout, TimeUnit unit);

    /**
     * Ends a block of work that has been started in
     * {@link #startWork(long, TimeUnit)}. All locks acquired will be released.
     * All registered locks will be unregistered from this lock manager.
     * 
     */
    public void endWork();

    public boolean isWorking();

    /**
     * @param managedResource
     *            resource for on which this block of work shall be done
     */
    public void lock(M managedResource, K key, boolean exclusive) throws LockException;

}
