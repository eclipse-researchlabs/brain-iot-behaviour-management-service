/*******************************************************************************
 * Copyright (C) 2021 Paremus
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/

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
