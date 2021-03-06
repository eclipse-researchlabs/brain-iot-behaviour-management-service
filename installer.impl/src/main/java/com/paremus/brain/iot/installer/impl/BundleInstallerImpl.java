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

import static org.osgi.framework.Bundle.INSTALLED;
import static org.osgi.framework.Bundle.RESOLVED;
import static org.osgi.framework.Constants.FRAMEWORK_UUID;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.log.FormatterLogger;
import org.osgi.service.log.LoggerFactory;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.PromiseFactory;

import com.paremus.brain.iot.installer.impl.InstallRequest.InstallAction;

import aQute.bnd.header.Parameters;
import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.resource.CapReqBuilder;
import aQute.bnd.repository.osgi.OSGiRepository;
import eu.brain.iot.eventing.api.EventBus;
import eu.brain.iot.installer.api.FunctionInstaller;
import eu.brain.iot.installer.api.InstallResolver;
import eu.brain.iot.installer.api.InstallResponseDTO;
import eu.brain.iot.installer.api.InstallResponseDTO.ResponseCode;

//Be immediate so we don't stop processing queued events if the service is released
@Component(configurationPid="eu.brain.iot.BundleInstallerService", immediate=true)
@Designate(ocd=BundleInstallerImpl.Config.class)
public class BundleInstallerImpl implements FunctionInstaller {

    @Reference(service = LoggerFactory.class, cardinality = ReferenceCardinality.OPTIONAL)
    private FormatterLogger log;

    @Reference
    private EventBus eventBus;

    @Reference
    private InstallResolver resolver;

    @Reference
    private FrameworkInstaller installer;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private final BlockingQueue<InstallRequest> queue = new LinkedBlockingQueue<>();

    private final PromiseFactory promiseFactory = new PromiseFactory(null);

    private BundleContext context;

    private Thread thread;

	private File httpCacheDir;

	@ObjectClassDefinition(
        name = "Bundle Installer",
        description = "Configuration for the Bundle Installer"
    )
    public @interface Config {
		@AttributeDefinition(description="The cache folder for downloaded bundles and indexes")
		String cache_location() default "";
    }


    @Activate
    void activate(Config config, BundleContext context) throws IOException, Exception {
        this.context = context;
        httpCacheDir = context.getDataFile("httpcache");

        start();
    }

    // also called by test
    synchronized void start() {
        thread = new InstallerThread();
        thread.start();
    }
    
    @Modified
    synchronized void modified() {
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
    }

    @Override
	public Promise<InstallResponseDTO> installFunction(String symbolicName, String version, List<String> indexes,
			List<String> requirements, HttpClient client) {
		Deferred<InstallResponseDTO> response = promiseFactory.deferred();
		
		try {
			InstallRequest dto = new InstallRequest();
			dto.action = InstallAction.INSTALL;
			if(indexes != null) dto.indexes.addAll(indexes);
			if(requirements != null) dto.requirements.addAll(requirements);
			dto.response = response;
			dto.sponsor = symbolicName + ":" + ((version == null) ? "0.0.0" : version);
			dto.client = client;
			
			queue.add(dto);
		} catch (Exception e) {
			response.fail(e);
		}
		
		return response.getPromise();
	}

    @Override
    public Promise<InstallResponseDTO> updateFunction(String oldSymbolicName, String oldVersion, 
    		String newSymbolicName, String newVersion, List<String> indexes, 
    		List<String> requirements, HttpClient client) {
    	Deferred<InstallResponseDTO> response = promiseFactory.deferred();
    	
    	try {
    		InstallRequest dto = new InstallRequest();
    		dto.action = InstallAction.UPDATE;
    		if(indexes != null) dto.indexes.addAll(indexes);
			if(requirements != null) dto.requirements.addAll(requirements);
    		dto.response = response;
    		dto.oldSponsor = oldSymbolicName + ":" + ((oldVersion == null) ? "0.0.0" : oldVersion);
    		dto.sponsor = newSymbolicName + ":" + ((newVersion == null) ? "0.0.0" : newVersion);
    		dto.client = client;
    		
    		queue.add(dto);
    	} catch (Exception e) {
    		response.fail(e);
    	}
    	
    	return response.getPromise();
    }

	@Override
	public Promise<InstallResponseDTO> uninstallFunction(String symbolicName, String version) {
		Deferred<InstallResponseDTO> response = promiseFactory.deferred();
		
		try {
			InstallRequest dto = new InstallRequest();
			dto.action = InstallAction.UNINSTALL;
			dto.response = response;
			dto.sponsor = symbolicName + ":" + version;
			
			queue.add(dto);
		} catch (Exception e) {
			response.fail(e);
		}
		
		return response.getPromise();
	}

	@Override
	public Map<String, String> listInstalledFunctions() {
		return installer.getSponsors().stream()
				.map(o -> String.valueOf(o).split(":", 2))
				.collect(Collectors.toMap(s -> s[0], s -> s.length == 1 ? "0.0.0" : s[1]));
	}

	@Override
	public Promise<InstallResponseDTO> resetNode() {
        info("Resetting the node %s", context.getProperty(FRAMEWORK_UUID));
		Deferred<InstallResponseDTO> response = promiseFactory.deferred();
		
		try {
			InstallRequest dto = new InstallRequest();
			dto.action = InstallAction.RESET;
			dto.response = response;

            info("Adding reset request to queue");
			queue.add(dto);
		} catch (Exception e) {
			response.fail(e);
		}
		
		return response.getPromise();
	}

    // package access for Mockito
    void sendResponse(ResponseCode code, String message, InstallRequest request) {
        info("sendResponse: code=%s message=%s\n", code, message);
        // Force a thread switch to release the installer thread
        promiseFactory.executor().execute(() ->
        	request.response.resolve(InstallerUtils.createResponse(code, Collections.singletonList(message), request)));
    }

    private void sendResponse(ResponseCode code, List<String> messages, InstallRequest request) {
        info("sendResponse: code=%s messages=%s\n", code, messages);
        // Force a thread switch to release the installer thread
        promiseFactory.executor().execute(() ->
    		request.response.resolve(InstallerUtils.createResponse(code, messages, request)));
    }

    private List<String> uninstall(InstallRequest request) throws Exception {
        List<Bundle> uninstalled = new ArrayList<>();

        if (request.action.equals(InstallRequest.InstallAction.UNINSTALL)) {
            String sponsor = request.sponsor;
            if (sponsor == null || sponsor.isEmpty()) {
                throw new BadRequestException("deployment symbolic name not set");
            }

            info("Uninstalling %s", sponsor);

            uninstalled.addAll(installer.removeSponsor(sponsor));
        } else {
            Set<Object> sponsors = installer.getSponsors();
            info("Resetting node %s by removing %s", context.getProperty(FRAMEWORK_UUID), sponsors);

            for(Object sponsor : sponsors) {
                uninstalled.addAll(installer.removeSponsor(sponsor));
            }
        }

        return uninstalled.stream().map(b -> b.toString()).collect(Collectors.toList());
    }

    private List<String> install(InstallRequest request) throws Exception {
        final boolean update = request.action.equals(InstallRequest.InstallAction.UPDATE);
        final String sponsor = request.sponsor;

        if (sponsor == null || sponsor.isEmpty()) {
            throw new BadRequestException("symbolic name not set");
        }

        List<Requirement> requirements = getRequirements(request);

        if (requirements.isEmpty()) {
            throw new BadRequestException("no requirements in request");
        }

        debug("Requirements: " + requirements);

        List<OSGiRepository> indexes = getRepositories(request);
        // resolve the request
        Map<Resource, String> resolve;
        try {
			resolve = resolver.resolveInitial(sponsor, indexes, requirements);
        } finally {
        	for(OSGiRepository r : indexes) {
        		r.close();
        	}
        }
        List<String> locations = new ArrayList<>(resolve.values());

        debug("Resolution size: %d", resolve.size());

        final String oldSponsor = request.oldSponsor;

        if (resolve.size() == 0) {
            return Collections.singletonList(sponsor + " is already installed");
        }

        List<String> oldLocs = installer.getLocations(oldSponsor);

        // if any error occurs, replay rollbacks to restore framework state
        List<Callable<Void>> rollbacks = new LinkedList<>();

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
                    List<Bundle> bundles = installer.addLocations(oldSponsor, oldLocs, request.client);
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

            List<Bundle> installed = installer.addLocations(sponsor, locations, request.client);

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

    private List<OSGiRepository> getRepositories(InstallRequest request) throws Exception  {

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

    	Processor processor = new Processor();
    	processor.addBasicPlugin(request.client);
    	
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

    List<Requirement> getRequirements(InstallRequest request) throws BadRequestException {
        List<Requirement> requirements = new ArrayList<>();

        try {
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

    Object[] fixArgs(Object... args) {
        // Felix log bug: null elements of args are removed, resulting in MissingFormatArgumentException
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null)
                    args[i] = "null";
            }
        }
        return args;
    }

    void debug(String format, Object... args) {
        if (log != null) {
            // FIXME: SCR is logging DEBUG on our loggers!
            log.info(format, fixArgs(args));
        } else {
            System.err.printf("BI:DEBUG:" + format + "\n", args);
        }
    }

    void info(String format, Object... args) {
        if (log != null) {
            log.info(format, fixArgs(args));
        } else {
            System.err.printf("BI:INFO:" + format + "\n", args);
        }
    }

    void warn(String format, Object... args) {
        if (log != null) {
            log.warn(format, fixArgs(args));
        } else {
            System.err.printf("BI:WARN:" + format + "\n", args);
        }
    }

    private class InstallerThread extends Thread {

        public InstallerThread() {
            super("BRAIN-IoT BundleInstaller Thread");
        }

        @Override
        public void run() {
            while (running.get()) {
                InstallRequest request = null;

                try {
                    request = queue.take();
                    debug("\n\nRequest: action=%s sponsor=%s",
                            request.action, request.sponsor);

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
                        warn("request %s failed: %s", request.action, e.toString(), e);
                        sendResponse(ResponseCode.FAIL, e.toString(), request);
                    }
                }
            }
        }
    }

}
