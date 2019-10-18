/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.paremus.brain.iot.installer.impl;

import java.util.ArrayList;
import java.util.List;

import org.osgi.resource.Requirement;
import org.osgi.service.repository.Repository;
import org.osgi.util.promise.Deferred;

import aQute.bnd.http.HttpClient;
import eu.brain.iot.installer.api.InstallResponseDTO;

/**
 * The event that can be used to install or update bundles.
 */

public class InstallRequest {

    /**
     * The sponsor for the install
     */
    public String sponsor;

    /**
     * Set of possible installer actions
     */
    public enum InstallAction {
        /**
         * Install the specified smart behaviour.
         * If a different version is already installed, it is uninstalled first.
         */
        INSTALL,

        /**
         * Update the specified smart behaviour.
         * If a different version is already installed, it is stopped before the update
         * and uninstalled after the update.
         */
        UPDATE,

        /**
         * Uninstall the specified smart behaviour.
         */
        UNINSTALL,

        /**
         * Uninstall all smart behaviours and reset any caches/blacklists.
         */
        RESET;
    }

    public InstallAction action;

    /**
     * Repository URLs against which to resolve request
     */
    public List<String> indexes = new ArrayList<>();

    /**
     * List of required capabilities.
     * Format is same as the manifest header Require-Capability, for example:
     * <pre>osgi.service;
     *     filter:="(osgi.jaxrs.media.type=application/json)";
     *     resolution:=optional;
     *     effective:=active</pre>
     */
    public List<String> requirements = new ArrayList<>();
    
    public Deferred<InstallResponseDTO> response;

    /**
     * The old sponsor if this is an Update
     */
	public String oldSponsor;
	
	/**
	 * The Http Client to use to download and install bundles
	 */
	public HttpClient client;

}
