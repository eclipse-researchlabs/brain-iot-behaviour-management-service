/*******************************************************************************
 * Copyright (C) 2021 Paremus
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/

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
