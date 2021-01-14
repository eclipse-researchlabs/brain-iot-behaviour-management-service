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

import eu.brain.iot.eventing.api.BrainIoTEvent;

import java.util.List;
import java.util.Map;

/**
 * The management event to alert operators that manual action is required
 */

public class ManagementAlertDTO extends BrainIoTEvent {
    /**
     * possible alerts
     */
    public enum AlertType {
        /**
         * consumer for unhandled event not found in repository
         */
        CONSUMER_NOT_FOUND,

        /**
         * no hosts able & willing to install
         */
        NO_HOSTS,

        /**
         * chosen host failed to install
         */
        INSTALL_FAILED,

        /**
         * event still unhandled after installation
         */
        CONSUMER_NOT_CONFIGURED,
    	
    	/**
    	 * The unhandled event resulted in too many different possible installation options
    	 */
    	TOO_MANY_OPTIONS;
    }

    public AlertType alert;

    public List<String> messages;

    public String eventType;

    public Map<String, Object> properties;

}
