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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import junit.framework.JUnit4TestAdapter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.transaction.util.RendezvousBarrier;
import org.junit.Test;

/**
 * Tests for locking.
 * 
 */
public class LockTest {

    private Log log = LogFactory.getLog(getClass());

    private static final int CONCURRENT_TESTS = 25;

    protected static final long TIMEOUT = 1000000;

    private static int deadlockCnt = 0;

    private static String defaultResource = "resource";

    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(LockTest.class);
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
                        manager.startWork(10, TimeUnit.SECONDS);
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
