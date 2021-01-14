/*******************************************************************************
 * Copyright (C) 2021 Paremus
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/

package com.paremus.brain.iot.resolver.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.osgi.framework.BundleContext;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wire;
import org.osgi.resource.Wiring;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.repository.Repository;
import org.osgi.service.resolver.Resolver;

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
    public Map<Resource, String> resolve(String name, List<? extends Repository> indexes, Collection<Requirement> requirements) throws Exception {
        return resolve(name, indexes, requirements, null);
    }

    @Override
    public Map<Resource, String> resolveInitial(String name, List<? extends Repository> indexes, Collection<Requirement> requirements) throws Exception {
        return resolve(name, indexes, requirements, initialWiringMap);
    }

    @Override
    public Map<Resource, String> resolve(String name, List<? extends Repository> repositories, Collection<Requirement> requirements, Map<Resource, Wiring> wiringMap) throws Exception {
    	ResolverContext context = new ResolverContext(bundleContext, name, repositories, 
    			new ArrayList<>(requirements), wiringMap);
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
}
