/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.installer.test;

import eu.brain.iot.eventing.api.SmartBehaviour;
import eu.brain.iot.installer.api.InstallResponseDTO;

public interface TestResponser {
    void addListener(SmartBehaviour<InstallResponseDTO> listener);
}
