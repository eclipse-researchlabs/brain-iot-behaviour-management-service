/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

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
        CONSUMER_NOT_CONFIGURED;
    }

    public AlertType alert;

    public List<String> messages;

    public String eventType;

    public Map<String, Object> properties;

}
