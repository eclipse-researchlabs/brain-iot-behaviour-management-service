/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.resolver.impl;

import org.osgi.resource.Resource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

class CapReqBase {

    protected final String namespace;
    protected final Map<String, String> directives;
    protected final Map<String, Object> attribs;
    protected final Resource resource;

    public CapReqBase(String namespace, Map<String, String> directives, Map<String, Object> attribs, Resource resource) {
        this.namespace = namespace;
        this.directives = new HashMap<>(directives);
        this.attribs = new HashMap<>(attribs);
        this.resource = resource;
    }

    public String getNamespace() {
        return namespace;
    }

    public Map<String, String> getDirectives() {
        return Collections.unmodifiableMap(directives);
    }

    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attribs);
    }

    public Resource getResource() {
        return resource;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CapReqBase that = (CapReqBase) o;
        return namespace.equals(that.namespace) &&
                Objects.equals(directives, that.directives) &&
                Objects.equals(attribs, that.attribs) &&
                Objects.equals(resource, that.resource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, directives, attribs, resource);
    }
}
