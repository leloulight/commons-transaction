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

import junit.framework.JUnit4TestAdapter;

import org.apache.commons.transaction.locking.LockManager;
import org.apache.commons.transaction.locking.RWLockManager;
import org.apache.commons.transaction.memory.PessimisticTxMap;
import org.apache.commons.transaction.memory.TxMap;
import org.junit.Test;

public class TransactionImplTest {
    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(TransactionImplTest.class);
    }

    @Test
    public void basic() {
        LockManager lm = new RWLockManager<String, String>();
        Transaction t = new DefaultTransaction(lm);
        TxMap<String, Object> txMap1 = new PessimisticTxMap<String, Object>("TxMap1");
        t.enlistResourceManager(txMap1);
        TxMap<String, Object> txMap2 = new PessimisticTxMap<String, Object>("TxMap2");
        t.enlistResourceManager(txMap2);

        try {
            t.start(60, TimeUnit.SECONDS);
            txMap1.put("Olli", "Huhu");
            txMap2.put("Olli", "Haha");
            t.commit();
        } catch (Throwable throwable) {
            t.rollback();
        }

    }
    
    public static void main(String[] args) {
        new TransactionImplTest().basic();
    }
}
