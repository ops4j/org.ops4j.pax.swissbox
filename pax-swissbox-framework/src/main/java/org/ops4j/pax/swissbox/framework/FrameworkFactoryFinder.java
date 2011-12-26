/*
 * Copyright 2011 Harald Wellmann.
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

import static org.ops4j.spi.ServiceProviderFinder.findAnyServiceProvider;
import static org.ops4j.spi.ServiceProviderFinder.findServiceProviders;
import static org.ops4j.spi.ServiceProviderFinder.loadAnyServiceProvider;
import static org.ops4j.spi.ServiceProviderFinder.loadUniqueServiceProvider;

import java.util.List;

import org.osgi.framework.launch.FrameworkFactory;

/**
 * Convenience class for obtaining OSGi {@link FrameworkFactory} implementations
 * via the {@link ServiceLoader}.
 * 
 * @author Harald Wellmann
 */
public class FrameworkFactoryFinder 
{    
    public static List<FrameworkFactory> findFrameworkFactories()
    {
        return findServiceProviders( FrameworkFactory.class );
    }

    public static FrameworkFactory findAnyFrameworkFactory()
    {
        return findAnyServiceProvider( FrameworkFactory.class );
    }

    public static FrameworkFactory loadAnyFrameworkFactory()
    {
        return loadAnyServiceProvider( FrameworkFactory.class );
    }

    public static FrameworkFactory loadSingleFrameworkFactory()
    {
        return loadUniqueServiceProvider( FrameworkFactory.class );
    }
}
