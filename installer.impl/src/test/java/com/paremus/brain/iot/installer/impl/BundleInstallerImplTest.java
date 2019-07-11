/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.paremus.brain.iot.installer.impl;

import eu.brain.iot.installer.api.InstallRequestDTO;
import eu.brain.iot.installer.api.InstallRequestDTO.InstallAction;
import eu.brain.iot.installer.api.InstallResponseDTO.ResponseCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.osgi.resource.Requirement;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class BundleInstallerImplTest {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    BundleInstallerImpl impl;
    Semaphore semA;

    @Before
    public void start() {
        impl = new BundleInstallerImpl();
        impl = Mockito.spy(impl);

        semA = new Semaphore(0);

        Mockito.doAnswer(i -> {
            semA.release();
            return null;
        }).when(impl).sendResponse(Mockito.any(), Mockito.anyString(), Mockito.any());

        impl.start();
    }

    @After
    public void stop() {
        impl.stop();
    }

    @Test
    public void testParseRequireBundle() throws BadRequestException {
        Map<String, String> bundles = new LinkedHashMap<>();
        bundles.put("foo", "1.0.0");
        bundles.put("bar", "[1.0,1.1)");
        bundles.put("baz", "[2.0,3.0)");
        bundles.put("fnarg", null);

        InstallRequestDTO request = new InstallRequestDTO();
        request.bundles = bundles;

        List<Requirement> actual = impl.getRequirements(request);

        assertEquals("(&(osgi.wiring.bundle=foo)(bundle-version>=1.0.0))", actual.get(0).getDirectives().get("filter"));
        assertEquals("(&(osgi.wiring.bundle=bar)(bundle-version>=1.0.0)(!(bundle-version>=1.1.0)))", actual.get(1).getDirectives().get("filter"));
        assertEquals("(&(osgi.wiring.bundle=baz)(bundle-version>=2.0.0)(!(bundle-version>=3.0.0)))", actual.get(2).getDirectives().get("filter"));
        assertEquals("(osgi.wiring.bundle=fnarg)", actual.get(3).getDirectives().get("filter"));
    }

    @Test
    public void testIndexesMissing() throws InterruptedException {
        InstallRequestDTO event = createRequest("test.IndexesMissing", InstallAction.INSTALL, null);
        impl.notify(event);
        assertTrue(semA.tryAcquire(1, TimeUnit.SECONDS));
        Mockito.verify(impl).sendResponse(Mockito.eq(ResponseCode.BAD_REQUEST), Mockito.contains("no indexes"), Mockito.any());
    }

    @Test
    public void testIndexesInvalid() throws InterruptedException {
        InstallRequestDTO event = createRequest("test.IndexesInvalid", InstallAction.INSTALL,
                Arrays.asList(new String[]{"c:\\invalid"}));
        impl.notify(event);
        assertTrue(semA.tryAcquire(1, TimeUnit.SECONDS));
        Mockito.verify(impl).sendResponse(Mockito.eq(ResponseCode.BAD_REQUEST), Mockito.contains("invalid URI"), Mockito.any());
    }

    @Test
    public void testBadRequest() throws InterruptedException {
        Map<String, String> bundles = new HashMap<>();

        InstallRequestDTO event = createRequest("test.BadRequest", InstallAction.INSTALL,
                Arrays.asList(new String[]{"http://brain-iot.org/index.xml"}), bundles);

        bundles.put("wibble", "a.2.3.q");

        impl.notify(event);
        assertTrue(semA.tryAcquire(1, TimeUnit.SECONDS));
        Mockito.verify(impl).sendResponse(Mockito.eq(ResponseCode.BAD_REQUEST), Mockito.contains("invalid range"), Mockito.any());

        bundles.put("wibble", "[1.2.3,1.2.4(");
        Mockito.clearInvocations(impl);

        impl.notify(event);
        assertTrue(semA.tryAcquire(1, TimeUnit.SECONDS));
        Mockito.verify(impl).sendResponse(Mockito.eq(ResponseCode.BAD_REQUEST), Mockito.contains("invalid range"), Mockito.any());
    }

    private static InstallRequestDTO createRequest(String sn, InstallAction action, List<String> indexes, Map<String, String> bundles) {
        InstallRequestDTO request = new InstallRequestDTO();
        request.symbolicName = sn;
        request.action = action;
        request.indexes = indexes;
        request.bundles = bundles;
        return request;
    }

    private static InstallRequestDTO createRequest(String sn, InstallAction action, List<String> indexes, String... nvs) {
        if (nvs.length % 2 != 0) {
            throw new IllegalArgumentException("odd number of name/version pairs");
        }

        Map<String, String> bundles = new HashMap<>();
        for (int i = 0; i < nvs.length; i += 2) {
            bundles.put(nvs[i], nvs[i + 1]);
        }

        return createRequest(sn, action, indexes, bundles);
    }

}
