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
package org.apache.commons.transaction;

import java.util.concurrent.TimeUnit;

/**
 * A managed transaction meant as interface to the user. Meant to operate on more than one resource manager.
 * This is a light weight replacement for a complex 2PC xa transaction.
 * 
 * @author olli
 *
 * 
 */
public interface Transaction {
    public void start(long timeout, TimeUnit unit);

    boolean isRollbackOnly();

    public void rollback();

    public void commit();

    void enlistResourceManager(ManageableResourceManager resourceManager);

}
