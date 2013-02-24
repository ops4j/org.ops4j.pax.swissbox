/**
 * 
 */
package org.ops4j.pax.swissbox.framework;

import java.util.Map;

import org.osgi.framework.ServiceReference;

/**
 * represents a {@link ServiceReference} in the {@link RemoteFramework}
 */
public interface RemoteServiceReference {

    /**
     * Fetch the properties of this service reference
     * 
     * @param name
     * @return
     */
    public Map<String, Object> getProperties(String name);

    /**
     * @return a filter string that identifies this service in the remote
     *         service registry
     */
    public String getServiceFilter();
}
