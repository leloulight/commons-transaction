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
package org.apache.commons.transaction;

import java.util.concurrent.TimeUnit;

/**
 * A managed transaction meant as interface to the user. 
 * <p>Should be used to
 * combine multiple resource managers into a complex transaction. Once a
 * resource manager has joined such a complex transaction all transactional
 * control is performed by this transaction. Do not call transactional methods
 * on the resource managers directly.
 * 
 * <p>This is a light weight replacement for a complex 2PC xa transaction.
 * 
 * @see DefaultTransaction
 */
public interface Transaction {
    /**
     * Starts a new transactions having a specific timeout. You can
     * {@link #enlistResourceManager(ManageableResourceManager) add resource managers}
     * before start or afterwards.
     * 
     * @param timeout
     *            the maximum time this transaction can run before it times out
     * @param unit
     *            the time unit of the {@code timeout} argument
     */
    void start(long timeout, TimeUnit unit);

    /**
     * Checks whether this transaction allows a rollback as the only valid
     * outcome. Once a transaction is marked for rollback there is no way to
     * undo this. A transaction that is marked for rollback can not be
     * committed.
     * 
     * @return <code>true</code> if this transaction can only rolled back
     */
    boolean isRollbackOnly();

    /**
     * Rolls back the complex transaction meaning that all changes made to
     * participating resource managers are undone.
     */
    void rollback();

    /**
     * Commits the complex transaction meaning that all changes made to
     * participating resource managers are made permanent.
     */
    void commit();

    /**
     * Adds a resource manager to this complex transaction. This means the
     * resource manager will from now on be controlled by this transaction.
     * Access to transactional methods is not allowed until the complex
     * transaction has finished. Of course, it is legal to call the other
     * methods if this manager to perform some real work.
     * 
     * @param resourceManager
     *            the resource manager to add
     */
    void enlistResourceManager(ManageableResourceManager resourceManager);

}
