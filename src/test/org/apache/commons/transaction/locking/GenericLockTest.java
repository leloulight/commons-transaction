/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//transaction/src/test/org/apache/commons/transaction/locking/GenericLockTest.java,v 1.1 2004/11/18 23:27:22 ozeigermann Exp $
 * $Revision: 1.1 $
 * $Date: 2004/11/18 23:27:22 $
 *
 * ====================================================================
 *
 * Copyright 2004 The Apache Software Foundation 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.commons.transaction.locking;

import java.io.PrintWriter;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.commons.transaction.util.LoggerFacade;
import org.apache.commons.transaction.util.PrintWriterLogger;

/**
 * Tests for generic locks. 
 *
 * @version $Revision: 1.1 $
 */
public class GenericLockTest extends TestCase {

    private static final LoggerFacade sLogger = new PrintWriterLogger(new PrintWriter(System.out),
            GenericLockTest.class.getName(), false);

    protected static final int READ_LOCK = 1;
    protected static final int WRITE_LOCK = 2;
    
    public static Test suite() {
        TestSuite suite = new TestSuite(GenericLockTest.class);
        return suite;
    }

    public static void main(java.lang.String[] args) {
        junit.textui.TestRunner.run(suite());
    }

    public GenericLockTest(String testName) {
        super(testName);
    }

    // we do not wait, as we only want the check the results and do not want real locking
    protected boolean acquireNoWait(GenericLock lock, String owner, int targetLockLevel)  {
        try {
            return lock.acquire(owner, targetLockLevel, false, true, -1);
        } catch (InterruptedException e) {
            return false;
        }
    }
    
    public void testBasic() throws Throwable {
        String owner1 = "owner1";
        String owner2 = "owner2";
        String owner3 = "owner3";
        
        // a read / write lock
        GenericLock lock = new GenericLock("Test read write lock", WRITE_LOCK, sLogger);
        
        // of course more than one can read
        boolean canRead1 = acquireNoWait(lock, owner1, READ_LOCK);
        assertTrue(canRead1);
        boolean canRead2 = acquireNoWait(lock, owner2, READ_LOCK);
        assertTrue(canRead2);
        
        // as there already are read locks, this write should not be possible
        boolean canWrite3 = acquireNoWait(lock, owner3, WRITE_LOCK);
        assertFalse(canWrite3);
        
        // release one read lock
        lock.release(owner2);
        // this should not change anything with the write as there is still one read lock left
        canWrite3 = acquireNoWait(lock, owner3, WRITE_LOCK);
        assertFalse(canWrite3);

        // release the other and final read lock as well
        lock.release(owner1);
        // no we should be able to get write access
        canWrite3 = acquireNoWait(lock, owner3, WRITE_LOCK);
        assertTrue(canWrite3);
        // but of course no more read access
        canRead2 = acquireNoWait(lock, owner2, READ_LOCK);
        assertFalse(canRead2);
        
        // relase the write lock and make sure we can read again
        lock.release(owner3);
        canRead2 = acquireNoWait(lock, owner2, READ_LOCK);
        assertTrue(canRead2);
        
        // now we do something weired, we try to block all locks lower than write...
        boolean canBlock3 = lock.acquire(owner3, WRITE_LOCK, false, GenericLock.COMPATIBILITY_SUPPORT, -1);
        // which of course does not work, as there already is an incompatible read lock
        assertFalse(canBlock3);
        
        // ok, release read lock (no we have no more locks) and try again
        lock.release(owner2);
        canBlock3 = lock.acquire(owner3, WRITE_LOCK, false, GenericLock.COMPATIBILITY_SUPPORT, -1);
        // which now should work creating an ordinary lock
        assertTrue(canBlock3);
        
        // as this just an ordinary lock, we should not get a read lock:
        canRead1 = acquireNoWait(lock, owner1, READ_LOCK);
        assertFalse(canRead1);
        
        // this is the trick now, we *can* get an addtional write lock with this request as it has
        // the same level as the write lock already set. This works, as we do not care for the
        // write lock level, but only want to inhibit the read lock:
        boolean canBlock2 = lock.acquire(owner2, WRITE_LOCK, false, GenericLock.COMPATIBILITY_SUPPORT, -1);
        assertTrue(canBlock2);
        
        // now if we release one of the blocks supporting each other we still should not get a
        // read lock
        lock.release(owner3);
        canRead1 = acquireNoWait(lock, owner1, READ_LOCK);
        assertFalse(canRead1);

        // but of course after we release the second as well
        lock.release(owner2);
        canRead1 = acquireNoWait(lock, owner1, READ_LOCK);
        assertTrue(canRead1);
    }
}
