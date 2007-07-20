package org.apache.commons.transaction.resource;

import java.io.IOException;

import org.apache.commons.transaction.locking.LockException;

public interface ResourceManager<R> {
    R getResource(String path) throws IOException, LockException;

    String getRootPath() throws IOException, LockException;

    void addInterceptor(ResourceInterceptor interceptor);

    void removeInterceptor(ResourceInterceptor interceptor);
}