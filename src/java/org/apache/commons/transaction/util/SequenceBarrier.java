/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//transaction/src/java/org/apache/commons/transaction/util/Attic/SequenceBarrier.java,v 1.1 2004/12/28 21:11:12 ozeigermann Exp $
 * $Revision: 1.1 $
 * $Date: 2004/12/28 21:11:12 $
 *
 * ====================================================================
 *
 * Copyright 1999-2002 The Apache Software Foundation 
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

package org.apache.commons.transaction.util;

/**
 * Simple counter barrier to make a sequence of calls from different threads deterministic.
 * This is very useful for testing where you want to have a contious flow throughout 
 * different threads. The idea is to have an ordered sequence of steps where step n can not be 
 * executed before n-1.
 * 
 * @version $Revision: 1.1 $
 */
public class SequenceBarrier {

    public static final int DEFAULT_TIMEOUT = 20000;

    protected final String name;

    protected int currentNumber;

    protected long timeout;

    protected LoggerFacade logger;

    public SequenceBarrier(String name, long timeout, LoggerFacade logger) {
        this.name = name;
        this.timeout = timeout;
        this.logger = logger;
        currentNumber = 0;
    }

    /**
     * Compares the number to the the internal counter. If it is greater than the counter
     * it's not our turn and we wait. If it is eqaul we increment the internal counter and let
     * others check if it is their turn now. 
     */
    public synchronized void count(int number) throws InterruptedException {
        if (number > currentNumber) {
            long started = System.currentTimeMillis();
            for (long remaining = timeout; remaining > 0 && number > currentNumber; remaining = timeout
                    - (System.currentTimeMillis() - started)) {
                wait(remaining);
            }
        }
        if (number == currentNumber) {
            currentNumber++;
            notifyAll();
        }
    }

    public synchronized void reset() {
        currentNumber = 0;
        notifyAll();
    }
}