/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.management.impl;

import java.util.Map;

import eu.brain.iot.eventing.annotation.ConsumerOfLastResort;
import eu.brain.iot.eventing.api.UntypedSmartBehaviour;

@ConsumerOfLastResort
public class LastResortConsumer implements UntypedSmartBehaviour {

    private BehaviourManagementImpl bmi;

    public LastResortConsumer(BehaviourManagementImpl behaviourManagementImpl) {
		bmi = behaviourManagementImpl;
	}

	@Override
    public void notify(String eventType, Map<String, ?> properties) {
        bmi.notifyLastResort(eventType, properties);
    }
}
