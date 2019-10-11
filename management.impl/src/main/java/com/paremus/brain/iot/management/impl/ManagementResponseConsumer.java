/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.management.impl;

import static com.paremus.brain.iot.management.impl.BehaviourManagementImpl.BEHAVIOUR_AUTHOR;
import static com.paremus.brain.iot.management.impl.BehaviourManagementImpl.BEHAVIOUR_NAME;

import com.paremus.brain.iot.management.api.ManagementResponseDTO;

import eu.brain.iot.eventing.annotation.SmartBehaviourDefinition;
import eu.brain.iot.eventing.api.SmartBehaviour;

@SmartBehaviourDefinition(consumed = {ManagementResponseDTO.class},
        author = BEHAVIOUR_AUTHOR, name = BEHAVIOUR_NAME,
        description = "Management response consumer"
)
public class ManagementResponseConsumer implements SmartBehaviour<ManagementResponseDTO> {

    private BehaviourManagementImpl bmi;

    public ManagementResponseConsumer(BehaviourManagementImpl behaviourManagementImpl) {
		bmi = behaviourManagementImpl;
	}

	@Override
    public void notify(ManagementResponseDTO event) {
        bmi.notify(event);
    }
}
