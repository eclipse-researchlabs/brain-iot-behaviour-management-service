/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.paremus.brain.iot.installer.test;

import static com.paremus.brain.iot.management.api.ManagementResponseDTO.ResponseCode.BID;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.service.cm.Configuration;
import org.osgi.util.promise.Promise;

import com.paremus.brain.iot.management.api.ManagementResponseDTO;

import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.osgi.resource.ResourceUtils;
import eu.brain.iot.eventing.api.EventBus;
import eu.brain.iot.eventing.api.SmartBehaviour;
import eu.brain.iot.installer.api.FunctionInstaller;
import eu.brain.iot.installer.api.InstallResponseDTO;
import eu.brain.iot.installer.api.InstallResponseDTO.ResponseCode;


public class BundleInstallerIntegrationTest implements SmartBehaviour<ManagementResponseDTO> {
	
    private final BlockingQueue<ManagementResponseDTO> queue = new LinkedBlockingQueue<>();
    private BundleContext context;
    private EventBus eventBus;
    private FunctionInstaller installer;
    private File resourceDir;
	private TestDependencies deps;

    @Before
    public void setUp() throws Exception {
        deps = TestDependencies.waitInstance(5000);
        assertNotNull("Failed to get test dependencies", deps);

        context = deps.context;
        eventBus = deps.eventBus;
        installer = deps.installer;

        deps.responder.addListener(this);

        String resources = System.getProperty("installer.test.resources", "src/main/resources");
        resourceDir = new File(resources);
    }
    
    @After
    public void tearDown() throws Exception {
    	Promise<?> p = installer.resetNode()
    		.timeout(10000)
    		.recover(p2 -> {
    			System.out.println("Failed to complete in time");
    			dumpThreads();
    			throw new RuntimeException(p2.getFailure());
    		})
    		.thenAccept(response -> assertEquals(ResponseCode.SUCCESS, response.code));
    	
    	if(!p.isDone()) {
    		Thread.sleep(2000);
    		if(!p.isDone()) {
    			System.out.println("The test cleanup seems to be stuck");
    			dumpThreads();
    		}
    	}
    	
    	p.getValue();
    	
    	queue.clear();
    }

	private void dumpThreads() {
		ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
		for (ThreadInfo ti : threadMxBean.dumpAllThreads(true, true)) {
		    System.out.print(ti.toString());
		}
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
    public void notify(ManagementResponseDTO response) {
        System.err.printf("TEST response code=%s \n", response.code);
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

        final int initialBundles = context.getBundles().length;

        InstallResponseDTO response = null;

        TestUtils.listBundles(context);
        
        assertEquals(Collections.emptyMap(), installer.listInstalledFunctions());

        /*
         * resolution failure
         */

        Promise<InstallResponseDTO> promise = installer.installFunction("test.example", "1.0.0", index1, 
        		asList(createBundleRequirement("com.paremus.brain.iot.example.behaviour.impl", "9.9.9")));

        response = promise.timeout(10000).getValue();
        assertEquals(ResponseCode.FAIL, response.code);
        assertEquals(Collections.emptyMap(), installer.listInstalledFunctions());

        /*
         * install example-1
         */
        promise = installer.installFunction("Install-Example-1", "1", index1, 
        		asList(createBundleRequirement("com.paremus.brain.iot.example.behaviour.impl", "0.0.0"),
        				createBundleRequirement("com.paremus.brain.iot.example.light.impl", "0.0.1"),
        				createBundleRequirement("com.paremus.brain.iot.example.sensor.impl", "(0.0.1,0.0.2]")));
        
        response = promise.timeout(10000).getValue();
        List<String> fw1 = TestUtils.listBundles(context);

        assertEquals(ResponseCode.SUCCESS, response.code);
        assertEquals(singletonMap("Install-Example-1", "1"), installer.listInstalledFunctions());
        int added1 = response.messages.size();
        assertTrue("expect >10 bundles", added1 > 10);
        pause("install example-1");

        /*
         * bad update example-2
         */
        promise = installer.updateFunction("Install-Example-1", "1", "Bad Update Example-2", "2", index2bad, 
        		asList(createBundleRequirement("com.paremus.brain.iot.example.behaviour.impl", "0.0.2"),
        				createBundleRequirement("com.paremus.brain.iot.example.light.impl", "0.0.2"),
        				createBundleRequirement("com.paremus.brain.iot.example.sensor.impl",  "(0.0.2,0.0.3]")));
        
        response = promise.timeout(10000).getValue();
        List<String> fw2 = TestUtils.listBundles(context);

        assertEquals(ResponseCode.FAIL, response.code);
        assertEquals(singletonMap("Install-Example-1", "1"), installer.listInstalledFunctions());
        assertEquals("FW state should have rolled-back", fw1, fw2);
        pause("bad update example-2");

        /*
         * update example-2
         */
        promise = installer.updateFunction("Install-Example-1", "1", "Install-Example-2", "2", index2, 
        		asList(createBundleRequirement("com.paremus.brain.iot.example.behaviour.impl", "0.0.2"),
        				createBundleRequirement("com.paremus.brain.iot.example.light.impl", "0.0.2"),
        				createBundleRequirement("com.paremus.brain.iot.example.sensor.impl", "(0.0.2,0.0.3]")));
        
        response = promise.timeout(10000).getValue();
        TestUtils.listBundles(context);

        assertEquals(ResponseCode.SUCCESS, response.code);
        assertEquals(singletonMap("Install-Example-2", "2"), installer.listInstalledFunctions());
        int added2 = response.messages.size();
        assertEquals("update 3 changed bundles", 3, added2);
        pause("update example-2");


        /*
         * uninstall example
         */
        promise = installer.uninstallFunction("Install-Example-2", "2");
        response = promise.timeout(10000).getValue();
        TestUtils.listBundles(context);

        assertEquals(ResponseCode.SUCCESS, response.code);
        assertEquals(Collections.emptyMap(), installer.listInstalledFunctions());

        /*
         * uninstall all bundles
         */
        promise = installer.resetNode();
        response = promise.timeout(10000).getValue();
        assertEquals(ResponseCode.SUCCESS, response.code);

        int finalBundles = context.getBundles().length;
        assertEquals("RESET should remove all added bundles", initialBundles, finalBundles);

    }

    // This test used to work, but it's the older version of doing things and for some reason
    // I can't work out how to reset the tests fully.
    @Test
    @Ignore
    public void testLastResort() throws Exception {
    	
    	// index2.xml is an old-style 2-tier index
    	configureBMSAndInstaller(new File(resourceDir, "index2.xml").toURI().toString());
    	
        eventBus.deliver("com.paremus.brain.iot.example.sensor.api.SensorReadingDTO", Collections.emptyMap());

        ManagementResponseDTO response;
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
    	
    	ManagementResponseDTO response;
    	List<String> bundles;
    	
    	response = queue.poll(10, TimeUnit.SECONDS);
    	assertEquals(BID, response.code);
    	assertEquals("com.paremus.brain.iot.example.behaviour.impl", response.symbolicName);
    	assertEquals("0.0.1.SNAPSHOT", response.version);
    	

    	response = queue.poll(30, TimeUnit.SECONDS);
    	bundles = TestUtils.listBundles(context);
    	
    	assertTrue(response != null);
    	assertEquals(ManagementResponseDTO.ResponseCode.INSTALL_OK, response.code);
    	assertTrue(bundles.stream().anyMatch(s -> s.contains("example.behaviour.impl")));
    	
    	response = queue.poll(10, TimeUnit.SECONDS);
    	assertEquals(BID, response.code);
    	assertEquals("com.paremus.brain.iot.example.light.impl", response.symbolicName);
    	assertEquals("0.0.1.SNAPSHOT", response.version);
    	
    	response = queue.poll(30, TimeUnit.SECONDS);
    	bundles = TestUtils.listBundles(context);
    	
    	assertTrue(response != null);
    	assertEquals(ManagementResponseDTO.ResponseCode.INSTALL_OK, response.code);
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

    private String createBundleRequirement(String symbolicName, String versionRange) {
    	try {
			return ResourceUtils.toRequireCapability(CapReqBuilder.createBundleRequirement(symbolicName, versionRange).buildSyntheticRequirement());
		} catch (Exception e) {
			throw new RuntimeException();
		}
    }
    
}
