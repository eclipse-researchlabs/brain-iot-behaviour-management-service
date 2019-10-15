/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.installer.impl;

import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.log.FormatterLogger;
import org.osgi.service.log.LoggerFactory;

import aQute.bnd.http.HttpClient;

@Component
public class FrameworkInstallerImpl implements FrameworkInstaller {
    @Reference(service = LoggerFactory.class, cardinality = ReferenceCardinality.OPTIONAL)
    private FormatterLogger log;

    private final Map<Long, Set<Object>> bundleSponsors = new HashMap<>();
    private BundleContext context;

    @Activate
    void activate(BundleContext context) {
        this.context = context;
    }

    @Override
    public synchronized Set<Object> getSponsors() {
    	return bundleSponsors.values().stream().flatMap(Set::stream).collect(toSet());
    }

    @Override
    public synchronized List<Bundle> addLocations(Object sponsor, List<String> bundleLocations, HttpClient client) throws BundleException, IOException {
        List<Bundle> installed = new ArrayList<>(bundleLocations.size());

        for (String location : bundleLocations) {
            // Find an existing bundle with that location.
            Bundle existing = context.getBundle(location);
            if (existing != null) {
                // If the existing bundle was previously installed by us then add to the sponsors.
                Set<Object> sponsors = bundleSponsors.get(existing.getBundleId());
                if (sponsors != null) {
                    sponsors.add(sponsor);
                }
            } else {
                // No existing bundle with that location. Install it and add the sponsor.
                try {
                    URI locationUri = new URI(location);
                    try (InputStream stream = client.connect(locationUri.toURL())) {
                        if (log != null)
                            log.info("installing %s", locationUri);
                        Bundle bundle = this.context.installBundle(location, stream);
                        installed.add(bundle);

                        Set<Object> sponsors = new HashSet<>();
                        sponsors.add(sponsor);
                        bundleSponsors.put(bundle.getBundleId(), sponsors);
                    } catch (BundleException e) {
                        if (e.getType() == BundleException.DUPLICATE_BUNDLE_ERROR) {
                            if (log != null)
                                log.warn("Duplicate bundle symbolic-name/version in install for location %s", location, e);
                        } else {
                            throw e;
                        }
                    }
                } catch (URISyntaxException e) {
                    throw new IOException("Invalid bundle location URI: " + location, e);
                } catch (Exception e) {
                	if(e instanceof IOException) throw (IOException) e;
                	else throw new IOException("An unknown error occurred installing a bundle from location URI: " + location, e);
                }
            }
        }

        return installed;
    }

    @Override
    public synchronized List<Bundle> removeSponsor(Object sponsor) {
        List<Bundle> uninstall = new ArrayList<>();

        for (Iterator<Entry<Long, Set<Object>>> iter = this.bundleSponsors.entrySet().iterator(); iter.hasNext(); ) {
            Entry<Long, Set<Object>> entry = iter.next();
            long bundleId = entry.getKey();
            Set<Object> sponsors = entry.getValue();

            if (sponsors.remove(sponsor) && sponsors.isEmpty()) {
                Bundle bundle = context.getBundle(bundleId);

                // We removed our sponsor and the sponsor set is now empty => this bundle should be removed.
                if (bundle != null)
                    uninstall.add(bundle);

                // Also remove the entry from the sponsor map.
                iter.remove();
            }
        }

        // reverse sort, so latest bundles are removed first
        Collections.sort(uninstall, (a, b) -> {
            return -a.compareTo(b);
        });

        for (Bundle bundle : uninstall) {
            try {
                bundle.uninstall();
            } catch (BundleException e) {
                if (log != null)
                    log.error("Failed to uninstall bundle: " + bundle.getSymbolicName(), e);
            }
        }

        return uninstall;
    }

    @Override
    public synchronized List<String> getLocations(Object sponsor) {
        List<String> locations = new ArrayList<>();

        for (Iterator<Entry<Long, Set<Object>>> iter = this.bundleSponsors.entrySet().iterator(); iter.hasNext(); ) {
            Entry<Long, Set<Object>> entry = iter.next();
            long bundleId = entry.getKey();
            Set<Object> sponsors = entry.getValue();

            if (sponsors.contains(sponsor)) {
                Bundle bundle = context.getBundle(bundleId);
                if (bundle != null) {
                    locations.add(bundle.getLocation());
                }
            }
        }

        return locations;
    }

}
