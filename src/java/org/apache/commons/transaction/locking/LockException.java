/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.transaction.locking;

/**
 * Exception displaying a lock problem.
 * 
 * @version $Id: LockException.java 493628 2007-01-07 01:42:48Z joerg $
 * @since 1.1
 */
public class LockException extends RuntimeException {

    /**
     * 
     */
    private static final long serialVersionUID = -6450258112934501320L;

    public enum Code {
        /**
         * Thread has been interrupted while waiting for lock.
         */
        INTERRUPTED,
        
        /**
         * Maximum wait time for a lock has been exceeded.
         */
        TIMED_OUT,
        
        /**
         * Locking request canceled because of deadlock.
         */
        DEADLOCK_VICTIM,
        
        /**
         * A conflict between two optimistic transactions occured.
         * 
         */
        CONFLICT,
        
        /**
         * A commit was tried, but did not succeed.
         * 
         */
        COMMIT_FAILED
    }

    protected Object resourceId;

    protected Code code;

    public LockException(String message, Code code, Object resourceId) {
        super(message);
        this.code = code;
        this.resourceId = resourceId;
    }

    public LockException(Code code, Object resourceId) {
        this.code = code;
        this.resourceId = resourceId;
    }

    public LockException(String message, Throwable cause, Object resourceId) {
        super(message, cause);
        this.resourceId = resourceId;
    }

    public LockException(Throwable cause, Object resourceId) {
        super(cause);
        this.resourceId = resourceId;
    }

    public LockException(Throwable cause) {
        super(cause);
    }

    public LockException(Throwable cause, Code code) {
        super(cause);
        this.code = code;
    }

    /**
     * Returns the formal reason for the exception.
     * 
     * @return the reason code
     */
    public Code getCode() {
        return code;
    }

    /**
     * Returns the resource the lock was tried on.
     * 
     * @return the resource or <code>null</code> if not applicable
     */
    public Object getResourceId() {
        return resourceId;
    }

}