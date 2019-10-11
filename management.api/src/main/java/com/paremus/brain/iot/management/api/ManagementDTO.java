/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.paremus.brain.iot.management.api;

import eu.brain.iot.eventing.api.BrainIoTEvent;

public abstract class ManagementDTO extends BrainIoTEvent {

    public String requestIdentity;
    
    public String requirement;
}
