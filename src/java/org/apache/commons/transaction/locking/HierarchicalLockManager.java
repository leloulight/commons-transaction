package org.apache.commons.transaction.locking;


// Takes care of hierarchical locking with folders and resources
public interface HierarchicalLockManager<M> extends LockManager<String, M> {
    public void lockAsFolder(String path, boolean exclusive) throws LockException;
    public void lockAsResource(String path, boolean exclusive) throws LockException;
}
