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

import java.io.Serializable;
import java.util.Map;

/**
 * Default implementation of {@link RemoteServiceReference}.
 */
public final class RemoteServiceReferenceImpl implements RemoteServiceReference,
        Serializable
{

    private static final long serialVersionUID = -8634572885337058082L;
    private final Map<String, Object> values;
    private final String serviceFilter;

    RemoteServiceReferenceImpl( Map<String, Object> values, String serviceFilter )
    {
        this.values = values;
        this.serviceFilter = serviceFilter;
    }

    public Map<String, Object> getProperties()
    {
        return values;
    }

    public String getServiceFilter()
    {
        return serviceFilter;
    }
}
