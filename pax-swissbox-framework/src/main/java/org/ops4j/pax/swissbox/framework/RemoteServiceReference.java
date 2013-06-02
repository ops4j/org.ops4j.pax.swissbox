/*
 * Copyright 2013 Christoph Läubrich
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.swissbox.framework;

import java.util.Map;

import org.osgi.framework.ServiceReference;

/**
 * Represents a {@link ServiceReference} in the {@link RemoteFramework}.
 */
public interface RemoteServiceReference
{

    /**
     * Fetches the properties of this service reference.
     * @return service properties.
     */
    public Map<String, Object> getProperties();

    /**
     * Gets a filter string that identifies this service in the remote service registry.
     * @return LDAP filter string
     */
    public String getServiceFilter();
}
