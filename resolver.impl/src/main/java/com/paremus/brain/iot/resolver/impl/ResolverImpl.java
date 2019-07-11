/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.resolver.impl;

import aQute.bnd.osgi.resource.RequirementBuilder;
import eu.brain.iot.installer.api.InstallResolver;
import org.osgi.framework.BundleContext;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.resolver.Resolver;

import java.net.URI;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@Component
public class ResolverImpl implements InstallResolver {

    private BundleContext bundleContext;

    //FIXME doesn't resolve against  org.apache.felix.resolver
    @Reference //(target = "(!(" + Constants.SERVICE_BUNDLEID + "=0))")
    Resolver frameworkResolver;

    Map<Resource, Wiring> initialWiringMap = null;

    @Activate
    void activate(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
        initialWiringMap = ResolverContext.getWirings(bundleContext);
    }

    @Override
    public Map<Resource, String> resolve(String name, List<URI> indexes, Collection<Requirement> requirements) throws Exception {
        return resolve(name, indexes, requirements, null);
    }

    @Override
    public Map<Resource, String> resolveInitial(String name, List<URI> indexes, Collection<Requirement> requirements) throws Exception {
        return resolve(name, indexes, requirements, initialWiringMap);
    }

    @Override
    public Map<Resource, String> resolve(String name, List<URI> indexes, Collection<Requirement> requirements, Map<Resource, Wiring> wiringMap) throws Exception {
        ResolverContext context = new ResolverContext(bundleContext, name, indexes, requirements, wiringMap);
        Map<Resource, List<Wire>> resolved = frameworkResolver.resolve(context);

        final Map<Resource, String> result = new IdentityHashMap<>();

        for (Entry<Resource, List<Wire>> entry : resolved.entrySet()) {
            Resource resource = entry.getKey();
            // Skip the synthetic "<<INITIAL>>" resource
            if (!context.isInitialResource(resource)) {
                result.put(resource, context.getLocation(resource));
            }
        }

        return result;
    }

    @Override
    public Requirement parseRequement(String requirement) {
        // https://stackoverflow.com/questions/15738918/splitting-a-csv-file-with-quotes-as-text-delimiter-using-string-split
        String[] parts = requirement.split(";(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
        RequirementBuilder reqBuilder = null;

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty())
                continue;
            String sep = null;
            int index;

            if ((index = part.indexOf(":=")) > 0) {
                sep = ":=";
            } else if ((index = part.indexOf("=")) > 0) {
                sep = "=";
            } else if (reqBuilder == null) {
                reqBuilder = new RequirementBuilder(part.trim());
                continue;
            } else {
                throw new IllegalArgumentException("Illegal directive/attribute: <" + part + ">");
            }

            if (reqBuilder == null) {
                throw new IllegalArgumentException("No namespace in requirement: " + requirement);
            }

            String key = part.substring(0, index).trim();
            String value = part.substring(index + sep.length()).trim();

            // Remove quotes, if value is quoted.
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }

            if (sep.equals(":=")) {
                reqBuilder.addDirective(key, value);
            } else {
                try {
                    reqBuilder.addAttribute(key, value);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid requirement attribute: " + part, e);
                }
            }
        }

        return reqBuilder.buildSyntheticRequirement();
    }

}
