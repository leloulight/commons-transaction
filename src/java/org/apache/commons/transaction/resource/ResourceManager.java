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
package org.apache.commons.transaction.resource;

/**
 * Interface for a manager on resources. All meaningful work is done using the
 * interface for the resource.
 */
public interface ResourceManager<R> {
    /**
     * Gets the resource denoted by the path
     * 
     * @param path the path of the resource
     * @return the resource denoted by the path
     * @throws ResourceException in case anything goes fatally wrong
     */
    R getResource(String path) throws ResourceException;

    /**
     * Gets the root path of this manager.
     * 
     * @return the root path or <code>null</code> if no applicable
     */
    String getRootPath();
}