/*
 * Created on 17.05.2004
 *
 * To change this generated comment go to 
 * Window>Preferences>Java>Code Generation>Code Template
 */
package org.apache.commons.transaction.memory.jca;

import javax.resource.ResourceException;

import org.apache.commons.transaction.memory.TransactionalMapWrapper;

/**
 * 
 * @version $Revision: 1.1 $
 * 
 */
public class MapLocalTransaction implements javax.resource.spi.LocalTransaction, javax.resource.cci.LocalTransaction {

    TransactionalMapWrapper map;

    public MapLocalTransaction(TransactionalMapWrapper map) {
        this.map = map;
    }

    public void begin() throws ResourceException {
        map.startTransaction();

    }

    public void commit() throws ResourceException {
        map.commitTransaction();
    }

    public void rollback() throws ResourceException {
        map.rollbackTransaction();
    }

}
