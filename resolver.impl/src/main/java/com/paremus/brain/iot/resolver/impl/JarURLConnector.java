/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.resolver.impl;

import aQute.bnd.service.url.TaggedData;
import aQute.bnd.service.url.URLConnector;

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class JarURLConnector implements URLConnector {

    @Override
    public TaggedData connectTagged(URL url) throws Exception {
        URLConnection connection = url.openConnection();
        if (connection instanceof JarURLConnection) {
            connection.setUseCaches(false);
        }
        return new TaggedData(connection, connection.getInputStream());
    }

    @Override
    public InputStream connect(URL url) throws IOException, Exception {
        return connectTagged(url).getInputStream();
    }

    @Override
    public TaggedData connectTagged(URL url, String tag) throws Exception {
        return connectTagged(url);
    }

}
