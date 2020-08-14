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

import com.kumuluz.ee.rest.client.mp.invoker.ParamInfo;
import org.eclipse.microprofile.rest.client.ext.QueryParamStyle;

import javax.ws.rs.CookieParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.QueryParam;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;

/**
 * Utility for processing bean parameters.
 *
 * @author Miha Jamsek
 * @since 1.0.1
 */
public class BeanParamProcessorUtil {

    private Parameter parameter;

    public BeanParamProcessorUtil(Parameter parameter) {
        this.parameter = parameter;
    }

    public <T> ParamInfo getBeanParams(ParamInfo paramInfo, T instance) {
        if (instance == null) {
            return paramInfo;
        }
        try {
            Class paramType = parameter.getType();
            for (Field field : paramType.getDeclaredFields()) {

                if (field.isAnnotationPresent(QueryParam.class)) {
                    QueryParam queryParam = field.getAnnotation(QueryParam.class);
                    field.setAccessible(true);
                    paramInfo.addQueryParameter(queryParam.value(), field.get(instance));
                }
                if (field.isAnnotationPresent(HeaderParam.class)) {
                    HeaderParam headerParam = field.getAnnotation(HeaderParam.class);
                    field.setAccessible(true);
                    paramInfo.addHeader(headerParam.value(), (String) field.get(instance));
                }
                if (field.isAnnotationPresent(CookieParam.class)) {
                    CookieParam cookieParam = field.getAnnotation(CookieParam.class);
                    field.setAccessible(true);
                    paramInfo.addCookieParameter(cookieParam.value(), field.get(instance));
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return paramInfo;
    }

}
