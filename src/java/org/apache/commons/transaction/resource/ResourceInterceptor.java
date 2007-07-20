package org.apache.commons.transaction.resource;


public interface ResourceInterceptor {
    boolean beforeCompletion(ResourceEvent event);
    void afterCompletion(ResourceEvent event);
}