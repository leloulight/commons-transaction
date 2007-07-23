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

public interface ManageableResourceManager extends TransactionalResourceManager {
    void setRollbackOnly();

    boolean commitCanFail();

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

    public boolean isReadOnlyTransaction();

    public void joinTransaction(LockManager lm);

}
