/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//transaction/src/java/org/apache/commons/transaction/util/Jdk14Logger.java,v 1.1 2004/11/18 23:27:18 ozeigermann Exp $
 * $Revision: 1.1 $
 * $Date: 2004/11/18 23:27:18 $
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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default logger implementation. Uses java.util.logging implementation provided by Java 1.4.
 *
 * @version $Revision: 1.1 $
 */
public class Jdk14Logger implements LoggerFacade {

    protected Logger logger;

    public Jdk14Logger(Logger logger) {
        this.logger = logger;
    }

    public Logger getLogger() {
        return logger;
    }

    public LoggerFacade createLogger(String name) {
        return new Jdk14Logger(Logger.getLogger(name));
    }

    public void logInfo(String message) {
        logger.info(message);
    }

    public void logFine(String message) {
        logger.fine(message);
    }

    public boolean isFineEnabled() {
        return logger.isLoggable(Level.FINE);
    }

    public void logFiner(String message) {
        logger.finer(message);
    }

    public boolean isFinerEnabled() {
        return logger.isLoggable(Level.FINER);
    }

    public void logFinest(String message) {
        logger.finest(message);
    }

    public boolean isFinestEnabled() {
        return logger.isLoggable(Level.FINEST);
    }

    public void logWarning(String message) {
        logger.warning(message);
    }

    public void logWarning(String message, Throwable t) {
        logger.log(Level.WARNING, message, t);
    }

    public void logSevere(String message) {
        logger.severe(message);
    }

    public void logSevere(String message, Throwable t) {
        logger.log(Level.SEVERE, message, t);
    }

}
