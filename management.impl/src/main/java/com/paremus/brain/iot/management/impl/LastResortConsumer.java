/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.management.impl;

import eu.brain.iot.eventing.annotation.ConsumerOfLastResort;
import eu.brain.iot.eventing.api.UntypedSmartBehaviour;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Map;

@Component
@ConsumerOfLastResort
public class LastResortConsumer implements UntypedSmartBehaviour {

    @Reference
    private ConsumerNotify consumerNotify;

    @Override
    public void notify(String eventType, Map<String, ?> properties) {
        consumerNotify.notifyLastResort(eventType, properties);
    }
}
