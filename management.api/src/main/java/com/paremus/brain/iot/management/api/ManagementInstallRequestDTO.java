/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.paremus.brain.iot.management.api;

public class ManagementInstallRequestDTO extends TargettedManagementDTO {
	
	public ManagementInstallAction action;
	
	/**
     * Set of possible actions
     */
    public enum ManagementInstallAction {
        /**
         * Install the specified smart behaviour.
         * If a different version is already installed, it is uninstalled first.
         */
        INSTALL,

        /**
         * Update the specified smart behaviour.
         * If a different version is already installed, it is stopped before the update
         * and uninstalled after the update.
         */
        UPDATE,

        /**
         * Uninstall the specified smart behaviour.
         */
        UNINSTALL,

        /**
         * Uninstall all smart behaviours and reset any caches/blacklists.
         */
        RESET;
    }
}
