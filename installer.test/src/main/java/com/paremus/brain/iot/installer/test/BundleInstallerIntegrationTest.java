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

import static com.paremus.brain.iot.management.api.ManagementResponseDTO.ResponseCode.BID;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.cm.Configuration;
import org.osgi.util.promise.Promise;

import com.paremus.brain.iot.management.api.BehaviourManagement;
import com.paremus.brain.iot.management.api.ManagementResponseDTO;

import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.Processor;
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
	private HttpClient client;

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
        
        Processor processor = new Processor();
    	processor.set(Processor.CONNECTION_SETTINGS, inCI() ? getCIConnectionSettings() : "");

    	client = new HttpClient();
    	client.setReporter(processor);
    	client.setRegistry(processor);
    	client.setCache(new File("target/test-cache"));

    	client.readSettings(processor);
    }
    
    @After
    public void tearDown() throws Exception {
    	Promise<?> p = installer.resetNode()
    		.timeout(10000)
    		.recover(p2 -> {
    			System.out.println("Failed to complete in time");
    			dumpThreads();
    			
    			if(System.getenv("CI") != null) {
    				ServiceReference<?> ref = context.getServiceReference("com.paremus.brain.iot.installer.impl.FrameworkInstaller");
    				Object service = context.getService(ref);
    				try {
    					Method m = service.getClass().getMethod("removeSponsor", Object.class);
    					for (Entry<String, String> e : installer.listInstalledFunctions().entrySet()) {
							m.invoke(service, e.getKey() + ":" + e.getValue());
						}
    					
    				} finally {
    					context.ungetService(ref);
    				}
    				
    				if(installer.listInstalledFunctions().isEmpty()) {
    					InstallResponseDTO dto = new InstallResponseDTO();
    					dto.code = ResponseCode.SUCCESS;
    					return dto;
    				}
    			}
    			
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
    	
    	assertEquals(Collections.emptyMap(), installer.listInstalledFunctions());
    	
    	ServiceReference<BehaviourManagement> ref = context.getServiceReference(BehaviourManagement.class);
    	if(ref != null) {
    		try {
    			context.getService(ref).clearBlacklist();
    		} finally {
    			context.ungetService(ref);
    		}
    	}
    }

	private void dumpThreads() {
		ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
		for (ThreadInfo ti : threadMxBean.dumpAllThreads(true, true)) {
		    System.out.print(ti.toString());
		}
	}

	private void configureBMSAndInstaller(String indexLocation, String... preinstalled) throws IOException, InterruptedException {
		// configure last_resort handler
		Dictionary<String, Object> props = new Hashtable<>();
        props.put("indexes", indexLocation);
        
        if(preinstalled.length > 0) {
        	props.put("preinstalled.behaviours", Arrays.asList(preinstalled));
        }
        
		if(inCI()) {
        	props.put("connection.settings", getCIConnectionSettings());
        }

        Configuration config = deps.configAdmin.getConfiguration("eu.brain.iot.BehaviourManagementService", "?");
        config.update(props);

        Thread.sleep(3000);
	}

	private boolean inCI() {
		return System.getenv("CI") != null;
	}

	private String getCIConnectionSettings() {
		return System.getenv("CI_PROJECT_DIR") + "/.m2/settings.xml";
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
        		asList(createBundleRequirement("com.paremus.brain.iot.example.behaviour.impl", "9.9.9")),
        		client);

        response = promise.timeout(10000).getValue();
        assertEquals(ResponseCode.FAIL, response.code);
        assertEquals(Collections.emptyMap(), installer.listInstalledFunctions());

        /*
         * install example-1
         */
        promise = installer.installFunction("Install-Example-1", "1", index1, 
        		asList(createBundleRequirement("com.paremus.brain.iot.example.behaviour.impl", "0.0.0"),
        				createBundleRequirement("com.paremus.brain.iot.example.light.impl", "0.0.1"),
        				createBundleRequirement("com.paremus.brain.iot.example.sensor.impl", "(0.0.1,0.0.2]")),
        		client);
        
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
        				createBundleRequirement("com.paremus.brain.iot.example.sensor.impl",  "(0.0.2,0.0.3]")),
        		client);
        
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
        				createBundleRequirement("com.paremus.brain.iot.example.sensor.impl", "(0.0.2,0.0.3]")),
        		client);
        
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
    	

    	response = queue.poll(60, TimeUnit.SECONDS);
    	bundles = TestUtils.listBundles(context);
    	
    	assertTrue(response != null);
    	assertEquals(ManagementResponseDTO.ResponseCode.INSTALL_OK, response.code);
    	assertTrue(bundles.stream().anyMatch(s -> s.contains("example.behaviour.impl")));
    	
    	response = queue.poll(10, TimeUnit.SECONDS);
    	assertEquals(BID, response.code);
    	assertEquals("com.paremus.brain.iot.example.light.impl", response.symbolicName);
    	assertEquals("0.0.1.SNAPSHOT", response.version);
    	
    	response = queue.poll(60, TimeUnit.SECONDS);
    	bundles = TestUtils.listBundles(context);
    	
    	assertTrue(response != null);
    	assertEquals(ManagementResponseDTO.ResponseCode.INSTALL_OK, response.code);
    	assertTrue(bundles.stream().anyMatch(s -> s.contains("example.light.impl")));
    	
    	pause("test last resort");
    }

    @Test
    public void testPreInstallation() throws Exception {
    	
    	// This is the "official" example marketplace
    	configureBMSAndInstaller("https://nexus.repository-pert.ismb.it/repository/marketplaces/com.paremus.brain.iot.marketplace/security-light-marketplace/0.0.1-SNAPSHOT/index.xml",
    			"com.paremus.brain.iot.example.behaviour.impl:0.0.1.SNAPSHOT");

    	boolean installed = false;
    	for(int i = 0; i < 60; i++) {
    		if(installer.listInstalledFunctions().isEmpty()) {
    			Thread.sleep(1000);
    		} else {
    			installed = true;
    			break;
    		}
    	}
    	
    	assertTrue("Preinstalled behaviour was missing", installed);
    	
    	eventBus.deliver("com.paremus.brain.iot.example.sensor.api.SensorReadingDTO", Collections.emptyMap());
    	
    	ManagementResponseDTO response;
    	List<String> bundles;
    	
    	response = queue.poll(10, TimeUnit.SECONDS);
    	assertNotNull(response);
    	assertEquals(BID, response.code);
    	assertEquals("com.paremus.brain.iot.example.light.impl", response.symbolicName);
    	assertEquals("0.0.1.SNAPSHOT", response.version);
    	
    	response = queue.poll(60, TimeUnit.SECONDS);
    	bundles = TestUtils.listBundles(context);
    	
    	assertTrue(response != null);
    	assertEquals(ManagementResponseDTO.ResponseCode.INSTALL_OK, response.code);
    	assertTrue(bundles.stream().anyMatch(s -> s.contains("example.light.impl")));
    	
    	pause("test last resort");
    }

    @Test
    public void testInstallationRemovedOnBMSStop() throws Exception {
    	
    	// This is the "official" example marketplace
    	configureBMSAndInstaller("https://nexus.repository-pert.ismb.it/repository/marketplaces/com.paremus.brain.iot.marketplace/security-light-marketplace/0.0.1-SNAPSHOT/index.xml",
    			"com.paremus.brain.iot.example.behaviour.impl:0.0.1.SNAPSHOT");
    	
    	boolean installed = false;
    	for(int i = 0; i < 60; i++) {
    		if(installer.listInstalledFunctions().isEmpty()) {
    			Thread.sleep(1000);
    		} else {
    			installed = true;
    			break;
    		}
    	}
    	
    	assertTrue("Preinstalled behaviour was missing", installed);
    	
    	Optional<Bundle> bundle = Arrays.stream(context.getBundles())
    		.filter(b -> "com.paremus.brain.iot.example.behaviour.impl".equals(b.getSymbolicName()))
    		.findFirst();
    	
    	assertTrue(bundle.isPresent());
    	
    	long id = bundle.get().getBundleId();

    	Optional<Bundle> installerBundle = Arrays.stream(context.getBundles())
    			.filter(b -> "com.paremus.brain.iot.installer.impl".equals(b.getSymbolicName()))
    			.findFirst();
    	
    	assertTrue(bundle.isPresent());
    	
    	installerBundle.get().stop();
    	
    	assertNull(context.getBundle(id));
    	
    	installerBundle.get().start();
    	
    	// Call setup again to reset the dependencies for teardown
    	setUp();
    	
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
