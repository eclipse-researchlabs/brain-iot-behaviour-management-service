/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.paremus.brain.iot.installer.impl;

import static eu.brain.iot.installer.api.InstallRequestDTO.InstallAction.UNINSTALL;
import static eu.brain.iot.installer.api.InstallRequestDTO.InstallAction.UPDATE;
import static org.osgi.framework.Bundle.INSTALLED;
import static org.osgi.framework.Bundle.RESOLVED;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.VersionRange;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.log.FormatterLogger;
import org.osgi.service.log.LoggerFactory;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import aQute.bnd.header.Parameters;
import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.repository.osgi.OSGiRepository;
import eu.brain.iot.eventing.annotation.SmartBehaviourDefinition;
import eu.brain.iot.eventing.api.EventBus;
import eu.brain.iot.eventing.api.SmartBehaviour;
import eu.brain.iot.installer.api.InstallRequestDTO;
import eu.brain.iot.installer.api.InstallResolver;
import eu.brain.iot.installer.api.InstallResponseDTO.ResponseCode;

@Component(configurationPid="eu.brain.iot.BundleInstallerService")
@Designate(ocd=BundleInstallerImpl.Config.class)
@SmartBehaviourDefinition(consumed = {InstallRequestDTO.class},
        author = "Paremus", name = "[Brain-IoT] Bundle Installer Service",
        description = "Resolves requirements using supplied indexes and installs all dependencies."
)
public class BundleInstallerImpl implements SmartBehaviour<InstallRequestDTO> {

    @Reference(service = LoggerFactory.class, cardinality = ReferenceCardinality.OPTIONAL)
    private FormatterLogger log;

    @Reference
    private EventBus eventBus;

    @Reference
    private InstallResolver resolver;

    @Reference
    private FrameworkInstaller installer;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private final BlockingQueue<InstallRequestDTO> queue = new LinkedBlockingQueue<>();

    private final Map<String, String> sponsors = new HashMap<>();

    private BundleContext context;

    private Thread thread;

	private HttpClient client;

	private Processor processor;

	private File httpCacheDir;

	@ObjectClassDefinition(
        name = "Bundle Installer",
        description = "Configuration for the Bundle Installer"
    )
    public @interface Config {
		@AttributeDefinition(description="Connection settings file for index and bundle download", defaultValue="")
    	public String connection_settings() default "";
    }


    @Activate
    void activate(Config config, BundleContext context) throws IOException, Exception {
        this.context = context;
        httpCacheDir = context.getDataFile("httpcache");

        processor = new Processor();
        processor.set(Processor.CONNECTION_SETTINGS, config.connection_settings());

    	client = new HttpClient();
		client.setReporter(processor);
		client.setRegistry(processor);
		client.setCache(httpCacheDir);

		client.readSettings(processor);

		processor.addBasicPlugin(client);
        start();
    }

    // also called by test
    synchronized void start() {
        thread = new InstallerThread();
        thread.start();
    }

    @Deactivate
    synchronized void stop() {
        Thread thread = this.thread;
        this.thread = null;

        running.set(false);
        thread.interrupt();

        try {
            thread.join(2000);
        } catch (InterruptedException e) {
        }

        client.close();
    }

    @Override
    public void notify(InstallRequestDTO event) {
        queue.add(event);
    }

    // package access for Mockito
    void sendResponse(ResponseCode code, String message, InstallRequestDTO request) {
        if (log != null)
            log.info("sendResponse: code=%s message=%s\n", code, message);
        eventBus.deliver(InstallerUtils.createResponse(code, Collections.singletonList(message), request));
    }

    private void sendResponse(ResponseCode code, List<String> messages, InstallRequestDTO request) {
        if (log != null)
            log.info("sendResponse: code=%s messages=%s\n", code, messages);
        eventBus.deliver(InstallerUtils.createResponse(code, messages, request));
    }

    private List<String> uninstall(InstallRequestDTO request) throws Exception {
        List<Bundle> uninstalled = new ArrayList<>();

        if (request.action.equals(UNINSTALL)) {
            String symbolicName = request.symbolicName;
            if (symbolicName == null || symbolicName.isEmpty()) {
                throw new BadRequestException("deployment symbolic name not set");
            }

            String sponsor = sponsors.remove(symbolicName);
            if (sponsor != null) {
                uninstalled.addAll(installer.removeSponsor(sponsor));
            }
        } else {
            Iterator<String> iterator = sponsors.values().iterator();

            while (iterator.hasNext()) {
                String sponsor = iterator.next();
                iterator.remove();
                List<Bundle> removed = installer.removeSponsor(sponsor);
                uninstalled.addAll(removed);
            }
        }

        return uninstalled.stream().map(b -> b.toString()).collect(Collectors.toList());
    }

    private List<String> install(InstallRequestDTO request) throws Exception {
        final boolean update = request.action.equals(UPDATE);
        String name = request.name;
        String symbolicName = request.symbolicName;
        String version = request.version;

        if (symbolicName == null || symbolicName.isEmpty()) {
            throw new BadRequestException("symbolic name not set");
        }

        if (name == null || name.isEmpty())
            name = symbolicName;

        if (version == null || version.isEmpty())
            version = "0";

        List<Requirement> requirements = getRequirements(request);

        if (requirements.isEmpty()) {
            throw new BadRequestException("no requirements in request");
        }

        debug("Requirements: " + requirements);

        List<OSGiRepository> indexes = getRepositories(request);
        // resolve the request
        Map<Resource, String> resolve;
        try {
			resolve = resolver.resolveInitial(name, indexes, requirements);
        } finally {
        	for(OSGiRepository r : indexes) {
        		r.close();
        	}
        }
        List<String> locations = new ArrayList<>(resolve.values());

        debug("Resolution size: %d", resolve.size());


        final String sponsor = request.symbolicName + '-' + request.version;
        final String oldSponsor = sponsors.put(request.symbolicName, sponsor);

        if (resolve.size() == 0) {
            return Collections.singletonList(sponsor + " is already installed");
        }

        List<String> oldLocs = installer.getLocations(oldSponsor);

        // if any error occurs, replay rollbacks to restore framework state
        List<Callable<Void>> rollbacks = new LinkedList<>();

        rollbacks.add(0, () -> {
            debug("ROLLBACK sponsor");
            sponsors.put(request.symbolicName, oldSponsor);
            return null;
        });

        try {
            if (update) {
                rollbacks.add(0, () -> {
                    debug("ROLLBACK stop");
                    for (String loc : oldLocs) {
                        Bundle b = context.getBundle(loc);
                        if (b != null && !isFragment(b)) {
                            b.start();
                        }
                    }
                    return null;
                });

                debug("STOP: %s", oldLocs.stream().map(l ->
                        l.replaceFirst(".*/", "")).collect(Collectors.toList()));

                for (String loc : oldLocs) {
                    Bundle b = context.getBundle(loc);
                    if (b != null) {
                        b.stop();
                    }
                }
            } else {
                rollbacks.add(0, () -> {
                    debug("ROLLBACK uninstall");
                    List<Bundle> bundles = installer.addLocations(oldSponsor, oldLocs, client);
                    for (Bundle b : bundles) {
                        if (!isFragment(b)) {
                            b.start();
                        }
                    }
                    return null;
                });

                List<Bundle> uninstalled = installer.removeSponsor(oldSponsor);
                debug("UNINSTALLED: %s", uninstalled);
            }

            rollbacks.add(0, () -> {
                debug("ROLLBACK install");
                installer.removeSponsor(sponsor);
                return null;
            });

            List<Bundle> installed = installer.addLocations(sponsor, locations, client);

            for (Bundle b : installed) {
                if (!isFragment(b)) {
                    debug("START %s", b);
                    b.start();
                }
            }

            if (update) {
                for (String loc : locations) {
                    Bundle b = context.getBundle(loc);
                    if (b != null && !isFragment(b)) {
                        switch (b.getState()) {
                            case INSTALLED:
                            case RESOLVED:
                                debug("RESTART %s", b);
                                b.start();
                        }
                    }
                }

                // update OK, we don't want to rollback
                rollbacks.clear();

                List<Bundle> uninstalled = installer.removeSponsor(oldSponsor);
                debug("UNINSTALLED: %s", uninstalled);
            }

            return installed.stream().map(b -> b.toString()).collect(Collectors.toList());
        } catch (Exception e) {
            try {
                for (Callable<Void> c : rollbacks) {
                    c.call();
                }
            } catch (Exception r) {
                throw new Exception("rollback failed: " + r, e);
            }
            throw e;
        }
    }


    private static boolean isFragment(Bundle bundle) {
        return (bundle.adapt(BundleRevision.class).getTypes() & BundleRevision.TYPE_FRAGMENT) > 0;
    }

    private List<OSGiRepository> getRepositories(InstallRequestDTO request) throws Exception  {

    	if (request.indexes == null || request.indexes.isEmpty()) {
            throw new BadRequestException("no indexes in request");
        }

    	List<URI> indexes = new ArrayList<>();
    	try {
            for (String index : request.indexes) {
                indexes.add(new URI(index));
            }
        } catch (URISyntaxException e) {
            throw new BadRequestException("indexes contains invalid URI: " + e);
        }

		List<OSGiRepository> repositories = new ArrayList<>(indexes.size());
		for(URI index : indexes) {

			OSGiRepository repo = new OSGiRepository();
			repo.setReporter(processor);
			repo.setRegistry(processor);

			Map<String, String> props = new HashMap<>();
			props.put("name", "Repository for " + index);
			props.put("locations", index.toString());
			props.put("cache", httpCacheDir.getAbsolutePath());

			repo.setProperties(props);

			repositories.add(repo);
		}

		return repositories;
    }

    List<Requirement> getRequirements(InstallRequestDTO request) throws BadRequestException {
        List<Requirement> requirements = new ArrayList<>();

        try {
            if (request.bundles != null) {
                for (Map.Entry<String, String> entry : request.bundles.entrySet()) {
                    String name = entry.getKey();
                    String version = entry.getValue();
                    VersionRange range = null;

                    if (version != null && !version.isEmpty()) {
                        range = VersionRange.valueOf(version);
                    }

                    requirements.add(InstallerUtils.bundleRequirement(name, range));
                }
            }

            if (request.requirements != null) {
                for (String req : request.requirements) {
                	Parameters p = new Parameters(req);
                    requirements.addAll(CapReqBuilder.getRequirementsFrom(p));
                }
            }
        } catch (Exception e) {
            throw new BadRequestException("invalid requirement: " + e);
        }

        return requirements;
    }

    void debug(String format, Object... args) {
        if (log != null) {
            // FIXME: SCR is logging DEBUG on our loggers!
            log.info(format, args);
        } else {
            System.err.printf("BI:DEBUG:" + format + "\n", args);
        }
    }

    private class InstallerThread extends Thread {

        public InstallerThread() {
            super("BRAIN-IoT BundleInstaller Thread");
        }

        @Override
        public void run() {
            while (running.get()) {
                InstallRequestDTO request = null;

                try {
                    request = queue.take();
                    debug("\n\nRequest: action=%s name=%s(%s) version=%s",
                            request.action, request.name, request.symbolicName, request.version);

                    if (request.action == null)
                        throw new BadRequestException("unknown action: null");

                    switch (request.action) {
                        case INSTALL:
                        case UPDATE:
                            List<String> added = install(request);
                            sendResponse(ResponseCode.SUCCESS, added, request);
                            break;

                        case UNINSTALL:
                        case RESET:
                            List<String> removed = uninstall(request);
                            sendResponse(ResponseCode.SUCCESS, removed, request);
                            break;

                        default:
                            throw new BadRequestException("unknown action: " + request.action);
                    }
                } catch (BadRequestException e) {
                    sendResponse(ResponseCode.BAD_REQUEST, e.getMessage(), request);
                } catch (Exception e) {
                    if (running.get()) {
                        if (log != null)
                            log.warn("request %s failed: %s", request.action, e.toString(), e);
                        sendResponse(ResponseCode.FAIL, e.toString(), request);
                    }
                }
            }
        }
    }

}
