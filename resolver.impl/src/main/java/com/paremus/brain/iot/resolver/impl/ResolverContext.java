/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.resolver.impl;

import static org.osgi.framework.namespace.IdentityNamespace.IDENTITY_NAMESPACE;
import static org.osgi.service.repository.ContentNamespace.CAPABILITY_MIME_ATTRIBUTE;
import static org.osgi.service.repository.ContentNamespace.CAPABILITY_URL_ATTRIBUTE;
import static org.osgi.service.repository.ContentNamespace.CONTENT_NAMESPACE;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.namespace.IdentityNamespace;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wiring;
import org.osgi.service.repository.Repository;
import org.osgi.service.resolver.HostedCapability;
import org.osgi.service.resolver.ResolveContext;

import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.osgi.resource.ResourceBuilder;

public class ResolverContext extends ResolveContext {

    public static final String MIME_BUNDLE = "application/vnd.osgi.bundle";
    public static final String MIME_INDEX = "application/vnd.osgi.repository+xml";

    private static final String INITIAL_RESOURCE_CAPABILITY_NAMESPACE = "__initial";

    private final BundleContext bundleContext;

    // The repositories that will be queries for providers
    private final List<? extends Repository> repositories;

    // A cache of resource->location (URL), generated during resolve and queried
    // after resolve in order to fetch the resource.
    private final Map<Resource, String> resourceLocationMap = new IdentityHashMap<>();

    // A cache of resources to the repositories which own them; used from
    // insertHostedCapability method.
    private final Map<Resource, Repository> resourceRepositoryMap = new IdentityHashMap<>();

    private final Resource initialResource;

    private final Map<Resource, Wiring> wiringMap;

    ResolverContext(BundleContext bundleContext, String name, List<? extends Repository> repositories, 
    		List<Requirement> requirements, Map<Resource, Wiring> wiringMap) throws Exception {
        this.bundleContext = bundleContext;
        this.repositories = repositories;
        this.wiringMap = (wiringMap != null) ? wiringMap : getWirings(bundleContext);

        ResourceBuilder rb = new ResourceBuilder();
        
        rb.addCapability(new CapReqBuilder(IDENTITY_NAMESPACE)
        		.addAttribute(IDENTITY_NAMESPACE, name));
        rb.addCapability(new CapReqBuilder(INITIAL_RESOURCE_CAPABILITY_NAMESPACE));
        rb.addRequirements(requirements);
        		
        this.initialResource = rb.build();
    }

    @Override
    public Collection<Resource> getMandatoryResources() {
        return Collections.<Resource>singleton(this.initialResource);
    }

    @Override
    public List<Capability> findProviders(Requirement requirement) {
        List<Capability> resultCaps = new LinkedList<>();

        // Find from installed bundles
        Bundle[] bundles = this.bundleContext.getBundles();
        for (Bundle bundle : bundles) {
            if (bundle.getState() == Bundle.UNINSTALLED) {
                continue; // Skip UNINSTALLED bundles
            }

            BundleRevision revision = bundle.adapt(BundleRevision.class);
            List<Capability> bundleCaps = revision.getCapabilities(requirement.getNamespace());
            if (bundleCaps != null) {
                for (Capability bundleCap : bundleCaps) {
                    if (match(requirement, bundleCap)) {
                        resultCaps.add(bundleCap);
                    }
                }
            }
        }

        // Find from repositories
        for (Repository repository : this.repositories) {
            Map<Requirement, Collection<Capability>> providers = repository
                    .findProviders(Collections.singleton(requirement));
            Collection<Capability> repoCaps = providers.get(requirement);
                resultCaps.addAll(repoCaps);

            for (Capability repoCap : repoCaps) {
                // Get the list of physical URIs for this resource.
                Resource resource = repoCap.getResource();
                // Keep track of which repositories own which resources.
                this.resourceRepositoryMap.put(resource, repository);

                // Save the Resource's URI for later.
                URI resolvedUri = resolveResourceLocation(resource);
                if (resolvedUri != null) {
                    // Cache the resolved URI into the resource URI map,
                    // which will be used after resolve.
                    this.resourceLocationMap.put(resource, resolvedUri.toString());
                }
            }
        }
        return resultCaps;
    }

    static boolean match(Requirement requirement, Capability capability) {
        // Namespace MUST match
        if (!requirement.getNamespace().equals(capability.getNamespace())) {
            return false;
        }

        // If capability effective!=resolve then it matches only requirements
        // with same effective
        String capabilityEffective = capability.getDirectives().get(Namespace.CAPABILITY_EFFECTIVE_DIRECTIVE);
        if (capabilityEffective != null) {
            String requirementEffective = requirement.getDirectives().get(Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE);
            if (!capabilityEffective.equals(Namespace.EFFECTIVE_RESOLVE)
                    && !capabilityEffective.equals(requirementEffective)) {
                return false;
            }
        }

        String filterStr = requirement.getDirectives().get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
        if (filterStr == null) {
            return true; // no filter, the requirement always matches
        }

        try {
            Filter filter = FrameworkUtil.createFilter(filterStr);
            return filter.matches(capability.getAttributes());
        } catch (InvalidSyntaxException e) {
            Resource resource = requirement.getResource();
            String id = resource != null ? getIdentity(resource) : "<unknown>";
            System.err.printf("Invalid filter syntax in requirement from resource %s: %s -- %s", id, filterStr, e);
        }
        return false;
    }


    /**
     * Get the repository index for the specified resource, where 0 indicates an
     * existing OSGi bundle in the framework and -1 indicates not found. This
     * method is used by
     * {@link #insertHostedCapability(List, HostedCapability)}.
     */
    private int findResourceRepositoryIndex(Resource resource) {
        if (resource instanceof BundleRevision) {
            return 0;
        }

        int index = 1;
        Repository repo = this.resourceRepositoryMap.get(resource);
        if (repo == null) {
            return -1;
        }
        for (Repository match : this.repositories) {
            if (repo == match) {
                return index;
            }
            index++;
        }
        // Still not found
        return -1;
    }

    @Override
    public int insertHostedCapability(List<Capability> capabilities, HostedCapability hc) {
        int hostIndex = findResourceRepositoryIndex(hc.getResource());
        if (hostIndex == -1) {
            throw new IllegalArgumentException(
                    "Hosted capability has host resource not found in any known repository.");
        }

        for (int pos = 0; pos < capabilities.size(); pos++) {
            int resourceIndex = findResourceRepositoryIndex(capabilities.get(pos).getResource());
            if (resourceIndex > hostIndex) {
                capabilities.add(pos, hc);
                return pos;
            }
        }

        // The list passed by (some versions of) Felix does not support the
        // single-arg add() method... this throws UnsupportedOperationException.
        // So we have to call the two-arg add() with an explicit index.
        int lastPos = capabilities.size();
        capabilities.add(lastPos, hc);
        return lastPos;
    }

    @Override
    public boolean isEffective(Requirement requirement) {
        return true;
    }

    @Override
    public Map<Resource, Wiring> getWirings() {
        return wiringMap;
    }

    public static Map<Resource, Wiring> getWirings(BundleContext context) {
        Map<Resource, Wiring> wiringMap = new HashMap<>();
        Bundle[] bundles = context.getBundles();

        for (Bundle bundle : bundles) {
            // BundleRevision extends Resource
            BundleRevision revision = bundle.adapt(BundleRevision.class);
            // BundleWiring extends Wiring
            BundleWiring wiring = revision.getWiring();
            if (wiring != null) {
                wiringMap.put(revision, wiring);
            }
        }
        return wiringMap;
    }

    boolean isInitialResource(Resource resource) {
        List<Capability> markerCaps = resource.getCapabilities(INITIAL_RESOURCE_CAPABILITY_NAMESPACE);
        return markerCaps != null && !markerCaps.isEmpty();
    }

    String getLocation(Resource resource) {
        String location;
        if (resource instanceof BundleRevision) {
            location = ((BundleRevision) resource).getBundle().getLocation();
        } else {
            location = this.resourceLocationMap.get(resource);
        }
        return location;
    }

    private static URI resolveResourceLocation(Resource resource) {
        List<Capability> contentCaps = resource.getCapabilities(CONTENT_NAMESPACE);
        for (Capability contentCap : contentCaps) {
            // Ensure this content entry has the correct MIME type for a bundle
            if (MIME_BUNDLE.equals(contentCap.getAttributes().get(CAPABILITY_MIME_ATTRIBUTE))) {
                // Get the URI attribute in the index, if this was relative in the file then
            	// it will have been made absolute by the Repository implementation for us
                Object uriObj = contentCap.getAttributes().get(CAPABILITY_URL_ATTRIBUTE);

                if (uriObj instanceof URI) {
                    return (URI) uriObj;
                } else if (uriObj instanceof String) {
                    return URI.create((String) uriObj);
                }
            }
        }

        // No content capability was found in the appropriate form
        return null;
    }

    private static String getIdentity(Resource resource) {
        List<Capability> caps = resource.getCapabilities(IdentityNamespace.IDENTITY_NAMESPACE);
        if (caps == null || caps.isEmpty()) {
            return "<unknown>";
        }

        Object idObj = caps.get(0).getAttributes().get(IdentityNamespace.IDENTITY_NAMESPACE);
        if (!(idObj instanceof String)) {
            return "<unknown>";
        }

        return (String) idObj;
    }

}
