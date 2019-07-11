/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.resolver.impl;

import org.osgi.resource.Capability;
import org.osgi.resource.Resource;

import java.util.Map;
import java.util.Map.Entry;

class CapabilityImpl extends CapReqBase implements Capability {

    public static CapabilityImpl copy(Capability cap, Resource resource) {
        return new CapabilityImpl(cap.getNamespace(), cap.getDirectives(), cap.getAttributes(), resource);
    }

    public CapabilityImpl(String namespace, Map<String, String> directives, Map<String, Object> attribs, Resource resource) {
        super(namespace, directives, attribs, resource);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.namespace);

        boolean first = true;
        for (Entry<String, String> directive : this.directives.entrySet()) {
            if (first) {
                sb.append(":");
            }
            first = false;
            sb.append(", ").append(directive.getKey()).append(":=").append(directive.getValue());
        }
        for (Entry<String, Object> attrib : this.attribs.entrySet()) {
            if (first) {
                sb.append(":");
            }
            first = false;
            sb.append(", ").append(attrib.getKey()).append("=").append(attrib.getValue());
        }

        return sb.toString();
    }

}
