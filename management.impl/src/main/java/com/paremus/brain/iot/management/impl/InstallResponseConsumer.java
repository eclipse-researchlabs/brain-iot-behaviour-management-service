/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.management.impl;

import static com.paremus.brain.iot.management.impl.BehaviourManagementImpl.BEHAVIOUR_AUTHOR;
import static com.paremus.brain.iot.management.impl.BehaviourManagementImpl.BEHAVIOUR_NAME;

import eu.brain.iot.eventing.annotation.SmartBehaviourDefinition;
import eu.brain.iot.eventing.api.SmartBehaviour;
import eu.brain.iot.installer.api.InstallResponseDTO;

@SmartBehaviourDefinition(consumed = {InstallResponseDTO.class},
        author = BEHAVIOUR_AUTHOR, name = BEHAVIOUR_NAME,
        description = "Install response consumer"
)
public class InstallResponseConsumer implements SmartBehaviour<InstallResponseDTO> {

	private BehaviourManagementImpl bmi;

    public InstallResponseConsumer(BehaviourManagementImpl behaviourManagementImpl) {
		this.bmi = behaviourManagementImpl;
	}

	@Override
    public void notify(InstallResponseDTO event) {
        bmi.notify(event);
    }
}
