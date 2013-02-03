package org.ops4j.pax.swissbox.framework;

import java.io.Serializable;
import java.util.Map;

public final class RemoteServiceReferenceImplementation implements RemoteServiceReference, Serializable {

    private static final long         serialVersionUID = -8634572885337058082L;
    private final Map<String, Object> values;
    private final String              serviceFilter;

    RemoteServiceReferenceImplementation(Map<String, Object> values, String serviceFilter) {
        this.values = values;
        this.serviceFilter = serviceFilter;
    }

    public Map<String, Object> getProperties(String name) {
        return values;
    }

    public String getServiceFilter() {
        return serviceFilter;
    }
}