/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package eu.brain.iot.installer.api;

import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wiring;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface InstallResolver {

	/**
	 * Resolve the requirements using the specified indexes, 
	 * assuming a blank framework
	 * 
	 * @param name the "name" of the resolution
	 * @param indexes the indexes to resolve against
	 * @param requirements the requirements to resolve
	 * @return The resources in the resolution, mapped to the URL they are from /to install them from 
	 * @throws Exception
	 */
    Map<Resource, String> resolve(String name, List<URI> indexes, Collection<Requirement> requirements) throws Exception;

    /**
     * Resolve the requirements using the specified indexes, 
     * including existing installed bundles in the framework
     * 
     * @param name the "name" of the resolution
     * @param indexes the indexes to resolve against
     * @param requirements the requirements to resolve
     * @return The resources in the resolution, mapped to the URL they are from /to install them from 
     * @throws Exception
     */
    Map<Resource, String> resolveInitial(String name, List<URI> indexes, Collection<Requirement> requirements) throws Exception;

    /**
     * Resolve the requirements using the specified indexes, 
     * including the supplied installed bundles
     * 
     * @param name the "name" of the resolution
     * @param indexes the indexes to resolve against
     * @param requirements the requirements to resolve
     * @param wiringMap the map of existing wirings
     * @return The resources in the resolution, mapped to the URL they are from /to install them from 
     * @throws Exception
     */
    Map<Resource, String> resolve(String name, List<URI> indexes, Collection<Requirement> requirements, Map<Resource, Wiring> wiringMap) throws Exception;

    /**
     * Turn a "Require-Capability" string into an OSGi {@link Requirement}
     * @param requirement
     * @return
     */
    Requirement parseRequement(String requirement);

    /**
     * Find Behaviour capabilities
     * @param indexes
     * @param ldapFilter
     * @return
     * @throws Exception
     */
    Collection<BehaviourDTO> findBehaviours(List<String> indexes, String ldapFilter) throws Exception;
}
