package com.paremus.brain.iot.resolver.impl;

import aQute.bnd.http.HttpClient;
import aQute.bnd.osgi.repository.XMLResourceGenerator;
import aQute.bnd.repository.osgi.OSGiRepository;
import aQute.bnd.service.url.URLConnector;
import eu.brain.iot.eventing.annotation.SmartBehaviourDefinition;
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

/**
 * Example 2-tier indexer.
 */
public class Level2Indexer {
    public static final String NAMESPACE_ = SmartBehaviourDefinition.PREFIX_;

    public static void main(String[] args) throws Exception {
        List<String> argv = new ArrayList<>(Arrays.asList(args));

        if (argv.isEmpty()) {
            argv.add("index2.xml");
            argv.add("installer.test/target/index.xml");
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

    static void index2(URI root, File index2, List<String> indexes) throws Exception {
        String namespace = NAMESPACE_.substring(0, NAMESPACE_.length() - 1);
        Requirement brainIotReq = new RequirementImpl(namespace,
                Collections.emptyMap(), Collections.emptyMap(), null);
        List<Requirement> brainIotReqs = new ArrayList<>();
        brainIotReqs.add(brainIotReq);

        List<Resource> index2Resources = new ArrayList<>();


        BasicRegistry registry = new BasicRegistry();
        registry.put(URLConnector.class, new JarURLConnector());
        registry.put(HttpClient.class, new HttpClient());

        for (String idx : indexes) {
            File file = new File(idx);
            String index = file.exists() ? file.getCanonicalFile().toURI().toString() : idx;
            Map<String, String> repoProps = new HashMap<>();
            repoProps.put("locations", index);
            OSGiRepository repo = new OSGiRepository();
            repo.setRegistry(registry);
            repo.setProperties(repoProps);

            final ResourceImpl[] resource = new ResourceImpl[1];
            final Capability[] currentId = new Capability[1];

            repo.findProviders(brainIotReqs).values().forEach(caps -> caps.stream()
                    .filter(c -> !c.getAttributes().isEmpty() || !c.getDirectives().isEmpty())
                    .distinct()
                    .forEach(cap -> {
                        System.out.println("Cap :" + cap);
                        List<Capability> ids = cap.getResource().getCapabilities("osgi.identity");
                        if (!ids.isEmpty()) {
                            Capability id = CapabilityImpl.copy(ids.get(0), null);
                            if (!id.equals(currentId[0])) {
                                if (resource[0] != null) {
                                    index2Resources.add(resource[0]);
                                }
                                resource[0] = compositeResource(index);
                                resource[0].addCapability(id);
                                currentId[0] = id;
                            }
                            resource[0].addCapability(cap);
                        }
                    }));

            if (resource[0] != null) {
                index2Resources.add(resource[0]);
            }
        }

        XMLResourceGenerator repository = new XMLResourceGenerator();
        repository.name("Brain-IOT level-1 repository");
        repository.resources(index2Resources);
        repository.save(index2.getAbsoluteFile());
    }

    private static ResourceImpl compositeResource(String index) {
        ResourceImpl resource = new ResourceImpl();
        Map<String, Object> attribs = new HashMap<>();
        attribs.put("mime", ResolverContext.MIME_INDEX);
        attribs.put("url", index);
        CapabilityImpl content = new CapabilityImpl("osgi.content", Collections.emptyMap(), attribs, resource);
        resource.addCapability(content);
        return resource;
    }
}
