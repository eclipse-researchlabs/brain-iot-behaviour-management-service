/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.management.impl;

import com.paremus.brain.iot.management.api.ManagementInstallRequestDTO;
import eu.brain.iot.eventing.annotation.SmartBehaviourDefinition;
import eu.brain.iot.eventing.api.SmartBehaviour;
import eu.brain.iot.installer.api.InstallResponseDTO;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
@SmartBehaviourDefinition(consumed = {ManagementInstallRequestDTO.class})
public class InstallRequestConsumer implements SmartBehaviour<ManagementInstallRequestDTO> {

    @Reference
    private BehaviourManagementImpl managementImpl;

    @Override
    public void notify(ManagementInstallRequestDTO event) {
        managementImpl.notify(event);
    }
}
