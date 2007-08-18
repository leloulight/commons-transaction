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

import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

import junit.framework.JUnit4TestAdapter;
import static junit.framework.Assert.*;
import org.junit.Test;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.file.TxFileResourceManagerTest;
import org.apache.commons.transaction.util.RendezvousBarrier;
import org.apache.commons.transaction.util.TurnBarrier;

/**
 * Tests for locking.
 * 
 */
public class LockTest {

    private Log log = LogFactory.getLog(getClass());

    private static final int CONCURRENT_TESTS = 25;

    protected static final long TIMEOUT = 1000000;

    private static int deadlockCnt = 0;

    private static String first = null;

    private static String defaultResource = "resource";

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(TxFileResourceManagerTest.class);
    }

    public static void main(java.lang.String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    @Test
    public void deadlock() throws Throwable {

        log.info("\n\nChecking deadlock detection\n\n");

        final String res1 = "res1";
        final String res2 = "res2";

        final RWLockManager<Object, Object> manager = new RWLockManager<Object, Object>();

        final RendezvousBarrier restart = new RendezvousBarrier("restart", TIMEOUT);

        for (int i = 0; i < CONCURRENT_TESTS; i++) {

            System.out.print(".");

            final RendezvousBarrier deadlockBarrier1 = new RendezvousBarrier("deadlock1" + i,
                    TIMEOUT);

            Thread deadlock = new Thread(new Runnable() {
                public void run() {
                    try {
                        manager.startWork(10, TimeUnit.SECONDS);
                        // first both threads get a lock, this one on res2
                        manager.lock(defaultResource, res2, true);
                        synchronized (deadlockBarrier1) {
                            deadlockBarrier1.meet();
                            deadlockBarrier1.reset();
                        }
                        // if I am first, the other thread will be dead, i.e.
                        // exactly one
                        manager.lock(defaultResource, res1, true);
                    } catch (LockException le) {
                        assertEquals(le.getCode(), LockException.Code.WOULD_DEADLOCK);
                        deadlockCnt++;
                    } catch (InterruptedException ie) {
                    } finally {
                        manager.endWork();
                        try {
                            synchronized (restart) {
                                restart.meet();
                                restart.reset();
                            }
                        } catch (InterruptedException ie) {
                        }
                    }
                }
            }, "Deadlock Thread");

            deadlock.start();

            try {
                manager.startWork(10, TimeUnit.SECONDS);
                // first both threads get a lock, this one on res2
                manager.lock(defaultResource, res1, false);
                synchronized (deadlockBarrier1) {
                    deadlockBarrier1.meet();
                    deadlockBarrier1.reset();
                }
                // if I am first, the other thread will be dead, i.e. exactly
                // one
                manager.lock(defaultResource, res2, true);
            } catch (LockException le) {
                assertEquals(le.getCode(), LockException.Code.WOULD_DEADLOCK);
                deadlockCnt++;
            } finally {
                manager.endWork();
                synchronized (restart) {
                    restart.meet();
                    restart.reset();
                }
            }

            // XXX in special scenarios the current implementation might cause
            // both
            // owners to be deadlock victims
            if (deadlockCnt != 1) {
                log.warn("More than one thread was deadlock victim!");
            }
            assertTrue(deadlockCnt >= 1);
            deadlockCnt = 0;
        }
    }

    /*
     * 
     * Test detection of an indirect deadlock:
     * 
     * Owner Owner Owner Step #1 #2 #3 1 read res1 (ok) 2 read res2 (ok) 3 read
     * res3 (ok) 4 write res2 (blocked because of #2) 5 write res1 (blocked
     * because of #1) 6 write res3 (blocked because #3) - Thread#1 waits for
     * Thread#3 on res3 - Thread#2 waits for Thread#1 on res1 - Thread#3 waits
     * for Thread#2 on res2
     * 
     * This needs recursion of the deadlock detection algorithm
     * 
     */
    @Test
    public void indirectDeadlock() throws Throwable {

        log.info("\n\nChecking detection of indirect deadlock \n\n");

        final String res1 = "res1";
        final String res2 = "res2";
        final String res3 = "res3";

        final RWLockManager<Object, Object> manager = new RWLockManager<Object, Object>();

        final RendezvousBarrier restart = new RendezvousBarrier("restart", 5, TIMEOUT);

        final TurnBarrier cb = new TurnBarrier("cb1", TIMEOUT, 1);

        for (int i = 0; i < CONCURRENT_TESTS; i++) {

            System.out.print(".");

            // thread that accesses lock of res1 just to cause interference and
            // possibly detect concurrency problems
            Thread jamThread1 = new Thread(new Runnable() {
                public void run() {
                    try {
                        for (int i = 0; i < 10; i++) {
                            manager.startWork(10, TimeUnit.SECONDS);
                            manager.lock(defaultResource, res1, false);
                            Thread.sleep(10);
                            manager.endWork();
                            Thread.sleep(10);
                            manager.startWork(10, TimeUnit.SECONDS);
                            manager.lock(defaultResource, res1, true);
                            Thread.sleep(10);
                            manager.endWork();
                            Thread.sleep(10);
                        }
                    } catch (LockException le) {
                        fail("Jam Thread should not fail");
                    } catch (InterruptedException ie) {
                    } finally {
                        manager.endWork();
                        synchronized (restart) {
                            try {
                                synchronized (restart) {
                                    restart.meet();
                                    restart.reset();
                                }
                            } catch (InterruptedException ie) {
                            }
                        }
                    }
                }
            }, "Jam Thread #1");

            jamThread1.start();

            // thread that accesses lock of res1 just to cause interference and
            // possibly detect concurrency problems
            Thread jamThread2 = new Thread(new Runnable() {
                public void run() {
                    try {
                        for (int i = 0; i < 10; i++) {
                            manager.startWork(10, TimeUnit.SECONDS);
                            manager.lock(defaultResource, res1, true);
                            Thread.sleep(10);
                            manager.endWork();
                            manager.startWork(10, TimeUnit.SECONDS);
                            Thread.sleep(10);
                            manager.lock(defaultResource, res1, false);
                            Thread.sleep(10);
                            manager.endWork();
                            Thread.sleep(10);
                        }
                    } catch (LockException le) {
                        fail("Jam Thread should not fail");
                    } catch (InterruptedException ie) {
                    } finally {
                        manager.endWork();
                        synchronized (restart) {
                            try {
                                synchronized (restart) {
                                    restart.meet();
                                    restart.reset();
                                }
                            } catch (InterruptedException ie) {
                            }
                        }
                    }
                }
            }, "Jam Thread #2");

            jamThread2.start();

            Thread t1 = new Thread(new Runnable() {
                public void run() {
                    try {
                        manager.startWork(10, TimeUnit.SECONDS);
                        cb.waitForTurn(2);
                        manager.lock(defaultResource, res2, false);
                        cb.signalTurn(3);
                        cb.waitForTurn(5);
                        synchronized (cb) {
                            cb.signalTurn(6);
                            manager.lock(defaultResource, res1, true);
                        }
                    } catch (LockException le) {
                        assertEquals(le.getCode(), LockException.Code.WOULD_DEADLOCK);
                        deadlockCnt++;
                    } catch (InterruptedException ie) {
                    } finally {
                        manager.endWork();
                        synchronized (restart) {
                            try {
                                synchronized (restart) {
                                    restart.meet();
                                    restart.reset();
                                }
                            } catch (InterruptedException ie) {
                            }
                        }
                    }
                }
            }, "Thread #1");

            t1.start();

            Thread t2 = new Thread(new Runnable() {
                public void run() {
                    try {
                        manager.startWork(10, TimeUnit.SECONDS);
                        cb.waitForTurn(3);
                        manager.lock(defaultResource, res3, false);
                        synchronized (cb) {
                            cb.signalTurn(5);
                            manager.lock(defaultResource, res2, true);
                        }
                    } catch (LockException le) {
                        assertEquals(le.getCode(), LockException.Code.WOULD_DEADLOCK);
                        deadlockCnt++;
                    } catch (InterruptedException ie) {
                    } finally {
                        manager.endWork();
                        synchronized (restart) {
                            try {
                                synchronized (restart) {
                                    restart.meet();
                                    restart.reset();
                                }
                            } catch (InterruptedException ie) {
                            }
                        }
                    }
                }
            }, "Thread #2");

            t2.start();

            try {
                manager.startWork(10, TimeUnit.SECONDS);
                cb.waitForTurn(1);
                manager.lock(defaultResource, res1, false);
                cb.signalTurn(2);
                cb.waitForTurn(6);
                manager.lock(defaultResource, res3, true);
            } catch (LockException le) {
                assertEquals(le.getCode(), LockException.Code.WOULD_DEADLOCK);
                deadlockCnt++;
            } catch (InterruptedException ie) {
            } finally {
                manager.endWork();
                synchronized (restart) {
                    try {
                        synchronized (restart) {
                            restart.meet();
                            restart.reset();
                        }
                    } catch (InterruptedException ie) {
                    }
                }
            }

            // XXX in special scenarios the current implementation might cause
            // more than one
            // owner to be a deadlock victim
            if (deadlockCnt != 1) {
                log.warn("\nMore than one thread was deadlock victim!\n");
            }
            assertTrue(deadlockCnt >= 1);
            deadlockCnt = 0;
            cb.reset();
        }
    }

    @Test
    public void globalTimeout() throws Throwable {

        log.info("\n\nChecking global timeouts\n\n");

        final String owner1 = "owner1";
        final String owner2 = "owner2";

        final String res1 = "res1";

        final RWLockManager<Object, Object> manager = new RWLockManager<Object, Object>();

        final RendezvousBarrier restart = new RendezvousBarrier("restart", 2, TIMEOUT);

        final TurnBarrier cb = new TurnBarrier("cb1", TIMEOUT, 1);

        for (int i = 0; i < CONCURRENT_TESTS; i++) {

            System.out.print(".");

            Thread t1 = new Thread(new Runnable() {
                public void run() {
                    try {
                        manager.startWork(10, TimeUnit.SECONDS);
                        cb.waitForTurn(2);
                        manager.lock(defaultResource, res1, false);
                        cb.signalTurn(3);
                        manager.endWork();
                        synchronized (restart) {
                            restart.meet();
                            restart.reset();
                        }
                    } catch (InterruptedException ie) {
                    }
                }
            }, "Thread #1");

            t1.start();

            cb.waitForTurn(1);
            manager.startWork(10, TimeUnit.SECONDS);
            manager.lock(defaultResource, res1, false);
            cb.signalTurn(2);
            cb.waitForTurn(3);
            boolean failed = false;
            try {
                manager.tryLock(defaultResource, res1, false);
            } catch (LockException le) {
                failed = true;
            }
            assertTrue(failed);
            manager.endWork();
            failed = false;
            manager.startWork(10, TimeUnit.SECONDS);
            try {
                manager.tryLock(defaultResource, res1, false);
            } catch (LockException le) {
                failed = true;
            }
            assertFalse(failed);
            manager.endWork();
            synchronized (restart) {
                restart.meet();
                restart.reset();
            }

            cb.reset();
        }

    }

    @Test
    public void stress() throws Throwable {

        log.info("\n\nStress checking locks\n\n");

        final String res1 = "res1";
        final String res2 = "res2";
        final String res3 = "res3";

        final RWLockManager<Object, Object> manager = new RWLockManager<Object, Object>();

        final RendezvousBarrier restart = new RendezvousBarrier("restart", 5, TIMEOUT);
        final RendezvousBarrier start = new RendezvousBarrier("start", 5, TIMEOUT);

        for (int i = 0; i < CONCURRENT_TESTS; i++) {

            System.out.print(".");

            Thread t1 = new Thread(new Runnable() {
                public void run() {
                    try {
                        try {
                            synchronized (start) {
                                start.meet();
                                start.reset();
                            }
                            manager.startWork(10, TimeUnit.SECONDS);
                            manager.lock(defaultResource, res1, false);
                            manager.lock(defaultResource, res2, false);
                            manager.lock(defaultResource, res3, true);
                        } catch (LockException ie) {
                        } finally {
                            manager.endWork();
                            synchronized (restart) {
                                restart.meet();
                                restart.reset();
                            }
                        }
                    } catch (InterruptedException ie) {
                    }
                }
            }, "Thread #1");
            t1.start();

            Thread t2 = new Thread(new Runnable() {
                public void run() {
                    try {
                        try {
                            synchronized (start) {
                                start.meet();
                                start.reset();
                            }
                            manager.startWork(10, TimeUnit.SECONDS);
                            manager.lock(defaultResource, res1, false);
                            manager.lock(defaultResource, res2, false);
                            manager.lock(defaultResource, res3, true);
                        } catch (LockException ie) {
                        } finally {
                            manager.endWork();
                            synchronized (restart) {
                                restart.meet();
                                restart.reset();
                            }
                        }
                    } catch (InterruptedException ie) {
                    }
                }
            }, "Thread #2");
            t2.start();

            Thread t3 = new Thread(new Runnable() {
                public void run() {
                    try {
                        try {
                            synchronized (start) {
                                start.meet();
                                start.reset();
                            }
                            manager.startWork(10, TimeUnit.SECONDS);
                            manager.lock(defaultResource, res1, false);
                            manager.lock(defaultResource, res2, false);
                            manager.lock(defaultResource, res3, true);
                        } catch (LockException ie) {
                        } finally {
                            manager.endWork();
                            synchronized (restart) {
                                restart.meet();
                                restart.reset();
                            }
                        }
                    } catch (InterruptedException ie) {
                    }
                }
            }, "Thread #3");
            t3.start();

            Thread t4 = new Thread(new Runnable() {
                public void run() {
                    try {
                        try {
                            synchronized (start) {
                                start.meet();
                                start.reset();
                            }
                            manager.startWork(10, TimeUnit.SECONDS);
                            manager.lock(defaultResource, res1, false);
                            manager.lock(defaultResource, res2, false);
                            manager.lock(defaultResource, res3, true);
                        } catch (LockException ie) {
                        } finally {
                            manager.endWork();
                            synchronized (restart) {
                                restart.meet();
                                restart.reset();
                            }
                        }
                    } catch (InterruptedException ie) {
                    }
                }
            }, "Thread #4");
            t4.start();

            try {
                try {
                    synchronized (start) {
                        start.meet();
                        start.reset();
                    }
                    manager.startWork(10, TimeUnit.SECONDS);
                    manager.lock(defaultResource, res1, false);
                    manager.lock(defaultResource, res2, false);
                    manager.lock(defaultResource, res3, false);
                } catch (LockException ie) {
                } finally {
                    manager.endWork();
                    try {
                        synchronized (restart) {
                            restart.meet();
                            restart.reset();
                        }
                    } catch (InterruptedException ie) {
                    }
                }
            } catch (InterruptedException ie) {
            }
        }

    }

    @Test
    public void choas() throws Throwable {

        log.info("\n\nChaos testing locks for internal deadlocks resp. concurrent mods\n\n");

        final String res1 = "res1";
        final String res2 = "res2";
        final String res3 = "res3";

        final RWLockManager<Object, Object> manager = new RWLockManager<Object, Object>();

        int concurrentThreads = 7;
        int threads = CONCURRENT_TESTS * concurrentThreads;

        final RendezvousBarrier end = new RendezvousBarrier("end", threads + 1, TIMEOUT);

        log.info("\n\nStarting " + threads + " threads\n\n");

        for (int i = 0; i < CONCURRENT_TESTS; i++) {

            final int cnt = i;

            System.out.print(".");

            Thread t1 = new Thread(new Runnable() {
                public void run() {
                    try {
                        manager.startWork(10, TimeUnit.SECONDS);
                        manager.lock(defaultResource, res1, false);
                        manager.lock(defaultResource, res2, false);
                        manager.lock(defaultResource, res3, true);
                    } catch (LockException ie) {
                        System.out.print("-");
                    } finally {
                        manager.endWork();
                        end.call();
                    }
                }
            }, "Thread #1");

            Thread t2 = new Thread(new Runnable() {
                public void run() {
                    try {
                        manager.startWork(10, TimeUnit.SECONDS);
                        manager.lock(defaultResource, res1, false);
                        manager.lock(defaultResource, res2, false);
                        manager.lock(defaultResource, res3, true);
                    } catch (LockException ie) {
                        System.out.print("-");
                    } finally {
                        manager.endWork();
                        end.call();
                    }
                }
            }, "Thread #2");

            Thread t3 = new Thread(new Runnable() {
                public void run() {
                    try {
                        manager.startWork(10 + cnt, TimeUnit.SECONDS);
                        manager.lock(defaultResource, res1, false);
                        manager.lock(defaultResource, res2, false);
                        manager.lock(defaultResource, res3, true);
                    } catch (LockException le) {
                        if (le.getCode() == LockException.Code.TIMED_OUT) {
                            System.out.print("*");
                        } else {
                            System.out.print("-");
                        }
                    } finally {
                        manager.endWork();
                        end.call();
                    }
                }
            }, "Thread #3");

            Thread t4 = new Thread(new Runnable() {
                public void run() {
                    try {
                        manager.startWork(10, TimeUnit.SECONDS);
                        manager.lock(defaultResource, res1, false);
                        manager.lock(defaultResource, res2, false);
                        manager.lock(defaultResource, res3, true);
                    } catch (LockException le) {
                        System.out.print("-");
                    } finally {
                        manager.endWork();
                        end.call();
                    }
                }
            }, "Thread #4");

            Thread deadlock1 = new Thread(new Runnable() {
                public void run() {
                    try {
                        manager.startWork(10, TimeUnit.SECONDS);
                        manager.lock(defaultResource, res2, true);
                        manager.lock(defaultResource, res1, true);
                    } catch (LockException le) {
                        assertEquals(le.getCode(), LockException.Code.WOULD_DEADLOCK);
                        System.out.print("-");
                    } finally {
                        manager.endWork();
                        end.call();
                    }
                }
            }, "Deadlock1 Thread");

            Thread deadlock2 = new Thread(new Runnable() {
                public void run() {
                    try {
                        manager.startWork(10, TimeUnit.SECONDS);
                        manager.lock(defaultResource, res1, false);
                        manager.lock(defaultResource, res2, false);
                    } catch (LockException le) {
                        assertEquals(le.getCode(), LockException.Code.WOULD_DEADLOCK);
                        System.out.print("-");
                    } finally {
                        manager.endWork();
                        end.call();
                    }
                }
            }, "Deadlock1 Thread");

            Thread reader = new Thread(new Runnable() {
                public void run() {
                    try {
                        manager.lock(defaultResource, res1, false);
                        manager.lock(defaultResource, res2, false);
                        manager.lock(defaultResource, res3, false);
                    } catch (LockException ie) {
                        System.out.print("-");
                    } finally {
                        manager.endWork();
                        end.call();
                    }
                }
            }, "Reader Thread");

            t4.start();
            t3.start();
            reader.start();
            t1.start();
            deadlock2.start();
            t2.start();
            deadlock1.start();
        }
        // wait until all threads have really terminated
        end.meet();

    }
}
