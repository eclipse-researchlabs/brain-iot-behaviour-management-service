/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.paremus.brain.iot.management.api;

import eu.brain.iot.installer.api.BehaviourDTO;

import java.util.Collection;

public interface BehaviourManagement {
	/**
	 * Find the behaviours in the known indexes
	 * 
	 * @param ldapFilter
	 * @return
	 * @throws Exception
	 */
    Collection<BehaviourDTO> findBehaviours(String ldapFilter) throws Exception;

    /**
     * Install the named behaviour
     * @param behaviour
     * @param targetNode
     */
    void installBehaviour(BehaviourDTO behaviour, String targetNode);
}
