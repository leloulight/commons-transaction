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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.transaction.AbstractTransactionalResource;
import org.apache.commons.transaction.TransactionalResource;
import org.apache.commons.transaction.AbstractTransactionalResource.AbstractTxContext;

/**
 * Wrapper that adds transactional control to all kinds of maps that implement
 * the {@link Map} interface. This wrapper has rather weak isolation, but is
 * simply, neven blocks and commits will never fail for logical reasons. <br>
 * Start a transaction by calling {@link #startTransaction()}. Then perform the
 * normal actions on the map and finally either call
 * {@link #commitTransaction()} to make your changes permanent or
 * {@link #rollbackTransaction()} to undo them. <br>
 * <em>Caution:</em> Do not modify values retrieved by {@link #get(Object)} as
 * this will circumvent the transactional mechanism. Rather clone the value or
 * copy it in a way you see fit and store it back using
 * {@link #put(Object, Object)}. <br>
 * <em>Note:</em> This wrapper guarantees isolation level
 * <code>READ COMMITTED</code> only. I.e. as soon a value is committed in one
 * transaction it will be immediately visible in all other concurrent
 * transactions.
 * 
 * @version $Id: TransactionalMapWrapper.java 493628 2007-01-07 01:42:48Z joerg $
 * @see OptimisticMapWrapper
 * @see PessimisticMapWrapper
 */
public class TransactionalMapWrapper extends
        AbstractTransactionalResource<TransactionalMapWrapper.MapTxContext> implements Map,
        TransactionalResource {

    /** The map wrapped. */
    protected Map wrapped;

    /**
     * Creates a new transactional map wrapper. Temporary maps and sets to store
     * transactional data will be instances of {@link java.util.HashMap} and
     * {@link java.util.HashSet}.
     * 
     * @param wrapped
     *            map to be wrapped
     */
    public TransactionalMapWrapper(Map wrapped) {
        this.wrapped = Collections.synchronizedMap(wrapped);
    }

    // can be used by sub classes
    protected TransactionalMapWrapper() {
    }

    //
    // Map methods
    // 

    /**
     * @see Map#clear()
     */
    public void clear() {
        MapTxContext txContext = getActiveTx();
        if (txContext != null) {
            txContext.clear();
        } else {
            wrapped.clear();
        }
    }

    /**
     * @see Map#size()
     */
    public int size() {
        MapTxContext txContext = getActiveTx();
        if (txContext != null) {
            return txContext.size();
        } else {
            return wrapped.size();
        }
    }

    /**
     * @see Map#isEmpty()
     */
    public boolean isEmpty() {
        MapTxContext txContext = getActiveTx();
        if (txContext == null) {
            return wrapped.isEmpty();
        } else {
            return txContext.isEmpty();
        }
    }

    /**
     * @see Map#containsKey(java.lang.Object)
     */
    public boolean containsKey(Object key) {
        return keySet().contains(key);
    }

    /**
     * @see Map#containsValue(java.lang.Object)
     */
    public boolean containsValue(Object value) {
        MapTxContext txContext = getActiveTx();

        if (txContext == null) {
            return wrapped.containsValue(value);
        } else {
            return values().contains(value);
        }
    }

    /**
     * @see Map#values()
     */
    public Collection values() {

        MapTxContext txContext = getActiveTx();

        if (txContext == null) {
            return wrapped.values();
        } else {
            // XXX expensive :(
            Collection values = new ArrayList();
            for (Iterator it = keySet().iterator(); it.hasNext();) {
                Object key = it.next();
                Object value = get(key);
                // XXX we have no isolation, so get entry might have been
                // deleted in the meantime
                if (value != null) {
                    values.add(value);
                }
            }
            return values;
        }
    }

    /**
     * @see Map#putAll(java.util.Map)
     */
    public void putAll(Map map) {
        MapTxContext txContext = getActiveTx();

        if (txContext == null) {
            wrapped.putAll(map);
        } else {
            for (Iterator it = map.entrySet().iterator(); it.hasNext();) {
                Map.Entry entry = (Map.Entry) it.next();
                txContext.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * @see Map#entrySet()
     */
    public Set entrySet() {
        MapTxContext txContext = getActiveTx();
        if (txContext == null) {
            return wrapped.entrySet();
        } else {
            Set entrySet = new HashSet();
            // XXX expensive :(
            for (Iterator it = keySet().iterator(); it.hasNext();) {
                Object key = it.next();
                Object value = get(key);
                // XXX we have no isolation, so get entry might have been
                // deleted in the meantime
                if (value != null) {
                    entrySet.add(new HashEntry(key, value));
                }
            }
            return entrySet;
        }
    }

    /**
     * @see Map#keySet()
     */
    public Set keySet() {
        MapTxContext txContext = getActiveTx();

        if (txContext == null) {
            return wrapped.keySet();
        } else {
            return txContext.keys();
        }
    }

    /**
     * @see Map#get(java.lang.Object)
     */
    public Object get(Object key) {
        MapTxContext txContext = getActiveTx();

        if (txContext != null) {
            return txContext.get(key);
        } else {
            return wrapped.get(key);
        }
    }

    /**
     * @see Map#remove(java.lang.Object)
     */
    public Object remove(Object key) {
        MapTxContext txContext = getActiveTx();

        if (txContext == null) {
            return wrapped.remove(key);
        } else {
            Object oldValue = get(key);
            txContext.remove(key);
            return oldValue;
        }
    }

    /**
     * @see Map#put(java.lang.Object, java.lang.Object)
     */
    public Object put(Object key, Object value) {
        MapTxContext txContext = getActiveTx();

        if (txContext == null) {
            return wrapped.put(key, value);
        } else {
            Object oldValue = get(key);
            txContext.put(key, value);
            return oldValue;
        }

    }

    @Override
    protected MapTxContext createContext() {
        return new MapTxContext();
    }

    @Override
    protected MapTxContext getActiveTx() {
        return activeTx.get();
    }

    // mostly copied from org.apache.commons.collections.map.AbstractHashedMap
    protected static class HashEntry implements Map.Entry {
        /** The key */
        protected Object key;

        /** The value */
        protected Object value;

        protected HashEntry(Object key, Object value) {
            this.key = key;
            this.value = value;
        }

        public Object getKey() {
            return key;
        }

        public Object getValue() {
            return value;
        }

        public Object setValue(Object value) {
            Object old = this.value;
            this.value = value;
            return old;
        }

        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof Map.Entry)) {
                return false;
            }
            Map.Entry other = (Map.Entry) obj;
            return (getKey() == null ? other.getKey() == null : getKey().equals(other.getKey()))
                    && (getValue() == null ? other.getValue() == null : getValue().equals(
                            other.getValue()));
        }

        public int hashCode() {
            return (getKey() == null ? 0 : getKey().hashCode())
                    ^ (getValue() == null ? 0 : getValue().hashCode());
        }

        public String toString() {
            return new StringBuffer().append(getKey()).append('=').append(getValue()).toString();
        }
    }

    public class MapTxContext extends AbstractTxContext {
        protected Set deletes;

        protected Map changes;

        protected Map adds;

        protected boolean cleared;

        protected MapTxContext() {
            deletes = new HashSet();
            changes = new HashMap();
            adds = new HashMap();
            cleared = false;
        }

        protected Set keys() {
            Set keySet = new HashSet();
            if (!cleared) {
                keySet.addAll(wrapped.keySet());
                keySet.removeAll(deletes);
            }
            keySet.addAll(adds.keySet());
            return keySet;
        }

        protected Object get(Object key) {

            if (deletes.contains(key)) {
                // reflects that entry has been deleted in this tx
                return null;
            }

            if (changes.containsKey(key)) {
                return changes.get(key);
            }

            if (adds.containsKey(key)) {
                return adds.get(key);
            }

            if (cleared) {
                return null;
            } else {
                // not modified in this tx
                return wrapped.get(key);
            }
        }

        protected void put(Object key, Object value) {
            try {
                setReadOnly(false);
                deletes.remove(key);
                if (wrapped.containsKey(key)) {
                    changes.put(key, value);
                } else {
                    adds.put(key, value);
                }
            } catch (RuntimeException e) {
                markForRollback();
                throw e;
            } catch (Error e) {
                markForRollback();
                throw e;
            }
        }

        protected void remove(Object key) {

            try {
                setReadOnly(false);
                changes.remove(key);
                adds.remove(key);
                if (wrapped.containsKey(key) && !cleared) {
                    deletes.add(key);
                }
            } catch (RuntimeException e) {
                markForRollback();
                throw e;
            } catch (Error e) {
                markForRollback();
                throw e;
            }
        }

        protected int size() {
            int size = (cleared ? 0 : wrapped.size());

            size -= deletes.size();
            size += adds.size();

            return size;
        }

        protected void clear() {
            setReadOnly(false);
            cleared = true;
            deletes.clear();
            changes.clear();
            adds.clear();
        }

        protected boolean isEmpty() {
            return (size() == 0);
        }

        public void commit() {
            if (!isReadOnly()) {

                if (cleared) {
                    wrapped.clear();
                }

                wrapped.putAll(changes);
                wrapped.putAll(adds);

                for (Iterator it = deletes.iterator(); it.hasNext();) {
                    Object key = it.next();
                    wrapped.remove(key);
                }
            }
        }

    }

}
