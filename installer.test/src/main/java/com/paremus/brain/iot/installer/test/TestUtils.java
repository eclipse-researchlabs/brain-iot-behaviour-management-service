/*******************************************************************************
 * Copyright (C) 2021 Paremus
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/

package com.paremus.brain.iot.installer.test;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.System.err;

public class TestUtils {

    enum State {
        UNINSTALLED(Bundle.UNINSTALLED),
        INSTALLED(Bundle.INSTALLED),
        RESOLVED(Bundle.RESOLVED),
        STARTING(Bundle.STARTING),
        STOPPING(Bundle.STOPPING),
        ACTIVE(Bundle.ACTIVE);

        private int numVal;

        State(int numVal) {
            this.numVal = numVal;
        }

        public int getNumVal() {
            return numVal;
        }
    }

    static Map<Integer, State> stateMap = new HashMap<>();

    static {
        for (State s : State.values()) {
            stateMap.put(s.getNumVal(), s);
        }
    }

    public static List<String> listBundles(BundleContext context) {
        List<String> list = new ArrayList<>();

        for (Bundle b : context.getBundles()) {
            State s = stateMap.get(b.getState());
            list.add(String.format("%s: %s", s, b));
        }

        System.out.println(String.join("\n", list));
        return list;
    }
}
