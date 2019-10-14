/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package eu.brain.iot.installer.api;

import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.resource.Wiring;
import org.osgi.service.repository.Repository;

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
    Map<Resource, String> resolve(String name, List<? extends Repository> repositories, Collection<Requirement> requirements) throws Exception;

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
    Map<Resource, String> resolveInitial(String name, List<? extends Repository> repositories, Collection<Requirement> requirements) throws Exception;

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
    Map<Resource, String> resolve(String name, List<? extends Repository> repositories, Collection<Requirement> requirements, Map<Resource, Wiring> wiringMap) throws Exception;

}
