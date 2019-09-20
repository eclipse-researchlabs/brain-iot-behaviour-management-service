/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.paremus.brain.iot.management.api;

import eu.brain.iot.installer.api.BehaviourDTO;

import java.util.Collection;

public interface BehaviourManagement {
    Collection<BehaviourDTO> findBehaviours(String ldapFilter) throws Exception;

    void installBehaviour(BehaviourDTO behaviour, String targetNode);
}
