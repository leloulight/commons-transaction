/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//transaction/src/java/org/apache/commons/transaction/util/Log4jLogger.java,v 1.1 2004/11/18 23:27:18 ozeigermann Exp $
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

import org.apache.log4j.Logger;

/**
 * Default logger implementation. Uses log4j logging.
 *
 * @version $Revision: 1.1 $
 */
public class Log4jLogger implements LoggerFacade {
    
    protected Logger logger;
    
    public Log4jLogger(Logger logger) {
        this.logger = logger;
    }

    public Logger getLogger() {
        return logger;
    }

    public LoggerFacade createLogger(String name) {
        return new Log4jLogger(Logger.getLogger(name));
    }

    public void logInfo(String message) {
        logger.info(message);
    }

    public void logFine(String message) {
        logger.debug(message);
    }

    public boolean isFineEnabled() {
        return logger.isDebugEnabled();
    }

    public void logFiner(String message) {
        logger.debug(message);
    }

    public boolean isFinerEnabled() {
        return logger.isDebugEnabled();
    }

    public void logFinest(String message) {
        logger.debug(message);
    }

    public boolean isFinestEnabled() {
        return logger.isDebugEnabled();
    }

    public void logWarning(String message) {
        logger.warn(message);
    }

    public void logWarning(String message, Throwable t) {
        logger.warn(message, t);
    }
    public void logSevere(String message) {
        logger.error(message);
    }

    public void logSevere(String message, Throwable t) {
        logger.error(message, t);
    }
}
