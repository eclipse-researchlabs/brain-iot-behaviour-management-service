/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.installer.test;

import eu.brain.iot.eventing.annotation.SmartBehaviourDefinition;
import eu.brain.iot.eventing.api.SmartBehaviour;
import eu.brain.iot.installer.api.InstallResponseDTO;
import org.osgi.service.component.annotations.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@SmartBehaviourDefinition(consumed = {InstallResponseDTO.class},
        author = "Paremus", name = "[Brain-IoT] Bundle Installer Test",
        description = " Test response handler"
)
public class TestResponseHandler implements SmartBehaviour<InstallResponseDTO>, TestResponser {
    private List<SmartBehaviour<InstallResponseDTO>> listeners = new ArrayList<>();

    @Override
    public void notify(InstallResponseDTO response) {
        for (SmartBehaviour<InstallResponseDTO> listener : listeners) {
            listener.notify(response);
        }
    }

    @Override
    public void addListener(SmartBehaviour<InstallResponseDTO> listener) {
        listeners.add(listener);
    }
}
