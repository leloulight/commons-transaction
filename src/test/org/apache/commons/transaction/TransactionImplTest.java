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
        Transaction t = new TransactionImpl(lm);
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
