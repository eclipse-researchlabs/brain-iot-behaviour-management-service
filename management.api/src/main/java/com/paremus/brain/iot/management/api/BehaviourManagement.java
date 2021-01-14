/*******************************************************************************
 * Copyright (C) 2021 Paremus
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/

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
    
    /**
     * Uninstall the named behaviour
     * @param behaviour
     * @param targetNode
     */
    void uninstallBehaviour(BehaviourDTO behaviour, String targetNode);
    
    /**
     * Uninstall all of the behaviours on the target node
     * @param targetNode
     */
    void resetNode(String targetNode);
    
    /**
     * Clear the blacklist on this Bundle Management Service
     */
    void clearBlacklist();
}
