package org.apache.commons.transaction.resource;

public interface ResourceEvent {
    enum EventType {
        ACCESS, READ, CREATE, DELETE, WRITE, MOVE, COPY, COMMIT, ROLLBACK, PROPERTYSET
    };

    String getPath();
    String getDestinationPath();
    String propertyName();
    StreamableResource getResource();
    
    EventType getEventType();
}
