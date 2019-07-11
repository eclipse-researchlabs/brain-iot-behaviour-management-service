/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.paremus.brain.iot.management.api;

import eu.brain.iot.eventing.api.BrainIoTEvent;

import java.util.Map;

public abstract class ManagementRequestDTO extends BrainIoTEvent {

    public String eventType;

    public Map<String, Object> eventData;

}
