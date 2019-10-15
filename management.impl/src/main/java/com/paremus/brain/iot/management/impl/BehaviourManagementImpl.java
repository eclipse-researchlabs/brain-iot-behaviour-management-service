/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.paremus.brain.iot.management.impl;

import static aQute.bnd.osgi.resource.CapReqBuilder.getRequirementsFrom;
import static com.paremus.brain.iot.management.api.ManagementResponseDTO.ResponseCode.ALREADY_INSTALLED;
import static com.paremus.brain.iot.management.api.ManagementResponseDTO.ResponseCode.BID;
import static eu.brain.iot.behaviour.namespace.SmartBehaviourDeploymentNamespace.CAPABILITY_REQUIREMENTS_ATTRIBUTE;
import static eu.brain.iot.behaviour.namespace.SmartBehaviourDeploymentNamespace.CONTENT_MIME_TYPE_INDEX;
import static eu.brain.iot.behaviour.namespace.SmartBehaviourDeploymentNamespace.IDENTITY_TYPE_SMART_BEHAVIOUR;
import static eu.brain.iot.behaviour.namespace.SmartBehaviourDeploymentNamespace.SMART_BEHAVIOUR_DEPLOYMENT_NAMESPACE;
import static eu.brain.iot.behaviour.namespace.SmartBehaviourNamespace.SMART_BEHAVIOUR_NAMESPACE;
import static java.lang.String.format;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.osgi.framework.namespace.IdentityNamespace.CAPABILITY_TYPE_ATTRIBUTE;
import static org.osgi.framework.namespace.IdentityNamespace.CAPABILITY_VERSION_ATTRIBUTE;
import static org.osgi.framework.namespace.IdentityNamespace.IDENTITY_NAMESPACE;
import static org.osgi.service.repository.ContentNamespace.CAPABILITY_MIME_ATTRIBUTE;
import static org.osgi.service.repository.ContentNamespace.CAPABILITY_URL_ATTRIBUTE;
import static org.osgi.service.repository.ContentNamespace.CONTENT_NAMESPACE;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.propertytypes.ExportedService;
import org.osgi.service.log.FormatterLogger;
import org.osgi.service.log.LoggerFactory;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

import com.paremus.brain.iot.management.api.BehaviourManagement;
import com.paremus.brain.iot.management.api.ManagementBidRequestDTO;
import com.paremus.brain.iot.management.api.ManagementDTO;
import com.paremus.brain.iot.management.api.ManagementInstallRequestDTO;
import com.paremus.brain.iot.management.api.ManagementInstallRequestDTO.ManagementInstallAction;
import com.paremus.brain.iot.management.api.ManagementResponseDTO;

import aQute.bnd.header.Parameters;
import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.Processor;
import aQute.bnd.osgi.resource.ResourceUtils;
import aQute.bnd.repository.osgi.OSGiRepository;
import eu.brain.iot.eventing.annotation.SmartBehaviourDefinition;
import eu.brain.iot.eventing.api.EventBus;
import eu.brain.iot.eventing.api.SmartBehaviour;
import eu.brain.iot.eventing.api.UntypedSmartBehaviour;
import eu.brain.iot.installer.api.BehaviourDTO;
import eu.brain.iot.installer.api.FunctionInstaller;
import eu.brain.iot.installer.api.InstallResolver;
import eu.brain.iot.installer.api.InstallResponseDTO;
import eu.brain.iot.installer.api.InstallResponseDTO.ResponseCode;

@Component(configurationPid = BehaviourManagementImpl.PID,
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
@ExportedService(service_exported_interfaces=BehaviourManagement.class)
@Designate(ocd=BehaviourManagementImpl.Config.class)
@SmartBehaviourDefinition(consumed = {ManagementBidRequestDTO.class},
        author = BehaviourManagementImpl.BEHAVIOUR_AUTHOR,
        name = BehaviourManagementImpl.BEHAVIOUR_NAME,
        description = "Implements the Behaviour Management Service")
public class BehaviourManagementImpl implements SmartBehaviour<ManagementBidRequestDTO>, BehaviourManagement {

	private static final String IDENTITY_FILTER = "osgi.identity;filter:=\"(&(osgi.identity=%s)(version=%s))\"";
	private static final String SMART_BEHAVIOUR_FILTER = SMART_BEHAVIOUR_NAMESPACE +
			";filter:=\"(consumed=%s)\"";


	static final String BEHAVIOUR_AUTHOR = "Paremus";
    static final String BEHAVIOUR_NAME = "[Brain-IoT] Behaviour Management Service";

    static final String EVENT_SERVICE_PROPERTY_PREFIX = "eu.brain.iot.behaviour.";

    static final String PID = "eu.brain.iot.BehaviourManagementService";

    @ObjectClassDefinition(
        name = "Behaviour Management Service",
        description = "Configuration for the Behaviour Management Service"
    )
    public @interface Config {
    	@AttributeDefinition(description="The Marketplace indexes for installing Smart Behaviours")
        String[] indexes();
        @AttributeDefinition(description="Connection settings file for index and bundle download", defaultValue="")
    	public String connection_settings() default "";
    }

    class UntypedEvent {
        String eventType;
        Map<String, Object> eventData;
    }

    @Reference(service = LoggerFactory.class, cardinality = ReferenceCardinality.OPTIONAL)
    private FormatterLogger log;

    @Reference
    private EventBus eventBus;

    @Reference
    private InstallResolver resolver;
    
    @Reference
    private FunctionInstaller installer;

    @Reference
    ConfigurationAdmin configAdmin;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private final BlockingQueue<ManagementDTO> queue = new LinkedBlockingQueue<>();

    private final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();

    private final Map<String, List<UntypedEvent>> inProgress = new ConcurrentHashMap<>();
    private final Map<String, Set<ManagementResponseDTO>> bidResponses = new ConcurrentHashMap<>();
    private final Map<String, String> pendingInstall = new ConcurrentHashMap<>();

    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    private List<URI> indexes = new ArrayList<>();

    private Config config;

    private Thread thread;

    private String myNode;

    private List<ServiceRegistration<?>> registrations = new ArrayList<ServiceRegistration<?>>();

	private Processor processor;
	private HttpClient client;

	private OSGiRepository repository;
	private File httpCacheDir;

    @Activate
    private void activate(BundleContext context, Config config) throws Exception {
		debug("activate");
        myNode = context.getProperty(Constants.FRAMEWORK_UUID);
        httpCacheDir = context.getDataFile("httpcache");


        processor = new Processor();
    	processor.set(Processor.CONNECTION_SETTINGS, config.connection_settings());

    	client = new HttpClient();
    	client.setReporter(processor);
    	client.setRegistry(processor);
    	client.setCache(httpCacheDir);

    	client.readSettings(processor);

		processor.addBasicPlugin(client);

        // configure our consumers to only accept responses to our requests
        Hashtable<String, Object> baseProps = new Hashtable<>();
        baseProps.put(EVENT_SERVICE_PROPERTY_PREFIX + "author", BEHAVIOUR_AUTHOR);
        baseProps.put(EVENT_SERVICE_PROPERTY_PREFIX + "name", BEHAVIOUR_NAME);


        Hashtable<String, Object> props = new Hashtable<>(baseProps);
        props.put(EVENT_SERVICE_PROPERTY_PREFIX + "description", "Install request consumer");
        props.put(EVENT_SERVICE_PROPERTY_PREFIX + "consumed", ManagementInstallRequestDTO.class.getName());
        props.put(EVENT_SERVICE_PROPERTY_PREFIX + "filter", String.format("(targetNode=%s)", myNode));

        registrations.add(context.registerService(SmartBehaviour.class,
        		new InstallRequestConsumer(this), props));

        props = new Hashtable<>(baseProps);
        props.put(EVENT_SERVICE_PROPERTY_PREFIX + "description", "Management response consumer");
        props.put(EVENT_SERVICE_PROPERTY_PREFIX + "consumed", ManagementResponseDTO.class.getName());

        registrations.add(context.registerService(SmartBehaviour.class,
        		new ManagementResponseConsumer(this), props));

        props = new Hashtable<>(baseProps);
        props.put(EVENT_SERVICE_PROPERTY_PREFIX + "description", "Unhandled Event consumer");
        props.put(EVENT_SERVICE_PROPERTY_PREFIX + "consumer.of.last.resort", true);

        registrations.add(context.registerService(UntypedSmartBehaviour.class,
        		new LastResortConsumer(this), props));

        modified(config);
        start();
    }

    private synchronized void start() {
        thread = new Thread(new QueueProcessor(), "BRAIN-IoT Behaviour Management");
        thread.start();

        sched.scheduleAtFixedRate(new BidTracker(), 1, 1, TimeUnit.SECONDS);
    }

    @Modified
    private synchronized void modified(Config cfg) throws Exception {
        this.config = cfg;

        List<URI> indexes = new ArrayList<>();
        for (String index : config.indexes()) {
            indexes.add(URI.create(index));
        }

        // debug to diagnose too many calls to modified()
        if (!indexes.equals(this.indexes)) {
            debug("modified: " + indexes);
            this.indexes = indexes;

            OSGiRepository repo = loadIndex("Bundle Management Marketplaces", indexes);

            shutdownOldRepo();

    		this.repository = repo;
        }
        else {
            debug("modified: indexes is unchanged!");
        }
    }

    private OSGiRepository loadIndex(String name, List<URI> indexes) throws Exception {

		OSGiRepository repo = new OSGiRepository();
		repo.setReporter(processor);
		repo.setRegistry(processor);

		Map<String, String> props = new HashMap<>();
		props.put("name", name);
		props.put("locations", indexes.stream().map(URI::toString).collect(Collectors.joining(",")));
		props.put("cache", httpCacheDir.getAbsolutePath());

		repo.setProperties(props);

		return repo;
	}

	private void shutdownOldRepo() {
		if(repository != null) {
			try {
				repository.close();
			} catch (Exception e) {
				// Nothing to see here
			}
		}
	}

	@Deactivate
    private synchronized void stop() {
        debug("deactivate");

        for(ServiceRegistration<?> reg : registrations) {
        	try {
        		reg.unregister();
        	} catch (IllegalStateException ise) {
        		// Just keep going
        	}
        }

        Thread thread = this.thread;
        this.thread = null;

        sched.shutdownNow();

        running.set(false);
        thread.interrupt();

        try {
            thread.join(2000);
        } catch (InterruptedException e) {
        }
        shutdownOldRepo();

        client.close();
    }

    @Override
    public Collection<BehaviourDTO> findBehaviours(String ldapFilter) throws Exception {

    	Requirement req = repository.newRequirementBuilder(SMART_BEHAVIOUR_NAMESPACE)
    				.addDirective("filter", ldapFilter)
    				.build();
		return repository.findProviders(Collections.singleton(req)).entrySet().stream()
			.flatMap(e -> e.getValue().stream())
			.map(this::newBehaviour)
			.collect(toList());
    }

    private BehaviourDTO newBehaviour(Capability cap) {
        BehaviourDTO dto = new BehaviourDTO();
        Resource resource = cap.getResource();
		Capability idCap = resource.getCapabilities(IDENTITY_NAMESPACE).get(0);

        dto.bundle = ResourceUtils.getIdentity(idCap);
        dto.version = ResourceUtils.getIdentityVersion(resource);

        cap.getAttributes().forEach((k, v) -> {
            switch (k) {
                case "name":
                    dto.name = String.valueOf(v);
                    break;
                case "description":
                    dto.description = String.valueOf(v);
                    break;
                case "author":
                    dto.author = String.valueOf(v);
                    break;
                case "consumed":
                    dto.consumed = String.valueOf(v);
                    break;
            }
        });
        return dto;
    }

    @Override
    public void installBehaviour(BehaviourDTO behaviour, String targetNode) {
        ManagementInstallRequestDTO request = new ManagementInstallRequestDTO();

        request.targetNode = targetNode;
        request.requestIdentity = "install:" + behaviour.bundle + ":" + behaviour.version;
        request.action = ManagementInstallAction.INSTALL;
        request.symbolicName = behaviour.bundle;
        request.version = behaviour.version;

        eventBus.deliver(request);
    }

    @Override
    public void uninstallBehaviour(BehaviourDTO behaviour, String targetNode) {
    	ManagementInstallRequestDTO request = new ManagementInstallRequestDTO();
    	
    	request.targetNode = targetNode;
    	request.requestIdentity = "uninstall:" + behaviour.bundle + ":" + behaviour.version;
    	request.action = ManagementInstallAction.UNINSTALL;
    	request.symbolicName = behaviour.bundle;
        request.version = behaviour.version;
    	
    	eventBus.deliver(request);
    }

    @Override
    public void resetNode(String targetNode) {
    	ManagementInstallRequestDTO request = new ManagementInstallRequestDTO();
    	
    	request.targetNode = targetNode;
    	request.action = ManagementInstallAction.RESET;
    	
    	eventBus.deliver(request);
    }

    @Override
    public void notify(ManagementBidRequestDTO request) {
        queue.add(request);
    }

    void notify(ManagementInstallRequestDTO request) {
        queue.add(request);
    }

    void notify(ManagementResponseDTO response) {
        String requestIdentity = response.requestIdentity;
        switch (response.code) {
            case FAIL:
                break;

            case BID:
                Set<ManagementResponseDTO> bids = bidResponses.get(requestIdentity);
                if (bids != null) {
                    bids.add(response);
                }
                break;

            case ALREADY_INSTALLED:
                warn("consumer for <%s> is already installed on node <%s>", requestIdentity, response.sourceNode);
                bidResponses.remove(requestIdentity);
                inProgress.remove(requestIdentity);
                break;

            case INSTALL_OK:
                blacklist.put(requestIdentity, 0L);   // flag install ok
                List<UntypedEvent> events = inProgress.remove(requestIdentity);

                if (events != null) {
                    info("Resending %d last resort events(%s)", events.size(), requestIdentity);
                    for (UntypedEvent event : events) {
                        eventBus.deliver(event.eventType, event.eventData);
                    }
                }
                break;
        }
    }

    void installComplete(String requestIdentity, InstallResponseDTO response) {
        String target = pendingInstall.remove(requestIdentity);
        if (target == null) {
            debug("Ignore InstallResponse that we did not initiate: %s", requestIdentity);
            return;
        }

        ManagementResponseDTO mr = new ManagementResponseDTO();
        mr.targetNode = target;
        mr.requestIdentity = requestIdentity;

        if (response.code.equals(ResponseCode.SUCCESS)) {
            mr.code = ManagementResponseDTO.ResponseCode.INSTALL_OK;
            eventBus.deliver(mr);

        } else {
            warn("Failed to install requirement for eventType(%s): %s", requestIdentity, response.messages);
            mr.code = ManagementResponseDTO.ResponseCode.FAIL;
            eventBus.deliver(mr);
        }
    }



    void notifyLastResort(String eventType, Map<String, ?> properties) {

    	String identity = "LastResort:" + eventType;
        List<UntypedEvent> pendingEvents = inProgress.get(identity);

        UntypedEvent event = new UntypedEvent();
        event.eventType = eventType;
        event.eventData = new HashMap<String, Object>(properties);

        if (pendingEvents != null) {
            info("Queue Last_Resort event(%s) pending install", eventType);
            pendingEvents.add(event);
        } else if (blacklist.containsKey(identity)) {
            if (blacklist.get(identity) == 0) {
                warn("Event(%s) still not consumed after installing behaviour", eventType);
                blacklist.put(identity, System.currentTimeMillis());
            } else {
                debug("Ignore event(%s) we've already handled", identity);
            }
        } else {
            pendingEvents = new ArrayList<>();

            inProgress.put(identity, pendingEvents);
            pendingEvents.add(event);

            // prepare for bid responses
            bidResponses.put(identity, new HashSet<>());

            ManagementBidRequestDTO request = new ManagementBidRequestDTO();
			request.requestIdentity = identity;
			
			Resource resource = getResourceForRequirement(toRequirementList(format(SMART_BEHAVIOUR_FILTER, eventType)), identity);
			
            request.symbolicName = getSymbolicName(resource, identity);
            request.version = getVersion(resource);
            eventBus.deliver(request);
        }
    }

    private List<Requirement> toRequirementList(String requirements) {
		try {
			return getRequirementsFrom(new Parameters(requirements));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			throw new RuntimeException("Failed to generate requirement list", e);
		}
	}

	private Resource getResourceForRequirement(List<Requirement> requirements, String request) {
	
		List<Resource> found = repository.findProviders(requirements).values().stream()
				.flatMap(Collection::stream)
				.map(Capability::getResource)
				.distinct()
				.collect(toList());
	
		if(found.isEmpty()) {
			// TODO alert no matching resource
			throw new RuntimeException("No resource suitable for " + request);
		} else if (found.size() > 1) {
			// TODO alert too many matching resources
			throw new RuntimeException("Too many resources suitable for " + request);
		}
	
		return found.get(0);
	}

	private String getSymbolicName(Resource res, String requestIdentity) {
		List<Capability> capabilities = res.getCapabilities(IDENTITY_NAMESPACE);
		if(capabilities.isEmpty()) {
			// TODO this will be impossible with updated indexes
			return requestIdentity;
		}
		return (String) capabilities.get(0).getAttributes().get(IDENTITY_NAMESPACE);
	}

	private String getVersion(Resource res) {
		List<Capability> capabilities = res.getCapabilities(IDENTITY_NAMESPACE);
		if(capabilities.isEmpty()) {
			// TODO this will be impossible with updated indexes
			return "0.0.0";
		}
		return String.valueOf(capabilities.get(0).getAttributes().get(CAPABILITY_VERSION_ATTRIBUTE));
	}

	class BidTracker implements Runnable {
        @Override
        public void run() {
            for (String requestIdentity : bidResponses.keySet()) {
                Set<ManagementResponseDTO> bids = bidResponses.get(requestIdentity);
                if (bids == null) continue;
                if (bids.size() == 0) continue;

                List<ManagementResponseDTO> lbids = new ArrayList<>(bids);
                Collections.sort(lbids, (a, b) -> b.bid - a.bid);
                ManagementResponseDTO bestBid = lbids.get(0);

                long millis = System.currentTimeMillis() - bestBid.timestamp.toEpochMilli();

                if (bestBid.bid > 0 || millis > 1000) {
                    bidResponses.remove(requestIdentity); // ignore further bids
                    ManagementInstallRequestDTO request = new ManagementInstallRequestDTO();
                    request.action = ManagementInstallAction.INSTALL;
                    request.targetNode = bestBid.sourceNode;
                    request.requestIdentity = bestBid.requestIdentity;
                    request.symbolicName = bestBid.symbolicName;
                    request.version = bestBid.version;
                    eventBus.deliver(request);
                }
            }
        }
    }

    class QueueProcessor implements Runnable {
        @Override
        public void run() {
            while (running.get()) {
            	ManagementDTO request = null;

                try {
                    request = queue.take();
                    String requestIdentity = request.requestIdentity;

                    // reject any more events of this type
                    blacklist.put(requestIdentity, System.currentTimeMillis());
                    
                    if(request instanceof ManagementInstallRequestDTO) {
                    	ManagementInstallRequestDTO installDTO = (ManagementInstallRequestDTO) request;
                    	pendingInstall.put(requestIdentity, request.sourceNode);
                    	Promise<InstallResponseDTO> p;
                    	switch(installDTO.action) {
							case INSTALL:
								String identityRequirement = String.format(IDENTITY_FILTER, request.symbolicName, request.version);
								
								Resource res = getResourceForRequirement(toRequirementList(identityRequirement), requestIdentity);

			                    List<URI> indexes = getRelevantIndex(res);

			                    String resolveRequirements = resolveRequirementsFor(res, identityRequirement);
			                    
			                    p = installer.installFunction(request.symbolicName, request.version, 
			                    		indexes.stream().map(URI::toString).collect(toList()), singletonList(resolveRequirements));
								break;
							case RESET:
								p = installer.resetNode();
								break;
							case UNINSTALL:
								p = installer.uninstallFunction(request.symbolicName, request.version);
								break;
							case UPDATE:
								// Not yet implemented
								p = Promises.failed(new UnsupportedOperationException("No support for update yet"));
								break;
							default:
								p = Promises.failed(new UnsupportedOperationException("No support for an action " + installDTO.action + " yet"));
								break;
                    	
                    	}
                    	final ManagementDTO req = request; 
                    	p.thenAccept(v -> installComplete(requestIdentity, v))
                    		.onFailure(t -> failedAction(req, t));
                    } else if (request instanceof ManagementBidRequestDTO) {
                    
                    	String identityRequirement = String.format(IDENTITY_FILTER, request.symbolicName, request.version);
						
						Resource res = getResourceForRequirement(toRequirementList(identityRequirement), requestIdentity);

	                    List<URI> indexes = getRelevantIndex(res);
	                    
                    	String resolveRequirements = resolveRequirementsFor(res, identityRequirement);
                    	
                    	Map<Resource, String> resolve = resolver.resolve(requestIdentity,
                        		singletonList(loadIndex("Resolving " + requestIdentity, indexes)),
                        		toRequirementList(resolveRequirements));
                    	ManagementResponseDTO response = new ManagementResponseDTO();
                        response.code = resolve.size() == 0 ? ALREADY_INSTALLED : BID;
                        response.bid = 0;
                        response.requestIdentity = requestIdentity;
                        response.targetNode = request.sourceNode;
                        response.symbolicName = request.symbolicName;
                        response.version = request.version;
                        eventBus.deliver(response);
                    } else {
                        throw new Exception("unknown request: " + request);
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        failedAction(request, e);
                    }
                }
            }
        }
        
        private void failedAction(ManagementDTO request, Throwable t) {
        	pendingInstall.remove(request.requestIdentity, request.sourceNode);
        	log.warn("Failed to process action(%s) event(%s): %s", request.getClass().getSimpleName(), 
        			request.requestIdentity, t.getMessage(), t);
        	ManagementResponseDTO response = new ManagementResponseDTO();
            response.code = ManagementResponseDTO.ResponseCode.FAIL;
            response.requestIdentity = request.requestIdentity;
            response.targetNode = request.sourceNode;
            eventBus.deliver(response);
        }

        private List<URI> getRelevantIndex(Resource resource) {

			List<Capability> capabilities = resource.getCapabilities(IDENTITY_NAMESPACE);

			//TODO We shouldn't have an empty id capability ever, but the tests do
			// at the moment
			if(capabilities.isEmpty() ||
					IDENTITY_TYPE_SMART_BEHAVIOUR.equals(capabilities.get(0).getAttributes().get(CAPABILITY_TYPE_ATTRIBUTE))) {
				// This is a Smart Behaviour
				List<URI> list = resource.getCapabilities(CONTENT_NAMESPACE).stream()
					.map(Capability::getAttributes)
					.filter(a -> CONTENT_MIME_TYPE_INDEX.equals(a.get(CAPABILITY_MIME_ATTRIBUTE)))
					.map(a -> String.valueOf(a.get(CAPABILITY_URL_ATTRIBUTE)))
					.map(URI::create)
					.collect(toList());

				return list.isEmpty() ? indexes : list;
			} else {
				// This is not a smart behaviour, just use the indexes we have
				return indexes;
			}
		}

		private String resolveRequirementsFor(Resource res, String resourceSelectionRequirement) {
			List<Capability> capabilities = res.getCapabilities(IDENTITY_NAMESPACE);

			//TODO We shouldn't have an empty id capability ever, but the tests do
			// at the moment
			if(capabilities.isEmpty() ||
					IDENTITY_TYPE_SMART_BEHAVIOUR.equals(capabilities.get(0).getAttributes().get(CAPABILITY_TYPE_ATTRIBUTE))) {
				// This is a Smart Behaviour
				String aggregate = res.getCapabilities(SMART_BEHAVIOUR_DEPLOYMENT_NAMESPACE).stream()
					.map(c -> String.valueOf(c.getAttributes().get(CAPABILITY_REQUIREMENTS_ATTRIBUTE)))
					.collect(Collectors.joining(","));

				return aggregate.isEmpty() ? resourceSelectionRequirement : aggregate;

			} else {
				// This is not a smart behaviour, just use the indexes we have
				return resourceSelectionRequirement;
			}
		}
    }


    void debug(String format, Object... args) {
        if (log != null) {
            // FIXME: SCR is logging DEBUG on our loggers!
            log.info(format, args);
        } else {
            System.err.printf("BMS:DEBUG:" + format + "\n", args);
        }
    }

    void info(String format, Object... args) {
        if (log != null) {
            log.info(format, args);
        } else {
            System.err.printf("BMS:INFO:" + format + "\n", args);
        }
    }

    void warn(String format, Object... args) {
        if (log != null) {
            log.warn(format, args);
        } else {
            System.err.printf("BMS:WARN:" + format + "\n", args);
        }
    }


}
