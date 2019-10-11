/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */

package com.paremus.brain.iot.management.impl;

import static com.paremus.brain.iot.management.api.ManagementResponseDTO.ResponseCode.ALREADY_INSTALLED;
import static com.paremus.brain.iot.management.api.ManagementResponseDTO.ResponseCode.BID;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
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

import com.paremus.brain.iot.management.api.BehaviourManagement;
import com.paremus.brain.iot.management.api.ManagementBidRequestDTO;
import com.paremus.brain.iot.management.api.ManagementInstallRequestDTO;
import com.paremus.brain.iot.management.api.ManagementRequestDTO;
import com.paremus.brain.iot.management.api.ManagementResponseDTO;

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
@SmartBehaviourDefinition(consumed = {ManagementBidRequestDTO.class},
        author = BehaviourManagementImpl.BEHAVIOUR_AUTHOR, 
        name = BehaviourManagementImpl.BEHAVIOUR_NAME,
        description = "Implements the Behaviour Management Service")
public class BehaviourManagementImpl implements SmartBehaviour<ManagementBidRequestDTO>, BehaviourManagement {
    
	static final String BEHAVIOUR_AUTHOR = "Paremus";
    static final String BEHAVIOUR_NAME = "[Brain-IoT] Behaviour Management Service";
    
    static final String EVENT_SERVICE_PROPERTY_PREFIX = "eu.brain.iot.behaviour.";
	
	static final String EXPORTS = "com.paremus.brain.iot.management.api.BehaviourManagement";
    static final String PID = "eu.brain.iot.BehaviourManagementService";

    @interface Config {
        String[] indexes();
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

    private final Map<String, List<Map<String, Object>>> inProgressUntyped = new ConcurrentHashMap<>();
    private final Map<String, Set<ManagementResponseDTO>> bidResponses = new ConcurrentHashMap<>();
    private final Map<String, String> pendingInstall = new ConcurrentHashMap<>();

    private final Map<String, Long> blacklist = new ConcurrentHashMap<>();

    private List<URI> indexes = new ArrayList<>();

    private Config config;

    private Thread thread;

    private String myNode;
    
    private List<ServiceRegistration<?>> registrations = new ArrayList<ServiceRegistration<?>>();

    @Activate
    private void activate(BundleContext context, Config config) throws IOException {
        debug("activate");
        myNode = context.getProperty(Constants.FRAMEWORK_UUID);

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
    private synchronized void modified(Config cfg) {
        this.config = cfg;

        List<URI> indexes = new ArrayList<>();
        for (String index : config.indexes()) {
            indexes.add(URI.create(index));
        }

        // debug to diagnose too many calls to modified()
        if (!indexes.equals(this.indexes)) {
            debug("modified: " + indexes);
            this.indexes = indexes;
        }
        else {
            debug("modified: indexes is unchanged!");
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
    }

    @Override
    public Collection<BehaviourDTO> findBehaviours(String ldapFilter) throws Exception {
        return resolver.findBehaviours(Arrays.asList(config.indexes()), ldapFilter);
    }

    @Override
    public void installBehaviour(BehaviourDTO behaviour, String targetNode) {
        ManagementInstallRequestDTO request = new ManagementInstallRequestDTO();

        request.targetNode = targetNode;
        request.eventType = "install:" + behaviour.bundle + ":" + behaviour.version;

        request.eventData = new HashMap<>();
        request.eventData.put("name", behaviour.name);
        request.eventData.put("bundle", behaviour.bundle);
        request.eventData.put("version", behaviour.version);

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
        String eventType = response.eventType;
        switch (response.code) {
            case FAIL:
                break;

            case BID:
                Set<ManagementResponseDTO> bids = bidResponses.get(eventType);
                if (bids != null) {
                    bids.add(response);
                }
                break;

            case ALREADY_INSTALLED:
                warn("consumer for <%s> is already installed on node <%s>", eventType, response.sourceNode);
                bidResponses.remove(eventType);
                inProgressUntyped.remove(eventType);
                break;

            case INSTALL_OK:
                blacklist.put(eventType, 0L);   // flag install ok
                List<Map<String, Object>> events = inProgressUntyped.remove(eventType);

                if (events != null) {
                    info("Resending %d last resort events(%s)", events.size(), eventType);
                    for (Map<String, Object> event : events) {
                        eventBus.deliver(eventType, event);
                    }
                }
                break;
        }
    }

    void notify(InstallResponseDTO response) {
        String eventType = response.installRequest.symbolicName;

        String target = pendingInstall.remove(eventType);
        if (target == null) {
            debug("Ignore InstallResponse that we did not initiate: %s", eventType);
            return;
        }

        ManagementResponseDTO mr = new ManagementResponseDTO();
        mr.targetNode = target;
        mr.eventType = eventType;

        if (response.code.equals(ResponseCode.SUCCESS)) {
            mr.code = ManagementResponseDTO.ResponseCode.INSTALL_OK;
            eventBus.deliver(mr);

        } else {
            warn("Failed to install requirement for eventType(%s): %s", eventType, response.messages);
            mr.code = ManagementResponseDTO.ResponseCode.FAIL;
            eventBus.deliver(mr);
        }
    }

    void notifyLastResort(String eventType, Map<String, ?> properties) {
        List<Map<String, Object>> pendingEvents = inProgressUntyped.get(eventType);

        if (pendingEvents != null) {
            info("Queue Last_Resort event(%s) pending install", eventType);
            pendingEvents.add(new HashMap<String, Object>(properties));
        } else if (blacklist.containsKey(eventType)) {
            if (blacklist.get(eventType) == 0) {
                warn("Event(%s) still not consumed after installing behaviour", eventType);
                blacklist.put(eventType, System.currentTimeMillis());
            } else {
                debug("Ignore event(%s) we've already handled", eventType);
            }
        } else {
            pendingEvents = new ArrayList<>();
            inProgressUntyped.put(eventType, pendingEvents);
            pendingEvents.add(new HashMap<String, Object>(properties));

            // prepare for bid responses
            bidResponses.put(eventType, new HashSet<>());

            ManagementBidRequestDTO request = new ManagementBidRequestDTO();
            request.eventType = eventType;
            request.eventData = new HashMap<>(properties);
            eventBus.deliver(request);
        }
    }

    class BidTracker implements Runnable {
        @Override
        public void run() {
            for (String eventType : bidResponses.keySet()) {
                Set<ManagementResponseDTO> bids = bidResponses.get(eventType);
                if (bids == null) continue;
                if (bids.size() == 0) continue;

                List<ManagementResponseDTO> lbids = new ArrayList<>(bids);
                Collections.sort(lbids, (a, b) -> b.bid - a.bid);
                ManagementResponseDTO bestBid = lbids.get(0);

                long millis = System.currentTimeMillis() - bestBid.timestamp.toEpochMilli();

                if (bestBid.bid > 0 || millis > 1000) {
                    bidResponses.remove(eventType); // ignore further bids
                    ManagementInstallRequestDTO request = new ManagementInstallRequestDTO();
                    request.targetNode = bestBid.sourceNode;
                    request.eventType = eventType;
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
                    String eventType = request.eventType;

                    // reject any more events of this type
                    blacklist.put(eventType, System.currentTimeMillis());

                    info("\n\nProcessing %s eventType=%s %s", action, eventType, request.eventData);

                    List<String> rawRequirements = new ArrayList<>();
                    rawRequirements.add(String.format("%s;filter:=\"(consumed=%s)\"",
                            "eu.brain.iot.behaviour", request.eventType));

                    List<Requirement> requirements = rawRequirements.stream()
                            .map(s -> resolver.parseRequement(s)).collect(Collectors.toList());

                    String name = "LastResort:" + eventType;

                    if (request instanceof ManagementBidRequestDTO) {
                        Map<Resource, String> resolve = resolver.resolve(name, indexes, requirements);
                        debug("resolve size=" + resolve.size());
                        ManagementResponseDTO response = new ManagementResponseDTO();
                        response.code = resolve.size() == 0 ? ALREADY_INSTALLED : BID;
                        response.bid = 0;
                        response.eventType = eventType;
                        response.targetNode = request.sourceNode;
                        eventBus.deliver(response);
                    } else if (request instanceof ManagementInstallRequestDTO) {
                        pendingInstall.put(eventType, request.sourceNode);
                        // create request to install requirement
                        InstallRequestDTO installRequest = new InstallRequestDTO();
                        installRequest.action = InstallAction.INSTALL;
                        installRequest.symbolicName = eventType;
                        installRequest.indexes = Arrays.asList(config.indexes());

                        if (request.eventData == null || !request.eventData.containsKey("bundle")) {
                            installRequest.name = name;
                            installRequest.requirements = rawRequirements;
                        } else {
                            String bundle = (String) request.eventData.get("bundle");
                            String version = (String) request.eventData.get("version");
                            String installName = (String) request.eventData.get("name");
                            installRequest.name = "ManualInstall: " + installName != null ? installName : eventType;
                            installRequest.bundles = Collections.singletonMap(bundle, version != null ? version : "0");
                        }

                        eventBus.deliver(installRequest);
                    } else {
                        throw new Exception("unknown request: " + request);
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        if (log != null)
                            log.warn("Failed to process action(%s) event(%s): %s", action, request.eventType, e.toString(), e);
                        e.printStackTrace();
                        ManagementResponseDTO response = new ManagementResponseDTO();
                        response.code = ManagementResponseDTO.ResponseCode.FAIL;
                        response.eventType = request.eventType;
                        response.targetNode = request.sourceNode;
                        eventBus.deliver(response);
                    }
                }
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
