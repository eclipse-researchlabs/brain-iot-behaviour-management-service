/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
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
