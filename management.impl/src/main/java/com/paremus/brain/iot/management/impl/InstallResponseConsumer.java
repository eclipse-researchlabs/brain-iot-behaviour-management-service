/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.management.impl;

import eu.brain.iot.eventing.annotation.SmartBehaviourDefinition;
import eu.brain.iot.eventing.api.SmartBehaviour;
import eu.brain.iot.installer.api.InstallResponseDTO;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
@SmartBehaviourDefinition(consumed = {InstallResponseDTO.class},
        author = "Paremus", name = "[Brain-IoT] Behaviour Management Service",
        description = "Install response consumer"
)
public class InstallResponseConsumer implements SmartBehaviour<InstallResponseDTO> {

    @Reference
    private BehaviourManagementImpl managementImpl;

    @Override
    public void notify(InstallResponseDTO event) {
        managementImpl.notify(event);
    }
}
