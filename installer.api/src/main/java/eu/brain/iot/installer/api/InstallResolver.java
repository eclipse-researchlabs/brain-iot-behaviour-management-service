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

    Map<Resource, String> resolve(String name, List<URI> indexes, Collection<Requirement> requirements) throws Exception;

    Map<Resource, String> resolveInitial(String name, List<URI> indexes, Collection<Requirement> requirements) throws Exception;

    Map<Resource, String> resolve(String name, List<URI> indexes, Collection<Requirement> requirements, Map<Resource, Wiring> wiringMap) throws Exception;

    Requirement parseRequement(String requirement);

    Collection<BehaviourDTO> findBehaviours(List<String> indexes, String ldapFilter) throws Exception;
}
