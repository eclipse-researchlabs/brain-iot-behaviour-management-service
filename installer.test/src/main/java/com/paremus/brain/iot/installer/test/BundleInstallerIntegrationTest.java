/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.paremus.brain.iot.installer.test;

import static eu.brain.iot.installer.api.InstallRequestDTO.InstallAction.UNINSTALL;
import static eu.brain.iot.installer.api.InstallRequestDTO.InstallAction.UPDATE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;

import eu.brain.iot.eventing.api.EventBus;
import eu.brain.iot.eventing.api.SmartBehaviour;
import eu.brain.iot.installer.api.InstallRequestDTO;
import eu.brain.iot.installer.api.InstallRequestDTO.InstallAction;
import eu.brain.iot.installer.api.InstallResponseDTO;
import eu.brain.iot.installer.api.InstallResponseDTO.ResponseCode;


public class BundleInstallerIntegrationTest implements SmartBehaviour<InstallResponseDTO> {

    private final BlockingQueue<InstallResponseDTO> queue = new LinkedBlockingQueue<>();
    private BundleContext context;
    private EventBus eventBus;
    private File resourceDir;
	private TestDependencies deps;

    @Before
    public void setUp() throws Exception {
        deps = TestDependencies.waitInstance(5000);
        assertNotNull("Failed to get test dependencies", deps);

        context = deps.context;
        eventBus = deps.eventBus;

        deps.responder.addListener(this);

        String resources = System.getProperty("installer.test.resources", "src/main/resources");
        resourceDir = new File(resources);
    }
    
    @After
    public void tearDown() throws Exception {
    	InstallRequestDTO reset = createRequest(null, InstallAction.RESET, null, null);
    	reset.name = "Uninstall Example Test";
        eventBus.deliver(reset);
        InstallResponseDTO response = queue.poll(5, TimeUnit.SECONDS);
        assertEquals(ResponseCode.SUCCESS, response.code);
    }

	private void configureBMSAndInstaller(String indexLocation) throws IOException, InterruptedException {
		// configure last_resort handler
		boolean inCI = System.getenv("CI") != null;

		Dictionary<String, Object> props = new Hashtable<>();
        props.put("indexes", indexLocation);
        
		if(inCI) {
        	props.put("connection.settings", System.getenv("CI_PROJECT_DIR") + "/.m2/settings.xml");
        }

        Configuration config = deps.configAdmin.getConfiguration("eu.brain.iot.BehaviourManagementService", "?");
        config.update(props);
        
        if(inCI) {
        	props.remove("indexes");
        	config = deps.configAdmin.getConfiguration("eu.brain.iot.BundleInstallerService", "?");
            config.update(props);
        }
        
        
        Thread.sleep(1000);
	}

    @Override
    public void notify(InstallResponseDTO response) {
        System.err.printf("TEST response action=%s code=%s message=%s\n",
                response.installRequest.action, response.code, response.messages);
        queue.add(response);
    }

    @Test
    public void testInstallExample() throws Exception {
        File repo1 = new File(resourceDir, "index-0.0.1.xml");
        File repo2 = new File(resourceDir, "index-0.0.2.xml");
        File repo2bad = new File(resourceDir, "index-0.0.2-bad.xml");

        assertTrue("index-0.0.1.xml not found", repo1.exists());
        assertTrue("index-0.0.2.xml not found", repo2.exists());
        assertTrue("index-0.0.2-bad.xml not found", repo2bad.exists());

        List<String> index1 = Collections.singletonList(repo1.toURI().toString());
        List<String> index2 = Collections.singletonList(repo2.toURI().toString());
        List<String> index2bad = Collections.singletonList(repo2bad.toURI().toString());

        Map<String, String> bundles = new HashMap<>();

        final int initialBundles = context.getBundles().length;

        InstallRequestDTO install = createRequest("test.example", InstallAction.INSTALL, index1, bundles);
        InstallResponseDTO response = null;

        TestUtils.listBundles(context);

        /*
         * resolution failure
         */
        install.name = "Resolution Failure Test";
        bundles.put("com.paremus.brain.iot.example.behaviour.impl", "9.9.9");


        eventBus.deliver(install);

        response = queue.poll(10, TimeUnit.SECONDS);
        assertEquals(ResponseCode.FAIL, response.code);

        /*
         * install example-1
         */
        install.name = "Install Example-1";
        install.version = "1";
        bundles.put("com.paremus.brain.iot.example.behaviour.impl", null);
        bundles.put("com.paremus.brain.iot.example.light.impl", "0.0.1");
        bundles.put("com.paremus.brain.iot.example.sensor.impl", "(0.0.1,0.0.2]");
        eventBus.deliver(install);

        response = queue.poll(10, TimeUnit.SECONDS);
        List<String> fw1 = TestUtils.listBundles(context);

        assertEquals(ResponseCode.SUCCESS, response.code);
        int added1 = response.messages.size();
        assertTrue("expect >10 bundles", added1 > 10);
        pause("install example-1");

        /*
         * bad update example-2
         */
        install.action = UPDATE;
        install.name = "Bad Update Example-2";
        install.version = "2";
        install.indexes = index2bad;
        bundles.put("com.paremus.brain.iot.example.behaviour.impl", "0.0.2");
        bundles.put("com.paremus.brain.iot.example.light.impl", "0.0.2");
        bundles.put("com.paremus.brain.iot.example.sensor.impl", "(0.0.2,0.0.3]");
        eventBus.deliver(install);

        response = queue.poll(10, TimeUnit.SECONDS);
        List<String> fw2 = TestUtils.listBundles(context);

        assertEquals(ResponseCode.FAIL, response.code);
        assertEquals("FW state should have rolled-back", fw1, fw2);
        pause("bad update example-2");

        /*
         * update example-2
         */
        install.action = UPDATE;
        install.name = "Install Example-2";
        install.version = "2";
        install.indexes = index2;
        bundles.put("com.paremus.brain.iot.example.behaviour.impl", "0.0.2");
        bundles.put("com.paremus.brain.iot.example.light.impl", "0.0.2");
        bundles.put("com.paremus.brain.iot.example.sensor.impl", "(0.0.2,0.0.3]");
        eventBus.deliver(install);

        response = queue.poll(10, TimeUnit.SECONDS);
        TestUtils.listBundles(context);

        assertEquals(ResponseCode.SUCCESS, response.code);
        int added2 = response.messages.size();
        assertEquals("update 3 changed bundles", 3, added2);
        pause("update example-2");


        /*
         * uninstall example
         */
        install.action = UNINSTALL;
        install.name = "Uninstall Example Test";
        eventBus.deliver(install);

        response = queue.poll(10, TimeUnit.SECONDS);
        TestUtils.listBundles(context);

        assertEquals(ResponseCode.SUCCESS, response.code);

        /*
         * uninstall all bundles
         */
        InstallRequestDTO reset = createRequest(null, InstallAction.RESET, null, null);
        reset.name = "Uninstall Example Test";
        eventBus.deliver(reset);

        response = queue.poll(5, TimeUnit.SECONDS);
        assertEquals(ResponseCode.SUCCESS, response.code);

        int finalBundles = context.getBundles().length;
        assertEquals("RESET should remove all added bundles", initialBundles, finalBundles);

    }

    // This test should work, but it's the older version of doing things and for some reason
    // I can't work out how to reset the tests fully.
    @Test
    @Ignore
    public void testLastResort() throws Exception {
    	
    	// index2.xml is an old-style 2-tier index
    	configureBMSAndInstaller(new File(resourceDir, "index2.xml").toURI().toString());
    	
        eventBus.deliver("com.paremus.brain.iot.example.sensor.api.SensorReadingDTO", Collections.emptyMap());

        InstallResponseDTO response;
        List<String> bundles;

        response = queue.poll(10, TimeUnit.SECONDS);
        bundles = TestUtils.listBundles(context);

        assertTrue(response != null);
        assertEquals(ResponseCode.SUCCESS, response.code);
        assertTrue(bundles.stream().anyMatch(s -> s.contains("example.behaviour.impl")));

        response = queue.poll(20, TimeUnit.SECONDS);
        bundles = TestUtils.listBundles(context);

        assertTrue(response != null);
        assertEquals(ResponseCode.SUCCESS, response.code);
        assertTrue(bundles.stream().anyMatch(s -> s.contains("example.light.impl")));

        pause("test last resort");
    }

    @Test
    public void testMarketplaceIndex() throws Exception {
    	
    	// This is the "official" example marketplace
    	configureBMSAndInstaller("https://nexus.repository-pert.ismb.it/repository/marketplaces/com.paremus.brain.iot.marketplace/security-light-marketplace/0.0.1-SNAPSHOT/index.xml");
    	
    	eventBus.deliver("com.paremus.brain.iot.example.sensor.api.SensorReadingDTO", Collections.emptyMap());
    	
    	InstallResponseDTO response;
    	List<String> bundles;
    	
    	response = queue.poll(30, TimeUnit.SECONDS);
    	bundles = TestUtils.listBundles(context);
    	
    	assertTrue(response != null);
    	assertEquals(ResponseCode.SUCCESS, response.code);
    	assertTrue(bundles.stream().anyMatch(s -> s.contains("example.behaviour.impl")));
    	
    	response = queue.poll(60, TimeUnit.SECONDS);
    	bundles = TestUtils.listBundles(context);
    	
    	assertTrue(response != null);
    	assertEquals(ResponseCode.SUCCESS, response.code);
    	assertTrue(bundles.stream().anyMatch(s -> s.contains("example.light.impl")));
    	
    	pause("test last resort");
    }

    private static void pause(String message) {
        String[] exampleURLs = {
                "http://localhost:8080/sensor-ui/index.html",
                "http://localhost:8080/light-ui/index.html",
        };

        if (Boolean.getBoolean("installer.test.pause")) {
            System.err.println("*** Pausing for " + message);
            System.err.println(String.join("\n", exampleURLs));
            System.err.println("*** Hit <Enter> to continue");
            try {
                System.in.read();
            } catch (Exception e) {
            }
        } else {
            System.err.println("*** use -runvm: -Dinstaller.test.pause=true to browse: " + message);
        }
    }

    private static InstallRequestDTO createRequest(String sn, InstallAction action, List<String> repoPath, Map<String, String> bundles) {
        InstallRequestDTO request = new InstallRequestDTO();
        request.symbolicName = sn;
        request.action = action;
        request.indexes = repoPath;
        request.bundles = bundles;
        return request;
    }

}
