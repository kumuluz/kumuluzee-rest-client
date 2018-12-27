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

import javax.ws.rs.client.ClientBuilder;
import java.util.Optional;

/**
 * Utility for checking if mapper is disabled.
 *
 * @author Miha Jamsek
 * @since 1.0.1
 */
public class MapperDisabledUtil {

    private static final String DEFAULT_MAPPER_PROPERTY = "microprofile.rest.client.disable.default.mapper";

    public static boolean isMapperDisabled(ClientBuilder clientBuilder) {
        Optional<Boolean> defaultMapperProp = ConfigurationUtil.getInstance().getBoolean(DEFAULT_MAPPER_PROPERTY);
        if (defaultMapperProp.isPresent() && defaultMapperProp.get().equals(Boolean.TRUE)) {
            return true;
        } else {
            try {
                Object property = clientBuilder.getConfiguration().getProperty(DEFAULT_MAPPER_PROPERTY);
                if (property != null) {
                    return (Boolean) property;
                }
            } catch (Throwable ignored) {

            }
        }
        return false;
    }
}
