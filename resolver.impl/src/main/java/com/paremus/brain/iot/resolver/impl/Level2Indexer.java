package com.paremus.brain.iot.resolver.impl;

import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.repository.XMLResourceGenerator;
import aQute.bnd.repository.osgi.OSGiRepository;
import aQute.bnd.service.url.URLConnector;
import eu.brain.iot.eventing.annotation.SmartBehaviourDefinition;
import eu.brain.iot.installer.api.BehaviourDTO;
import org.osgi.framework.Filter;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Level2Indexer creates top-level Brain-IoT smart behaviour repository index
 * from a list of normal repository indexes.
 */
public class Level2Indexer {
    private static final String NAMESPACE = SmartBehaviourDefinition.PREFIX_
            .substring(0, SmartBehaviourDefinition.PREFIX_.length() - 1);

    public static void main(String[] args) throws Exception {
        List<String> argv = new ArrayList<>(Arrays.asList(args));

        boolean testing = false;

        if (argv.isEmpty() && testing) {
            argv.add("index2.xml");
            argv.add("installer.test/target/index.xml");
            argv.add("../SmartBehaviourEventBus/eventing-example/single-framework-example/target/index.xml");
        }

        if (argv.size() < 2) {
            System.err.println("Usage: Level2Indexer output.xml index-URLs..");
            System.exit(1);
        } else {
            File out = new File(argv.remove(0));
            URI root = out.getCanonicalFile().getParentFile().toURI();
            index2(root, out, argv);
            System.exit(0);
        }
    }

    public static void index2(URI root, File output, List<String> indexes) throws Exception {
        List<Resource> resources = getBrainIotResources(indexes, root);
        XMLResourceGenerator repository = new XMLResourceGenerator();
        repository.name("Brain-IOT level-1 repository");
        repository.resources(resources);
        repository.save(output.getAbsoluteFile());
    }

    public static List<BehaviourDTO> findBrainIotResources(List<String> indexes, Filter filter) throws Exception {
        return getBrainIotResources(indexes, null).stream().filter(r ->
                r.getCapabilities(NAMESPACE).stream()
                        .anyMatch(c -> filter == null || filter.matches(c.getAttributes()))
        ).flatMap(r ->
                r.getCapabilities("osgi.identity").stream()
                        .filter(c -> "osgi.bundle".equals(c.getAttributes().get("type")))
                        .limit(1)
                        .flatMap(id -> r.getCapabilities(NAMESPACE).stream()
                                .map(c -> newBehaviour(id, c)))
        ).distinct().collect(Collectors.toList());
    }

    private static BehaviourDTO newBehaviour(Capability id, Capability brainiot) {
        BehaviourDTO dto = new BehaviourDTO();
        dto.bundle = String.valueOf(id.getAttributes().get("osgi.identity"));
        dto.version = String.valueOf(id.getAttributes().get("version"));
        brainiot.getAttributes().forEach((k, v) -> {
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

    private static List<Resource> getBrainIotResources(List<String> indexes, URI root) throws Exception {
        Requirement brainIotReq = new RequirementImpl(NAMESPACE,
                Collections.emptyMap(), Collections.emptyMap(), null);
        List<Requirement> brainIotReqs = Arrays.asList(brainIotReq);

        BasicRegistry registry = new BasicRegistry();
        registry.put(URLConnector.class, new JarURLConnector());
        registry.put(HttpClient.class, new HttpClient());

        List<Resource> resources = new ArrayList<>();

        for (String idx : indexes) {
            File file = new File(idx);
            URI index = file.exists() ? file.getCanonicalFile().toURI() : new URI(idx);
            OSGiRepository repo = new OSGiRepository();
            repo.setRegistry(registry);
            repo.setProperties(Collections.singletonMap("locations", index.toString()));

            final ResourceImpl[] resource = new ResourceImpl[1];
            final Capability[] currentId = new Capability[1];

            repo.findProviders(brainIotReqs).values().forEach(caps -> caps.stream()
                    .filter(c -> !c.getAttributes().isEmpty() || !c.getDirectives().isEmpty())
                    .distinct()
                    .forEach(cap -> {
                        List<Capability> ids = cap.getResource().getCapabilities("osgi.identity");
                        if (!ids.isEmpty()) {
                            Capability id = CapabilityImpl.copy(ids.get(0), null);
                            if (!id.equals(currentId[0])) {
                                if (resource[0] != null) {
                                    resources.add(resource[0]);
                                }
                                resource[0] = new ResourceImpl();
                                if (root != null) {
                                    resource[0].addCapability(compositeCapability(root.relativize(index)));
                                }
                                resource[0].addCapability(id);
                                currentId[0] = id;
                            }
                            resource[0].addCapability(cap);
                        }
                    }));

            if (resource[0] != null) {
                resources.add(resource[0]);
            }
        }

        return resources;
    }


    private static Capability compositeCapability(URI index) {
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("mime", ResolverContext.MIME_INDEX);
        attribs.put("url", index.toString());
        return new CapabilityImpl("osgi.content", Collections.emptyMap(), attribs, null);
    }
}
