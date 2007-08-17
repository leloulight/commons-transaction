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
import org.apache.commons.transaction.locking.RWLockManager;
import org.apache.commons.transaction.util.RendezvousBarrier;

/**
 * Tests for map wrapper.
 */
public class PessimisticTxMapTest extends BasicTxMapTest {

    private static final Log log = LogFactory.getLog(PessimisticTxMapTest.class.getName());

    protected static final long TIMEOUT = Long.MAX_VALUE;

    private static int deadlockCnt = 0;

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(PessimisticTxMapTest.class);
    }

    public static void main(java.lang.String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    @Test
    public void testMulti() {
        log.info("Checking concurrent transaction features");

        final PessimisticTxMap<String, String> txMap1 = new PessimisticTxMap<String, String>(
                "txMap1");
        final Map map1 = txMap1.getWrappedMap();

        Thread thread1 = new Thread(new Runnable() {
            public void run() {
                txMap1.startTransaction(5, TimeUnit.MINUTES);
                txMap1.put("key1", "value2");
                synchronized (txMap1) {
                    txMap1.commitTransaction();
                    report("value2", (String) txMap1.get("key1"));
                }
            }
        }, "Thread1");

        txMap1.put("key1", "value1");

        txMap1.startTransaction(5, TimeUnit.MINUTES);

        report("value1", (String) txMap1.get("key1"));

        thread1.start();

        // we have serializable as isolation level, that's why I will still see
        // the old value
        report("value1", (String) txMap1.get("key1"));

        txMap1.put("key1", "value3");

        // after commit it must be our value
        synchronized (txMap1) {
            txMap1.commitTransaction();
            report("value3", (String) txMap1.get("key1"));
        }
        try {
            thread1.join();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Test
    public void testConflict() {
        log.info("Checking concurrent conflict resolvation features");

        final RWLockManager<Object, Object> lm = new RWLockManager<Object, Object>();
        // adds more concurrency and makes test run faster
        lm.setAbsolutePrewaitTime(0);
        final PessimisticTxMap<String, String> txMap1 = new PessimisticTxMap<String, String>(
                "txMap1", lm);
        final Map map1 = txMap1.getWrappedMap();

        final RendezvousBarrier restart = new RendezvousBarrier("restart", TIMEOUT);

        int conflictingRuns = 0;
        int runs = 25;

        for (int i = 0; i < runs; i++) {
            System.out.println(i);

            final RendezvousBarrier deadlockBarrier1 = new RendezvousBarrier("deadlock" + i,
                    TIMEOUT);

            Thread thread1 = new Thread(new Runnable() {
                public void run() {
                    txMap1.startTransaction(5, TimeUnit.MINUTES);
                    try {
                        // first both threads get a lock, this one on key2
                        txMap1.put("key2", "value2");
                        synchronized (deadlockBarrier1) {
                            deadlockBarrier1.meet();
                            deadlockBarrier1.reset();
                        }
                        // if I am first, the other thread will be dead, i.e.
                        // exactly one
                        txMap1.put("key1", "value2");
                        txMap1.commitTransaction();
                    } catch (LockException le) {
                        assertEquals(le.getCode(), LockException.Code.WOULD_DEADLOCK);
                        deadlockCnt++;
                        txMap1.rollbackTransaction();
                    } catch (InterruptedException ie) {
                    } finally {
                        try {
                            synchronized (restart) {
                                restart.meet();
                                restart.reset();
                            }
                        } catch (InterruptedException ie) {
                        }

                    }
                }
            }, "Thread1");

            thread1.start();

            txMap1.startTransaction(5, TimeUnit.MINUTES);
            try {
                // first both threads get a lock, this one on key1
                txMap1.get("key1");
                synchronized (deadlockBarrier1) {
                    try {
                        deadlockBarrier1.meet();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    deadlockBarrier1.reset();
                }
                // if I am first, the other thread will be dead, i.e. exactly
                // one
                txMap1.get("key2");
                txMap1.commitTransaction();
            } catch (LockException le) {
                assertEquals(le.getCode(), LockException.Code.WOULD_DEADLOCK);
                deadlockCnt++;
                txMap1.rollbackTransaction();
            } finally {
                try {
                    synchronized (restart) {
                        restart.meet();
                        restart.reset();
                    }
                } catch (InterruptedException ie) {
                }

            }

            // XXX in special scenarios the current implementation might cause
            // both owners to be deadlock victims
            if (deadlockCnt != 1) {
                // log.warn("More than one thread was deadlock victim!");
                conflictingRuns++;
            }
            assertTrue(deadlockCnt >= 1);
            deadlockCnt = 0;

            try {
                thread1.join();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
        System.out.println();
        System.out.println("Of the " + runs + " there were " + conflictingRuns
                + " runs that rolled back both transactions!");
    }

}
