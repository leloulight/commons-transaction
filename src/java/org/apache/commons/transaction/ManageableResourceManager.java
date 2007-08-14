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

import org.apache.commons.transaction.locking.LockManager;

/**
 * Needs to be implemented by all resource managers that want to take part in a
 * {@link Transaction combined transaction}. This interface is not meant for
 * user interaction.
 * 
 */
public interface ManageableResourceManager extends TransactionalResourceManager {

    /**
     * Checks whether this resource manager is willing and able to commit its
     * part of the complex transaction.
     * 
     * @return <code>true</code> if this resource manager can commit its part
     *         of the transaction
     */
    boolean prepareTransaction();

    /**
     * Instructs the resource manager to forget about the current transaction.
     * 
     */
    void forgetTransaction();

    /**
     * Lets this resource manager join a transaction that is protected by a
     * common lock manager. An implementation is required to perform all locking
     * operations on the given lock manager as long as it takes part in the
     * complex transaction.
     * 
     * @param lm
     *            the common lock maanger
     */
    void joinTransaction(LockManager<Object, Object> lm);

    /**
     * Checks whether this resource manager allows a rollback as the only valid
     * outcome. Once a transaction is marked for rollback there is no way to
     * undo this. A transaction that is marked for rollback can not be
     * committed.
     * 
     * @return <code>true</code> if this resource manager can only roll back
     */
    boolean isRollbackOnly();

    /**
     * Checks if there had been any write operations on this resource manager
     * since the start of the transaction. If not the transaction is not
     * required to call either
     * {@link TransactionalResourceManager#commitTransaction()} or
     * {@link TransactionalResourceManager#rollbackTransaction()}, but only
     * {@link #forgetTransaction()}.
     * 
     * @return <code>true</code> if there had been read operations only
     */
    boolean isReadOnly();

    /**
     * Checks whether a tried commit could possibly fail because of logical
     * reasons.
     * 
     * @return <code>true</code> if a commit could fail
     */
    boolean commitCanFail();
}
