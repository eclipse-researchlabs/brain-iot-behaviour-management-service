/*******************************************************************************
 * Copyright (C) 2021 Paremus
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/

package com.paremus.brain.iot.installer.test;

import java.util.ArrayList;
import java.util.List;

import org.osgi.service.component.annotations.Component;

import com.paremus.brain.iot.management.api.ManagementResponseDTO;

import eu.brain.iot.eventing.annotation.SmartBehaviourDefinition;
import eu.brain.iot.eventing.api.SmartBehaviour;

@Component
@SmartBehaviourDefinition(consumed = {ManagementResponseDTO.class},
        author = "Paremus", name = "[Brain-IoT] Bundle Installer Test",
        description = " Test response handler"
)
public class TestResponseHandler implements SmartBehaviour<ManagementResponseDTO>, TestResponser {
    private List<SmartBehaviour<ManagementResponseDTO>> listeners = new ArrayList<>();

    @Override
    public void notify(ManagementResponseDTO response) {
        for (SmartBehaviour<ManagementResponseDTO> listener : listeners) {
            listener.notify(response);
        }
    }

    @Override
    public void addListener(SmartBehaviour<ManagementResponseDTO> listener) {
        listeners.add(listener);
    }
}
