/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package eu.brain.iot.installer.api;

import eu.brain.iot.eventing.api.BrainIoTEvent;

import java.util.List;
import java.util.Map;

/**
 * The event that can be used to install or update bundles.
 */

public class InstallRequestDTO extends BrainIoTEvent {

    /**
     * A friendly name for the smart behaviour
     */
    public String name;

    /**
     * The symbolic name for the smart behaviour
     */
    public String symbolicName;

    /**
     * The version of the smart behaviour
     */
    public String version;

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
    public List<String> indexes;

    /**
     * The bundles to be installed or updated. Map key is bundle symbolic name, value is version range.
     * The range format must be accepted by {@link org.osgi.framework.VersionRange}
     */
    public Map<String, String> bundles;

    /**
     * List of required capabilities.
     * Format is same as the manifest header Require-Capability, for example:
     * <pre>osgi.service;
     *     filter:="(osgi.jaxrs.media.type=application/json)";
     *     resolution:=optional;
     *     effective:=active</pre>
     */
    public List<String> requirements;

}
