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
import org.osgi.service.log.FormatterLogger;
import org.osgi.service.log.LoggerFactory;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import com.paremus.brain.iot.management.api.BehaviourManagement;
import com.paremus.brain.iot.management.api.ManagementBidRequestDTO;
import com.paremus.brain.iot.management.api.ManagementInstallRequestDTO;
import com.paremus.brain.iot.management.api.ManagementRequestDTO;
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
import eu.brain.iot.installer.api.InstallRequestDTO;
import eu.brain.iot.installer.api.InstallRequestDTO.InstallAction;
import eu.brain.iot.installer.api.InstallResolver;
import eu.brain.iot.installer.api.InstallResponseDTO;
import eu.brain.iot.installer.api.InstallResponseDTO.ResponseCode;

@Component(configurationPid = BehaviourManagementImpl.PID,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = {Constants.SERVICE_EXPORTED_INTERFACES + "=" + BehaviourManagementImpl.EXPORTS}
)
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

	static final String EXPORTS = "com.paremus.brain.iot.management.api.BehaviourManagement";
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
    ConfigurationAdmin configAdmin;

    private final AtomicBoolean running = new AtomicBoolean(true);

    private final BlockingQueue<ManagementRequestDTO> queue = new LinkedBlockingQueue<>();

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
        props.put(EVENT_SERVICE_PROPERTY_PREFIX + "description", "Install response consumer");
        props.put(EVENT_SERVICE_PROPERTY_PREFIX + "consumed", InstallResponseDTO.class.getName());
        props.put(EVENT_SERVICE_PROPERTY_PREFIX + "filter", String.format("(requestNode=%s)", myNode));

        registrations.add(context.registerService(SmartBehaviour.class,
        		new InstallResponseConsumer(this), props));

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

        request.requirement = format(IDENTITY_FILTER, behaviour.bundle, behaviour.version);

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

    void notify(InstallResponseDTO response) {
        String requestIdentity = response.installRequest.name;

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
            request.requirement = format(SMART_BEHAVIOUR_FILTER, eventType);
            eventBus.deliver(request);
        }
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
                    request.targetNode = bestBid.sourceNode;
                    request.requestIdentity = requestIdentity;
                    request.requirement = bestBid.requirement;
                    eventBus.deliver(request);
                }
            }
        }
    }

    class QueueProcessor implements Runnable {
        @Override
        public void run() {
            while (running.get()) {
                ManagementRequestDTO request = null;
                String action = null;

                try {
                    request = queue.take();
                    action = request.getClass().getSimpleName();
                    String requestIdentity = request.requestIdentity;

                    // reject any more events of this type
                    blacklist.put(requestIdentity, System.currentTimeMillis());

                    info("\n\nProcessing %s requestIdentity=%s %s", action, requestIdentity, request.requirement);

                    List<Requirement> resourceSelectionRequirement = toRequirementList(request.requirement);

                    Resource res = getResourceForRequirement(resourceSelectionRequirement, requestIdentity);

                    List<URI> indexes = getRelevantIndex(res);

                    String resolveRequirements = resolveRequirementsFor(res, request.requirement);

                    if (request instanceof ManagementBidRequestDTO) {
                        Map<Resource, String> resolve = resolver.resolve(requestIdentity,
                        		singletonList(loadIndex("Resolving " + requestIdentity, indexes)),
                        		toRequirementList(resolveRequirements));
                        debug("resolve size=" + resolve.size());
                        ManagementResponseDTO response = new ManagementResponseDTO();
                        response.code = resolve.size() == 0 ? ALREADY_INSTALLED : BID;
                        response.bid = 0;
                        response.requestIdentity = requestIdentity;
                        response.targetNode = request.sourceNode;
                        response.requirement = request.requirement;
                        eventBus.deliver(response);
                    } else if (request instanceof ManagementInstallRequestDTO) {
                        pendingInstall.put(requestIdentity, request.sourceNode);
                        // create request to install requirement
                        InstallRequestDTO installRequest = new InstallRequestDTO();
                        installRequest.action = InstallAction.INSTALL;
                        installRequest.requirements = Collections.singletonList(resolveRequirements);
                        installRequest.indexes = indexes.stream().map(URI::toString).collect(toList());
                        installRequest.name = requestIdentity;
                        installRequest.symbolicName = getSymbolicName(res, requestIdentity);
                        installRequest.version = getVersion(res);

                        eventBus.deliver(installRequest);
                    } else {
                        throw new Exception("unknown request: " + request);
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        if (log != null)
                            log.warn("Failed to process action(%s) event(%s): %s", action, request.requestIdentity, e.toString(), e);
                        e.printStackTrace();
                        ManagementResponseDTO response = new ManagementResponseDTO();
                        response.code = ManagementResponseDTO.ResponseCode.FAIL;
                        response.requestIdentity = request.requestIdentity;
                        response.targetNode = request.sourceNode;
                        eventBus.deliver(response);
                    }
                }
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
			return String.valueOf(capabilities.get(0).getAttributes().get(IDENTITY_NAMESPACE));
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
