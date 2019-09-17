/* Copyright 2019 Paremus, Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package com.paremus.brain.iot.resolver.impl;

import aQute.bnd.service.Registry;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class BasicRegistry implements Registry {

    private final Map<Class<?>, List<Object>> plugins = new HashMap<>();

    public synchronized <T> BasicRegistry put(Class<T> clazz, T plugin) {
        List<Object> list = plugins.get(clazz);
        if (list == null) {
            list = new LinkedList<>();
            plugins.put(clazz, list);
        }
        list.add(plugin);
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized <T> List<T> getPlugins(Class<T> clazz) {
        List<T> objs = (List<T>) plugins.get(clazz);
        return objs != null ? Collections.<T>unmodifiableList(objs) : Collections.<T>emptyList();
    }

    @Override
    public <T> T getPlugin(Class<T> clazz) {
        List<T> l = getPlugins(clazz);
        return (l != null && !l.isEmpty()) ? l.get(0) : null;
    }

}
