/*
 * Copyright 2008 Alin Dreghiciu.
 *
 * Licensed  under the  Apache License,  Version 2.0  (the "License");
 * you may not use  this file  except in  compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed  under the  License is distributed on an "AS IS" BASIS,
 * WITHOUT  WARRANTIES OR CONDITIONS  OF ANY KIND, either  express  or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.pax.swissbox.property;

import org.osgi.framework.BundleContext;
import org.ops4j.lang.NullArgumentException;
import org.ops4j.util.property.FallbackPropertyResolver;
import org.ops4j.util.property.PropertyResolver;

/**
 * Resolves properties by first looking in an optional configured dictionary then if property not found looking in
 * bundle context.
 *
 * @author Alin Dreghiciu
 * @since 0.1.0, January 16, 2008
 */
public class BundleContextPropertyResolver
    extends FallbackPropertyResolver
{

    /**
     * Service bundle context.
     */
    private final BundleContext m_bundleContext;

    /**
     * Creates a property resolver without a fallback resolver.
     *
     * @param bundleContext bundle context; cannot be null
     */
    public BundleContextPropertyResolver( final BundleContext bundleContext )
    {
        this( bundleContext, null );
    }

    /**
     * Creates a property resolver with a fallback resolver.
     *
     * @param bundleContext    bundle context; cannot be null
     * @param fallbackResolver fallback property resolver; can be null
     */
    public BundleContextPropertyResolver( final BundleContext bundleContext,
                                          final PropertyResolver fallbackResolver )
    {
        super( fallbackResolver );
        NullArgumentException.validateNotNull( bundleContext, "Bundle context" );
        m_bundleContext = bundleContext;
    }

    /**
     * Resolves a property based on it's name by:<br>
     * 1. if there is a configuration available look for the property;<br>
     * 2. if property is not set or there is no configuration available look for a framework / system property.
     *
     * @param propertyName property name to be resolved
     * @return value of property or null if property is not set or is empty.
     */
    @Override
    public String findProperty( final String propertyName )
    {
        String value = m_bundleContext.getProperty( propertyName );
        if( value != null && value.trim().length() == 0 )
        {
            value = null;
        }
        return value;
    }

}
