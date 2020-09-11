/*
 * Copyright 2018 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications copyright (C) 2019 Kumuluz
 */
package com.kumuluz.ee.rest.client.mp.cdi;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.enterprise.inject.spi.InterceptionType;
import javax.enterprise.inject.spi.Interceptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Proxy {@link InvocationHandler} for custom interceptors.
 *
 * @author Urban Malc
 * @since 1.2.2
 */
public class InterceptorInvocationHandler implements InvocationHandler {

    private final Object target;

    private final Map<Method, List<InterceptorInvocationContext.InterceptorInvocation>> interceptorChains;

    public InterceptorInvocationHandler(final Class<?> restClientInterface,
                                        final Object target) {
        this.target = target;

        BeanManager beanManager = CDI.current().getBeanManager();
        CreationalContext<?> creationalContext = beanManager.createCreationalContext(null);
        this.interceptorChains = initInterceptorChains(beanManager, creationalContext, restClientInterface);
    }

    private static List<Annotation> getBindings(Annotation[] annotations, BeanManager beanManager) {
        if (annotations.length == 0) {
            return Collections.emptyList();
        }
        List<Annotation> bindings = new ArrayList<>();
        for (Annotation annotation : annotations) {
            if (beanManager.isInterceptorBinding(annotation.annotationType())) {
                bindings.add(annotation);
            }
        }
        return bindings;
    }

    private static Map<Method, List<InterceptorInvocationContext.InterceptorInvocation>> initInterceptorChains(BeanManager beanManager, CreationalContext<?> creationalContext, Class<?> restClientInterface) {

        Map<Method, List<InterceptorInvocationContext.InterceptorInvocation>> chains = new HashMap<>();
        // Interceptor as a key in a map is not entirely correct (custom interceptors) but should work in most cases
        Map<Interceptor<?>, Object> interceptorInstances = new HashMap<>();

        List<Annotation> classLevelBindings = getBindings(restClientInterface.getAnnotations(), beanManager);

        for (Method method : restClientInterface.getMethods()) {
            if (method.isDefault() || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            List<Annotation> methodLevelBindings = getBindings(method.getAnnotations(), beanManager);

            if (!classLevelBindings.isEmpty() || !methodLevelBindings.isEmpty()) {

                Annotation[] interceptorBindings = merge(methodLevelBindings, classLevelBindings);

                List<Interceptor<?>> interceptors = beanManager.resolveInterceptors(InterceptionType.AROUND_INVOKE, interceptorBindings);
                if (!interceptors.isEmpty()) {
                    List<InterceptorInvocationContext.InterceptorInvocation> chain = new ArrayList<>();
                    for (Interceptor<?> interceptor : interceptors) {
                        chain.add(new InterceptorInvocationContext.InterceptorInvocation(interceptor,
                                interceptorInstances.computeIfAbsent(interceptor, i -> beanManager.getReference(i, i.getBeanClass(), creationalContext))));
                    }
                    chains.put(method, chain);
                }
            }
        }
        return chains.isEmpty() ? Collections.emptyMap() : chains;
    }

    private static Annotation[] merge(List<Annotation> methodLevelBindings, List<Annotation> classLevelBindings) {
        Set<Class<? extends Annotation>> types = methodLevelBindings.stream().map(Annotation::annotationType).collect(Collectors.toSet());
        List<Annotation> merged = new ArrayList<>(methodLevelBindings);
        for (Annotation annotation : classLevelBindings) {
            if (!types.contains(annotation.annotationType())) {
                merged.add(annotation);
            }
        }
        return merged.toArray(new Annotation[]{});
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        List<InterceptorInvocationContext.InterceptorInvocation> chain = interceptorChains.get(method);
        if (chain != null) {
            // Invoke business method interceptors
            return new InterceptorInvocationContext(target, method, args, chain).proceed();
        } else {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable targetException = e.getTargetException();
                if (targetException != null) {
                    throw targetException;
                }

                throw e;
            }
        }
    }
}
