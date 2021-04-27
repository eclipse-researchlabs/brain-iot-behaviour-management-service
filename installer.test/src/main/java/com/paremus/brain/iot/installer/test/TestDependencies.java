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

import java.util.Collections;

import org.osgi.framework.BundleContext;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogListener;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.admin.LoggerAdmin;
import org.osgi.service.log.admin.LoggerContext;

import eu.brain.iot.eventing.api.EventBus;
import eu.brain.iot.installer.api.FunctionInstaller;

/**
 * Helper to allow DS to provide test dependencies
 */
@Component(immediate = true)
public class TestDependencies implements LogListener {

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private LogReaderService logReader;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private LoggerAdmin logAdmin;

    @Reference
    EventBus eventBus;
    
    @Reference
    FunctionInstaller installer;

    @Reference
    TestResponser responder;

    @Reference
    ConfigurationAdmin configAdmin;

    BundleContext context;

    private static volatile TestDependencies instance;

    @Activate
    private void activate(BundleContext context) {
        this.context = context;
        TestDependencies.instance = this;

        if (logAdmin != null) {
            LoggerContext rootContext = logAdmin.getLoggerContext(null);
            rootContext.setLogLevels(Collections.singletonMap("com.paremus.brain.iot", LogLevel.INFO));
        }

        if (logReader != null)
            logReader.addLogListener(this);
    }
    
    @Deactivate
    private void deactivate() {
    	TestDependencies.instance = null;
    }

    private final static int step = 100;

    public static TestDependencies waitInstance(int millis) throws InterruptedException {
        while (instance == null && millis > 0) {
            millis -= step;
            Thread.sleep(step);
        }
        return instance;
    }

    @Override
    public void logged(LogEntry entry) {
        System.err.printf("test:%s: %s\n", entry.getLogLevel(), entry.getMessage());
    }
}

