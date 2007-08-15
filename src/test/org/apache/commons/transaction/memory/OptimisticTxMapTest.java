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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import junit.framework.JUnit4TestAdapter;
import static junit.framework.Assert.*;
import org.junit.Test;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.commons.transaction.locking.LockException;
import org.apache.commons.transaction.util.RendezvousBarrier;

/**
 * Tests for optimistic tx map.
 * 
 */
public class OptimisticTxMapTest extends BasicTxMapTest {

    private static final Log log = LogFactory.getLog(OptimisticTxMapTest.class.getName());

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(OptimisticTxMapTest.class);
    }

    public static void main(java.lang.String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    @Test
    public void testMulti() {
        log.info("Checking concurrent transaction features");

        final OptimisticTxMap<String, String> txMap1 = new OptimisticTxMap<String, String>("txMap1");
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
        // we have serializable as isolation level, that's why I will still see
        // the old value
        report("value1", (String) txMap1.get("key1"));

        // now when I override it it should of course be my value
        txMap1.put("key1", "value3");
        report("value3", (String) txMap1.get("key1"));

        // after rollback it must be the value written by the other thread
        txMap1.rollbackTransaction();
        report("value2", (String) txMap1.get("key1"));
    }

    @Test
    public void testConflict() {
        log.info("Checking concurrent transaction features");

        final OptimisticTxMap<String, String> txMap1 = new OptimisticTxMap<String, String>("txMap1");
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
        // we have serializable as isolation level, that's why I will still see
        // the old value
        report("value1", (String) txMap1.get("key1"));

        // now when I override it it should of course be my value
        txMap1.put("key1", "value3");
        report("value3", (String) txMap1.get("key1"));

        boolean conflict = false;

        try {
            txMap1.commitTransaction();
        } catch (LockException ce) {
            conflict = true;
        }

        assertTrue(conflict);
        // after failed commit it must be the value written by the other thread
        report("value2", (String) map1.get("key1"));

        // force commit anyhow...
        txMap1.commitTransaction(true);
        // after successful commit it must be the value written by this thread
        report("value3", (String) txMap1.get("key1"));
        report("value3", (String) map1.get("key1"));
    }

}