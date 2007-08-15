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
package org.apache.commons.transaction.memory;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import junit.framework.JUnit4TestAdapter;
import org.junit.Test;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.locking.LockManager;
import org.apache.commons.transaction.util.RendezvousBarrier;

/**
 * Tests for basic tx map.
 * 
 */
public class BasicTxMapTest {

    private static final Log log = LogFactory.getLog(BasicTxMapTest.class.getName());

    protected static final long BARRIER_TIMEOUT = 20000;

    // XXX need this, as JUnit seems to print only part of these strings
    protected static void report(String should, String is) {
        if (!should.equals(is)) {
            fail("\nWrong output:\n'" + is + "'\nShould be:\n'" + should + "'\n");
        }
    }

    protected static void checkCollection(Collection col, Object[] values) {
        int cnt = 0;
        int trueCnt = 0;

        for (Iterator it = col.iterator(); it.hasNext();) {
            cnt++;
            Object value1 = it.next();
            for (int i = 0; i < values.length; i++) {
                Object value2 = values[i];
                if (value2.equals(value1))
                    trueCnt++;
            }
        }
        assertEquals(cnt, values.length);
        assertEquals(trueCnt, values.length);
    }

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(BasicTxMapTest.class);
    }

    public static void main(java.lang.String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    @Test
    public void testBasic() {

        log.info("Checking basic transaction features");

        final BasicTxMap<String, String> txMap1 = new BasicTxMap("txMap1");
        final Map map1 = txMap1.getWrappedMap();

        assertTrue(txMap1.isEmpty());

        // make sure changes are propagated to wrapped map outside tx
        txMap1.put("key1", "value1");
        report("value1", (String) map1.get("key1"));
        assertFalse(txMap1.isEmpty());

        // make sure changes are propagated to wrapped map only after commit
        txMap1.startTransaction(1, TimeUnit.HOURS);
        assertFalse(txMap1.isEmpty());
        txMap1.put("key1", "value2");
        report("value1", (String) map1.get("key1"));
        report("value2", (String) txMap1.get("key1"));
        txMap1.commitTransaction();
        report("value2", (String) map1.get("key1"));
        report("value2", (String) txMap1.get("key1"));

        // make sure changes are reverted after roll back
        txMap1.startTransaction(1, TimeUnit.HOURS);
        txMap1.put("key1", "value3");
        txMap1.rollbackTransaction();
        report("value2", (String) map1.get("key1"));
        report("value2", (String) txMap1.get("key1"));
    }

    @Test
    public void testComplex() {

        log.info("Checking advanced and complex transaction features");

        final BasicTxMap<String, String> txMap1 = new BasicTxMap("txMap1");
        final Map map1 = txMap1.getWrappedMap();

        // first fill in some global values:
        txMap1.put("key1", "value1");
        txMap1.put("key2", "value2");

        // let's see if we have all values:
        log.info("Checking if global values are present");

        assertTrue(txMap1.containsValue("value1"));
        assertTrue(txMap1.containsValue("value2"));
        assertFalse(txMap1.containsValue("novalue"));

        // ... and all keys
        log.info("Checking if global keys are present");
        assertTrue(txMap1.containsKey("key1"));
        assertTrue(txMap1.containsKey("key2"));
        assertFalse(txMap1.containsKey("nokey"));

        // and now some inside a transaction
        txMap1.startTransaction(1, TimeUnit.HOURS);
        txMap1.put("key3", "value3");
        txMap1.put("key4", "value4");

        // let's see if we have all values:
        log.info("Checking if values inside transactions are present");
        assertTrue(txMap1.containsValue("value1"));
        assertTrue(txMap1.containsValue("value2"));
        assertTrue(txMap1.containsValue("value3"));
        assertTrue(txMap1.containsValue("value4"));
        assertFalse(txMap1.containsValue("novalue"));

        // ... and all keys
        log.info("Checking if keys inside transactions are present");
        assertTrue(txMap1.containsKey("key1"));
        assertTrue(txMap1.containsKey("key2"));
        assertTrue(txMap1.containsKey("key3"));
        assertTrue(txMap1.containsKey("key4"));
        assertFalse(txMap1.containsKey("nokey"));

        // now let's delete some old stuff
        log.info("Checking remove inside transactions");
        txMap1.remove("key1");
        assertFalse(txMap1.containsKey("key1"));
        assertFalse(txMap1.containsValue("value1"));
        assertNull(txMap1.get("key1"));
        assertEquals(3, txMap1.size());

        // and some newly created
        txMap1.remove("key3");
        assertFalse(txMap1.containsKey("key3"));
        assertFalse(txMap1.containsValue("value3"));
        assertNull(txMap1.get("key3"));
        assertEquals(2, txMap1.size());

        log.info("Checking remove and propagation after commit");
        txMap1.commitTransaction();

        txMap1.remove("key1");
        assertFalse(txMap1.containsKey("key1"));
        assertFalse(txMap1.containsValue("value1"));
        assertNull(txMap1.get("key1"));
        assertFalse(txMap1.containsKey("key3"));
        assertFalse(txMap1.containsValue("value3"));
        assertNull(txMap1.get("key3"));
        assertEquals(2, txMap1.size());
    }

    @Test
    public void testSets() {

        log.info("Checking set opertaions");

        final BasicTxMap<String, String> txMap1 = new BasicTxMap("txMap1");
        final Map map1 = txMap1.getWrappedMap();

        // first fill in some global values:
        txMap1.put("key1", "value1");
        txMap1.put("key2", "value200");

        // and now some inside a transaction
        txMap1.startTransaction(1, TimeUnit.HOURS);
        txMap1.put("key2", "value2"); // modify
        txMap1.put("key3", "value3");
        txMap1.put("key4", "value4");

        // check entry set
        boolean key1P, key2P, key3P, key4P;
        key1P = key2P = key3P = key4P = false;
        int cnt = 0;
        for (Iterator it = txMap1.entrySet().iterator(); it.hasNext();) {
            cnt++;
            Map.Entry entry = (Map.Entry) it.next();
            if (entry.getKey().equals("key1") && entry.getValue().equals("value1"))
                key1P = true;
            else if (entry.getKey().equals("key2") && entry.getValue().equals("value2"))
                key2P = true;
            else if (entry.getKey().equals("key3") && entry.getValue().equals("value3"))
                key3P = true;
            else if (entry.getKey().equals("key4") && entry.getValue().equals("value4"))
                key4P = true;
        }
        assertEquals(cnt, 4);
        assertTrue(key1P && key2P && key3P && key4P);

        checkCollection(txMap1.values(), new String[] { "value1", "value2", "value3", "value4" });
        checkCollection(txMap1.keySet(), new String[] { "key1", "key2", "key3", "key4" });

        txMap1.commitTransaction();

        // check again after commit (should be the same)
        key1P = key2P = key3P = key4P = false;
        cnt = 0;
        for (Iterator it = txMap1.entrySet().iterator(); it.hasNext();) {
            cnt++;
            Map.Entry entry = (Map.Entry) it.next();
            if (entry.getKey().equals("key1") && entry.getValue().equals("value1"))
                key1P = true;
            else if (entry.getKey().equals("key2") && entry.getValue().equals("value2"))
                key2P = true;
            else if (entry.getKey().equals("key3") && entry.getValue().equals("value3"))
                key3P = true;
            else if (entry.getKey().equals("key4") && entry.getValue().equals("value4"))
                key4P = true;
        }
        assertEquals(cnt, 4);
        assertTrue(key1P && key2P && key3P && key4P);

        checkCollection(txMap1.values(), new String[] { "value1", "value2", "value3", "value4" });
        checkCollection(txMap1.keySet(), new String[] { "key1", "key2", "key3", "key4" });

        // now try clean

        txMap1.startTransaction(1, TimeUnit.HOURS);

        // add
        txMap1.put("key5", "value5");
        // modify
        txMap1.put("key4", "value400");
        // delete
        txMap1.remove("key1");

        assertEquals(txMap1.size(), 4);

        txMap1.clear();
        assertEquals(txMap1.size(), 0);
        assertEquals(map1.size(), 4);

        // add
        txMap1.put("key5", "value5");
        // delete
        txMap1.remove("key1");

        // adding one, not removing anything gives size 1
        assertEquals(txMap1.size(), 1);
        assertEquals(map1.size(), 4);
        assertNull(txMap1.get("key4"));
        assertNotNull(txMap1.get("key5"));

        txMap1.commitTransaction();

        // after commit clear must have been propagated to wrapped map:
        assertEquals(txMap1.size(), 1);
        assertEquals(map1.size(), 1);
        assertNull(txMap1.get("key4"));
        assertNotNull(txMap1.get("key5"));
        assertNull(map1.get("key4"));
        assertNotNull(map1.get("key5"));
    }

    @Test
    public void testMulti() {
        log.info("Checking concurrent transaction features");

        final BasicTxMap<String, String> txMap1 = new BasicTxMap("txMap1");
        final Map map1 = txMap1.getWrappedMap();

        final RendezvousBarrier beforeCommitBarrier = new RendezvousBarrier("Before Commit", 2,
                BARRIER_TIMEOUT);

        final RendezvousBarrier afterCommitBarrier = new RendezvousBarrier("After Commit", 2,
                BARRIER_TIMEOUT);

        Thread thread1 = new Thread(new Runnable() {
            public void run() {
                txMap1.startTransaction(1, TimeUnit.HOURS);
                try {
                    beforeCommitBarrier.meet();
                    txMap1.put("key1", "value2");
                    txMap1.commitTransaction();
                    afterCommitBarrier.call();
                } catch (InterruptedException e) {
                    log.warn("Thread interrupted", e);
                    afterCommitBarrier.reset();
                    beforeCommitBarrier.reset();
                }
            }
        }, "Thread1");

        txMap1.put("key1", "value1");

        txMap1.startTransaction(1, TimeUnit.HOURS);
        thread1.start();

        report("value1", (String) txMap1.get("key1"));
        beforeCommitBarrier.call();
        try {
            afterCommitBarrier.meet();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // we have read committed as isolation level, that's why I will see the
        // new value of the other thread now
        report("value2", (String) txMap1.get("key1"));

        // now when I override it it should of course be my value again
        txMap1.put("key1", "value3");
        report("value3", (String) txMap1.get("key1"));

        // after rollback it must be the value written by the other thread again
        txMap1.rollbackTransaction();
        report("value2", (String) txMap1.get("key1"));
    }

}
