package org.apache.commons.transaction.locking;

public class HierarchicalRWLockManager<M> extends RWLockManager<String, M> implements
        HierarchicalLockManager<M> {

    public void lockAsFolder(String path, boolean exclusive) throws LockException {
        // TODO Auto-generated method stub
        
    }

    public void lockAsResource(String path, boolean exclusive) throws LockException {
        // TODO Auto-generated method stub
        
    }
}
