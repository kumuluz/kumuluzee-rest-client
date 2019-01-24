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

import com.kumuluz.ee.configuration.utils.ConfigurationUtil;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;

/**
 * Common configuration for rest client registrations.
 *
 * @author Urban Malc
 * @since 1.0.1
 */
public class RegistrationConfigUtil {

    private static Map<String, Integer> registrationToIndex;

    private synchronized static void scanRegistrations() {
        if (registrationToIndex == null) {
            ConfigurationUtil keeConf = ConfigurationUtil.getInstance();

            Map<String, Integer> registrations = new HashMap<>();

            int noRegistrations = keeConf.getListSize("kumuluzee.rest-client.registrations").orElse(0);
            for (int i = 0; i < noRegistrations; i++) {
                Optional<String> registrationClass = keeConf
                        .get("kumuluzee.rest-client.registrations[" + i + "].class");

                if (registrationClass.isPresent()) {
                    registrations.put(registrationClass.get(), i);
                }
            }

            registrationToIndex = registrations;
        }
    }

    public static <T> Optional<T> getConfigurationParameter(Class<?> registration, String property, Class<T> tClass) {
        if (registrationToIndex == null) {
            scanRegistrations();
        }

        String classname = registration.getName();
        List<String> keys = new ArrayList<>();
        keys.add(classname + "/mp-rest/" + property);

        if (registrationToIndex.containsKey(classname)) {
            keys.add("kumuluzee.rest-client.registrations[" + registrationToIndex.get(classname) + "]." + property);
        }

        Optional<T> param = Optional.empty();
        for (String key : keys) {
            param = getOptionalValue(key, tClass);

            if (param.isPresent()) {
                break;
            }
        }

        return param;
    }

    @SuppressWarnings("unchecked")
    private static <T> Optional<T> getOptionalValue(String key, Class<T> tClass) {
        ConfigurationUtil configurationUtil = ConfigurationUtil.getInstance();

        if (tClass.equals(String.class)) {
            return (Optional<T>) configurationUtil.get(key);
        } else if (tClass.equals(Integer.class)) {
            return (Optional<T>) configurationUtil.getInteger(key);
        } else if (tClass.equals(URL.class)) {
            String url = configurationUtil.get(key).orElse(null);
            if (url == null) {
                return Optional.empty();
            }
            try {
                return (Optional<T>) Optional.of(new URL(url));
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException(String.format("Could not convert value %s to URL", url), e);
            }
        } else if (tClass.equals(URI.class)) {
            String url = configurationUtil.get(key).orElse(null);
            if (url == null) {
                return Optional.empty();
            }
            return (Optional<T>) Optional.of(URI.create(url));
        } else {
            throw new IllegalArgumentException("Converter for " + tClass + " not found.");
        }
    }
}
