/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.resolver.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.resolver.Resolver;

import aQute.bnd.header.Parameters;
import aQute.bnd.osgi.resource.CapReqBuilder;
import eu.brain.iot.installer.api.BehaviourDTO;
import eu.brain.iot.installer.api.InstallResolver;

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
        ResolverContext context = new ResolverContext(bundleContext, name, indexes, new ArrayList<>(requirements), wiringMap);
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
    	
    	Parameters p = new Parameters(requirement);
    	
    	try {
			return CapReqBuilder.getRequirementsFrom(p).get(0);
		} catch (Exception e) {
			throw new IllegalArgumentException("An error occurred parsing the requirement " + requirement, e);
		}
    }

    @Override
    public Collection<BehaviourDTO> findBehaviours(List<String> indexes, String ldapFilter) throws Exception {
        Filter filter = ldapFilter != null ? bundleContext.createFilter(ldapFilter) : null;
        return Level2Indexer.findBrainIotResources(indexes, filter);
    }
    
    

}
