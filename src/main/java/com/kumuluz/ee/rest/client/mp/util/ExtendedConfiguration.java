/*
 *  Copyright (c) 2014-2017 Kumuluz and/or its affiliates
 *  and other contributors as indicated by the @author tags and
 *  the contributor list.
 *
 *  Licensed under the MIT License (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://opensource.org/licenses/MIT
 *
 *  The software is provided "AS IS", WITHOUT WARRANTY OF ANY KIND, express or
 *  implied, including but not limited to the warranties of merchantability,
 *  fitness for a particular purpose and noninfringement. in no event shall the
 *  authors or copyright holders be liable for any claim, damages or other
 *  liability, whether in an action of contract, tort or otherwise, arising from,
 *  out of or in connection with the software or the use or other dealings in the
 *  software. See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.kumuluz.ee.rest.client.mp.util;

import javax.ws.rs.RuntimeType;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Feature;
import java.util.*;

/**
 * Allows extending existing {@link Configuration} with custom providers.
 *
 * @author Urban Malc
 * @since 1.0.1
 */
public class ExtendedConfiguration implements Configuration {

    private Configuration delegate;

    private Set<Object> instances;
    private Map<Class, Map<Class<?>, Integer>> contracts;

    public ExtendedConfiguration(Configuration delegate,
                                 Set<Object> instances,
                                 Map<Class, Map<Class<?>, Integer>> contracts) {

        this.delegate = delegate;
        this.instances = instances;
        this.contracts = contracts;
    }

    @Override
    public RuntimeType getRuntimeType() {
        return delegate.getRuntimeType();
    }

    @Override
    public Map<String, Object> getProperties() {
        return delegate.getProperties();
    }

    @Override
    public Object getProperty(String s) {
        return delegate.getProperty(s);
    }

    @Override
    public Collection<String> getPropertyNames() {
        return delegate.getPropertyNames();
    }

    @Override
    public boolean isEnabled(Feature feature) {
        return delegate.isEnabled(feature);
    }

    @Override
    public boolean isEnabled(Class<? extends Feature> aClass) {
        return delegate.isEnabled(aClass);
    }

    @Override
    public boolean isRegistered(Object o) {
        return delegate.isRegistered(o) || instances.contains(o);
    }

    @Override
    public boolean isRegistered(Class<?> aClass) {
        return delegate.isRegistered(aClass) || instances.stream().map(Object::getClass).anyMatch(aClass::equals);
    }

    @Override
    public Map<Class<?>, Integer> getContracts(Class<?> aClass) {
        Map<Class<?>, Integer> fromDelegate = delegate.getContracts(aClass);
        Map<Class<?>, Integer> custom = this.contracts.get(aClass);

        Map<Class<?>, Integer> combined = new HashMap<>();
        if (fromDelegate != null) {
            combined.putAll(fromDelegate);
        }
        if (custom != null) {
            combined.putAll(custom);
        }

        return combined;
    }

    @Override
    public Set<Class<?>> getClasses() {
        return delegate.getClasses();
    }

    @Override
    public Set<Object> getInstances() {
        Set<Object> fromDelegate = delegate.getInstances();

        Set<Object> combined = new HashSet<>();
        combined.addAll(fromDelegate);
        combined.addAll(instances);

        return combined;
    }
}
