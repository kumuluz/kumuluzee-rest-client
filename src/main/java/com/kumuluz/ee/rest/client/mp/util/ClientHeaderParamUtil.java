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

import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.*;

/**
 * Utility methods for processing and validating {@link ClientHeaderParam} annotations.
 *
 * @author Urban Malc
 * @since 1.2.0
 */
public class ClientHeaderParamUtil {

    public static MultivaluedMap<String, String> collectClientHeaderParams(Method method) throws Throwable {
        MultivaluedMap<String, String> interfaceHeaders = new MultivaluedHashMap<>();
        for (ClientHeaderParam clientHeaderParam : method.getDeclaringClass().getAnnotationsByType(ClientHeaderParam.class)) {
            processClientHeaderParam(interfaceHeaders, clientHeaderParam, method);
        }
        MultivaluedMap<String, String> methodHeaders = new MultivaluedHashMap<>();
        for (ClientHeaderParam clientHeaderParam : method.getAnnotationsByType(ClientHeaderParam.class)) {
            processClientHeaderParam(methodHeaders, clientHeaderParam, method);
        }

        methodHeaders.forEach((k, v) -> {
            interfaceHeaders.remove(k);
            interfaceHeaders.addAll(k, v);
        });

        return interfaceHeaders;
    }

    private static void processClientHeaderParam(MultivaluedMap<String, String> headers,
                                                 ClientHeaderParam clientHeaderParam, Method method) throws Throwable {
        if (clientHeaderParam.value().length == 1 && isMethodCall(clientHeaderParam.value()[0])) {
            processMethodClientHeaderParam(headers, clientHeaderParam, method);
            return;
        }

        List<String> paramValues = new ArrayList<>();
        for (String value : clientHeaderParam.value()) {
            if (isMethodCall(value)) {
                throw new RestClientDefinitionException("Multiple method references defined in single ClientHeaderParam");
            }

            paramValues.add(value);
        }

        addToHeaderMap(clientHeaderParam.name(), paramValues, headers);
    }

    private static void processMethodClientHeaderParam(MultivaluedMap<String, String> headers,
                                                       ClientHeaderParam clientHeaderParam, Method method) throws Throwable {
        try {
            String invocationName = clientHeaderParam.value()[0];
            invocationName = invocationName.substring(1, invocationName.length() - 1);

            int separatorIdx = invocationName.lastIndexOf(".");

            Class<?> invocationClass = method.getDeclaringClass();
            String invocationMethod = invocationName;
            if (separatorIdx >= 0) {
                invocationClass = Class.forName(invocationName.substring(0, separatorIdx));
                invocationMethod = invocationName.substring(separatorIdx + 1);
            }

            Object returnObject = null;
            for (Method m : invocationClass.getMethods()) {
                if (m.getName().equals(invocationMethod)) {
                    if (m.getParameterCount() == 0) {
                        if (separatorIdx < 0) {
                            returnObject = invokeDefaultMethod(method.getDeclaringClass(), m, null);
                        } else {
                            returnObject = m.invoke(null);
                        }
                    } else if (m.getParameterCount() == 1) {
                        if (m.getParameterTypes()[0].isAssignableFrom(String.class)) {
                            if (separatorIdx < 0) {
                                returnObject = invokeDefaultMethod(method.getDeclaringClass(), m, clientHeaderParam.name());
                            } else {
                                returnObject = m.invoke(null, clientHeaderParam.name());
                            }
                        }
                    }
                }
            }

            if (returnObject == null) {
                throw new IllegalArgumentException("Method reference not found or returned null");
            }

            if (returnObject instanceof String) {
                addToHeaderMap(clientHeaderParam.name(), Collections.singletonList((String)returnObject), headers);
            } else if (returnObject instanceof String[]) {
                addToHeaderMap(clientHeaderParam.name(), Arrays.asList((String[])returnObject), headers);
            } else {
                throw new IllegalArgumentException("Method returned object that is neither String or String[]");
            }
        } catch (RestClientDefinitionException e) {
            throw e;
        } catch (Throwable e) {
            if (clientHeaderParam.required()) {
                throw e;
            }
        }
    }

    private static Object invokeDefaultMethod(Class interfaceClass, Method method, String argument) throws Throwable {

        final Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class);
        if (!constructor.isAccessible()) {
            constructor.setAccessible(true);
        }

        Object proxyInstance = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[]{interfaceClass},
                (Object proxy, Method m, Object[] arguments) -> null);
    
        
        Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
        field.setAccessible(true);
        MethodHandles.Lookup lookup = (MethodHandles.Lookup) field.get(null);
        Class<?> declaringClazz = method.getDeclaringClass();
        
        MethodHandle handle = lookup.unreflectSpecial(method, declaringClazz)
        .bindTo(proxyInstance);

        if (argument == null) {
            return handle.invokeWithArguments();
        } else {
            return handle.invokeWithArguments(argument);
        }
    }

    private static void addToHeaderMap(String name, List<String> paramValues, MultivaluedMap<String, String> headers) {
        if (headers.containsKey(name)) {
            throw new RestClientDefinitionException("Multiple values defined for the same header on the same target");
        }

        headers.addAll(name, paramValues);
    }

    private static boolean isMethodCall(String value) {
        return value.startsWith("{") && value.endsWith("}");
    }

    public static void validateClientHeaderParams(Method method) {
        Set<String> definedNames = new HashSet<>();
        for (ClientHeaderParam clientHeaderParam : method.getDeclaringClass().getAnnotationsByType(ClientHeaderParam.class)) {
            if (definedNames.contains(clientHeaderParam.name())) {
                throw new RestClientDefinitionException(getHumanFriendlyDescriptor(method) +
                        " Interface class defines multiple ClientHeaderParams with the name " +
                        clientHeaderParam.name());
            } else {
                definedNames.add(clientHeaderParam.name());
            }
            validateClientHeaderParam(clientHeaderParam, method);
        }
        definedNames.clear();
        for (ClientHeaderParam clientHeaderParam : method.getAnnotationsByType(ClientHeaderParam.class)) {
            if (definedNames.contains(clientHeaderParam.name())) {
                throw new RestClientDefinitionException(getHumanFriendlyDescriptor(method) +
                        " Method defines multiple ClientHeaderParams with the name " +
                        clientHeaderParam.name());
            } else {
                definedNames.add(clientHeaderParam.name());
            }
            validateClientHeaderParam(clientHeaderParam, method);
        }
    }

    private static void validateClientHeaderParam(ClientHeaderParam clientHeaderParam, Method method) {
        long methodCalls = Arrays.stream(clientHeaderParam.value()).filter(ClientHeaderParamUtil::isMethodCall).count();
        if (methodCalls >= 1 && clientHeaderParam.value().length > 1) {
            throw new RestClientDefinitionException(getHumanFriendlyDescriptor(method) +
                    " Additional values defined alongside method reference in single ClientHeaderParam");
        }

        Arrays.stream(clientHeaderParam.value()).filter(ClientHeaderParamUtil::isMethodCall).forEach(ref -> {
            String invocationName = ref.substring(1, ref.length() - 1);
            int separatorIdx = invocationName.lastIndexOf(".");

            Class<?> invocationClass = method.getDeclaringClass();
            String invocationMethod = invocationName;
            if (separatorIdx >= 0) {
                try {
                    invocationClass = Class.forName(invocationName.substring(0, separatorIdx));
                } catch (ClassNotFoundException e) {
                    throw new RestClientDefinitionException(getHumanFriendlyDescriptor(method) +
                            " Could not resolve class declared in method reference: " +
                            invocationName.substring(0, separatorIdx));
                }
                invocationMethod = invocationName.substring(separatorIdx + 1);
            }

            Method matchingMethod = null;
            for (Method m : invocationClass.getMethods()) {
                if (m.getName().equals(invocationMethod)) {
                    if (m.getParameterCount() == 0) {
                        matchingMethod = m;
                    } else if (m.getParameterCount() == 1) {
                        if (m.getParameterTypes()[0].isAssignableFrom(String.class)) {
                            matchingMethod = m;
                        }
                    }
                }
            }

            if (matchingMethod == null) {
                throw new RestClientDefinitionException(getHumanFriendlyDescriptor(method) +
                        " Could not find method reference. Make sure it has either zero parameters or one parameter " +
                        "of type String.");
            }

            if (!matchingMethod.getReturnType().isAssignableFrom(String.class) &&
                    !matchingMethod.getReturnType().isAssignableFrom(String[].class)) {
                throw new RestClientDefinitionException(getHumanFriendlyDescriptor(method) +
                        " Method reference should return either String or String[].");
            }
        });
    }

    private static String getHumanFriendlyDescriptor(Method method) {
        return String.format("[Method: %s; Class: %s]", method.getName(), method.getDeclaringClass().getName());
    }
}
