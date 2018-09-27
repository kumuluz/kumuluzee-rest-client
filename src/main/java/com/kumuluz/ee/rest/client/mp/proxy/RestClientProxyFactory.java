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
package com.kumuluz.ee.rest.client.mp.proxy;

import org.apache.deltaspike.proxy.api.DeltaSpikeProxyFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

/**
 * DeltaSpike proxy factory for interfaces annotated with
 * {@link org.eclipse.microprofile.rest.client.inject.RegisterRestClient}.
 *
 * @author Miha Jamsek
 * @since 1.0.1
 */
public class RestClientProxyFactory extends DeltaSpikeProxyFactory {

    private static final RestClientProxyFactory INSTANCE = new RestClientProxyFactory();

    public static RestClientProxyFactory getInstance() {
        return INSTANCE;
    }

    @Override
    protected ArrayList<Method> getDelegateMethods(Class<?> aClass, ArrayList<Method> allMethods) {
        ArrayList<Method> methods = new ArrayList<>();
        for (Method method : allMethods) {
            if (Modifier.isAbstract(method.getModifiers())) {
                methods.add(method);
            }
        }
        return methods;
    }

    @Override
    protected String getProxyClassSuffix() {
        return "$$RCProxyClient";
    }
}
