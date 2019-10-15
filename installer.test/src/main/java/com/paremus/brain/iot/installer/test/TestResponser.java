/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.installer.test;

import com.paremus.brain.iot.management.api.ManagementResponseDTO;

import eu.brain.iot.eventing.api.SmartBehaviour;

public interface TestResponser {
    void addListener(SmartBehaviour<ManagementResponseDTO> listener);
}
