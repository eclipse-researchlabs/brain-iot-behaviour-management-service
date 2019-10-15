/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.installer.impl;

import aQute.bnd.osgi.resource.RequirementBuilder;
import eu.brain.iot.installer.api.InstallResponseDTO;
import org.osgi.framework.VersionRange;
import org.osgi.framework.namespace.BundleNamespace;
import org.osgi.namespace.implementation.ImplementationNamespace;
import org.osgi.resource.Namespace;
import org.osgi.resource.Requirement;

import java.util.List;

public class InstallerUtils {

    public static InstallResponseDTO createResponse(InstallResponseDTO.ResponseCode code, List<String> messages, InstallRequestDTO installRequest) {
        InstallResponseDTO event = new InstallResponseDTO();
        event.code = code;
        event.messages = messages;
        event.requestNode = installRequest.sourceNode;
        return event;
    }

    public static Requirement bundleRequirement(String symbolicName, VersionRange range) {
        return createRequirement(BundleNamespace.BUNDLE_NAMESPACE, symbolicName, range, BundleNamespace.CAPABILITY_BUNDLE_VERSION_ATTRIBUTE);
    }

    public static Requirement implementationRequirement(String symbolicName, VersionRange range) {
        return createRequirement(ImplementationNamespace.IMPLEMENTATION_NAMESPACE, symbolicName, range, ImplementationNamespace.CAPABILITY_VERSION_ATTRIBUTE);
    }

    public static Requirement createRequirement(String namespace, String symbolicName, VersionRange range, String versionAttr) {
        String filter = null;

        if (range == null) {
            filter = String.format("(%s=%s)", namespace, symbolicName);
        } else {
            filter = range.toFilterString(versionAttr);

            // add namespace to filter which may already contain '&'
            // (&(version>=x)(!(version>=y)) or (version>=x)
            boolean multiple = filter.charAt(1) == '&';

            if (multiple) {
                filter = String.format("(&(%s=%s)%s", namespace, symbolicName, filter.substring(2));
            } else {
                filter = String.format("(&(%s=%s)%s)", namespace, symbolicName, filter);
            }
        }

        return new RequirementBuilder(namespace)
                .addDirective(Namespace.REQUIREMENT_FILTER_DIRECTIVE, filter)
                .buildSyntheticRequirement(); // synthetic since cannot build Requirement with null Resource.

    }

}
