/*******************************************************************************
 * Copyright (C) 2021 Paremus
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 ******************************************************************************/

package com.paremus.brain.iot.installer.impl;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.osgi.framework.BundleContext;
import org.osgi.util.promise.Promise;

import com.paremus.brain.iot.installer.impl.BundleInstallerImpl.Config;

import aQute.bnd.http.HttpClient;
import eu.brain.iot.installer.api.InstallResponseDTO;
import eu.brain.iot.installer.api.InstallResponseDTO.ResponseCode;


public class BundleInstallerImplTest {

    private static final String IDENTITY_REQUIREMENT = "osgi.identity;filter:=(osgi.identity=foo)";

	@Rule
    public MockitoRule rule = MockitoJUnit.rule();
    
    @Mock
    BundleContext context;

    @Mock
    Config config;
    
    @Mock
    HttpClient client;

    BundleInstallerImpl impl;

    @Before
    public void start() throws IOException, Exception {
        impl = new BundleInstallerImpl();
        impl = Mockito.spy(impl);

        Mockito.when(config.cache_location()).thenReturn("");
        
        impl.activate(config, context);
    }

    @After
    public void stop() {
        impl.stop();
    }

    @Test
    public void testIndexesMissing() throws Exception {
        Promise<InstallResponseDTO> promise = impl.installFunction("foo", "1.0.0", null, singletonList(IDENTITY_REQUIREMENT), client);
        
        InstallResponseDTO value = promise.timeout(1000).getValue();
        assertEquals(ResponseCode.BAD_REQUEST, value.code);
        assertTrue(value.messages.toString(), value.messages.contains("no indexes in request"));
    }

    @Test
    public void testRequirementsMissing() throws Exception {
    	Promise<InstallResponseDTO> promise = impl.installFunction("foo", "1.0.0", Arrays.asList("http://brain-iot.org/index.xml"), null, client);
    	
    	InstallResponseDTO value = promise.timeout(1000).getValue();
    	assertEquals(ResponseCode.BAD_REQUEST, value.code);
    	assertTrue(value.messages.toString(), value.messages.contains("no requirements in request"));
    }

    @Test
    public void testIndexesInvalid() throws Exception {
    	Promise<InstallResponseDTO> promise = impl.installFunction("foo", "1.0.0",  singletonList("c:\\invalid"), singletonList(IDENTITY_REQUIREMENT), client);
    	
    	InstallResponseDTO value = promise.timeout(1000).getValue();
    	assertEquals(ResponseCode.BAD_REQUEST, value.code);
    	assertTrue(value.messages.toString(), value.messages.stream().anyMatch(s -> s.contains("indexes contains invalid")));
    }

}
