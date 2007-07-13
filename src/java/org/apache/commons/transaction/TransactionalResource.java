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

public interface TransactionalResource {
    /**
     * TODO
     * 
     * @param mSecs
     */
    public void setTransactionTimeout(long mSecs);

    /**
     * TODO
     * 
     * @return
     */
    public boolean prepareTransaction();

    /**
     * Starts a new transaction and associates it with the current thread. All
     * subsequent changes in the same thread made to the map are invisible from
     * other threads until {@link #commitTransaction()} is called. Use
     * {@link #rollbackTransaction()} to discard your changes. After calling
     * either method there will be no transaction associated to the current
     * thread any longer. <br>
     * <br>
     * <em>Caution:</em> Be careful to finally call one of those methods, as
     * otherwise the transaction will lurk around for ever.
     * 
     * @see #commitTransaction()
     * @see #rollbackTransaction()
     */
    public void startTransaction();

    /**
     * Checks whether this transaction has been marked to allow a rollback as
     * the only valid outcome. This can be set my method
     * {@link #markTransactionForRollback()} or might be set internally be any
     * fatal error. Once a transaction is marked for rollback there is no way to
     * undo this. A transaction that is marked for rollback can not be
     * committed, also rolled back.
     * 
     * @return <code>true</code> if this transaction has been marked for a
     *         roll back
     * @see #markTransactionForRollback()
     */
    public boolean isTransactionMarkedForRollback();

    /**
     * Marks the current transaction to allow only a rollback as valid outcome.
     * 
     * @see #isTransactionMarkedForRollback()
     */
    public void markTransactionForRollback();

    /**
     * Discards all changes made in the current transaction and deletes the
     * association between the current thread and the transaction.
     * 
     * @see #startTransaction()
     * @see #commitTransaction()
     */
    public void rollbackTransaction();

    /**
     * Commits all changes made in the current transaction and deletes the
     * association between the current thread and the transaction.
     * 
     * @see #startTransaction()
     * @see #rollbackTransaction()
     */
    public void commitTransaction();

    /**
     * Returns the state of the current transaction.
     * 
     * @return state of the current transaction as decribed in the
     *         {@link Status} interface.
     */
    public Status getTransactionState();

    /**
     * Checks if any write operations have been performed inside this
     * transaction.
     * 
     * @return <code>true</code> if no write opertation has been performed
     *         inside the current transaction, <code>false</code> otherwise
     */
    public boolean isReadOnly();

}
