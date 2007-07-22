package org.apache.commons.transaction.resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;

import org.apache.commons.transaction.locking.LockException;

public interface StreamableResource {
    String getPath();

    boolean isDirectory();

    boolean isFile();

    Collection<StreamableResource> getChildren() throws IOException, LockException;

    StreamableResource getParent() throws IOException, LockException;

    InputStream readStream() throws IOException, LockException;

    OutputStream writeStream(boolean append) throws IOException, LockException;

    boolean delete() throws IOException, LockException;

    boolean move(String destinationpath) throws IOException, LockException;

    boolean copy(String destinationpath) throws IOException, LockException;

    boolean exists();

    void createAsDirectory() throws IOException, LockException;

    void createAsFile() throws IOException, LockException;

    // plus more general properties
    // among them could be length, lastmodfied, etc.
    Object getProperty(String name);

    Object setProperty(String name, Object newValue);
    Object removeProperty(String name);
    
    // plus locking methods
    void readLock();

    void writeLock();

    boolean tryReadLock();

    boolean tryWriteLock();
}
