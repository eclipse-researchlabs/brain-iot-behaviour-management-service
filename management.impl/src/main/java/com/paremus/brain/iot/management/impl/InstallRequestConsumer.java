/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.management.impl;

import static com.paremus.brain.iot.management.impl.BehaviourManagementImpl.BEHAVIOUR_AUTHOR;
import static com.paremus.brain.iot.management.impl.BehaviourManagementImpl.BEHAVIOUR_NAME;

import com.paremus.brain.iot.management.api.ManagementInstallRequestDTO;

import eu.brain.iot.eventing.annotation.SmartBehaviourDefinition;
import eu.brain.iot.eventing.api.SmartBehaviour;

@SmartBehaviourDefinition(consumed = {ManagementInstallRequestDTO.class},
        author = BEHAVIOUR_AUTHOR, name = BEHAVIOUR_NAME,
        description = "Install request consumer")
public class InstallRequestConsumer implements SmartBehaviour<ManagementInstallRequestDTO> {

    private BehaviourManagementImpl bmi;

    public InstallRequestConsumer(BehaviourManagementImpl behaviourManagementImpl) {
		bmi = behaviourManagementImpl;
	}

	@Override
    public void notify(ManagementInstallRequestDTO event) {
        bmi.notify(event);
    }
}
