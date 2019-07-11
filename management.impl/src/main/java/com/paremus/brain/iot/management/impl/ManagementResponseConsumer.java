/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.management.impl;

import com.paremus.brain.iot.management.api.ManagementResponseDTO;
import eu.brain.iot.eventing.annotation.SmartBehaviourDefinition;
import eu.brain.iot.eventing.api.SmartBehaviour;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
@SmartBehaviourDefinition(consumed = {ManagementResponseDTO.class})
public class ManagementResponseConsumer implements SmartBehaviour<ManagementResponseDTO> {

    @Reference
    private BehaviourManagementImpl managementImpl;

    @Override
    public void notify(ManagementResponseDTO event) {
        managementImpl.notify(event);
    }
}