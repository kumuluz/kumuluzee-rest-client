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
package com.kumuluz.ee.rest.client.mp.cdi;

import com.kumuluz.ee.rest.client.mp.util.ProviderRegistrationUtil;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.PassivationCapable;
import javax.enterprise.util.AnnotationLiteral;
import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Bean that creates a Rest Client using a {@link RestClientBuilder}.
 *
 * @author Urban Malc
 * @since 1.2.2
 */
public class InvokerDelegateBean implements Bean<Object>, PassivationCapable {

    private Class<?> restClientType;
    private Class<? extends Annotation> scope;

    public InvokerDelegateBean(Class<?> restClientType, Class<? extends Annotation> scope) {

        this.restClientType = restClientType;
        this.scope = scope;
    }

    @Override
    public Class<?> getBeanClass() {
        return restClientType;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return Collections.emptySet();
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public Object create(CreationalContext<Object> creationalContext) {

        RestClientBuilder restClientBuilder = RestClientBuilder.newBuilder();

        ProviderRegistrationUtil.registerProviders(restClientBuilder, restClientType);

        Object restClient = restClientBuilder.build(restClientType);

        return Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{restClientType},
                new InterceptorInvocationHandler(restClientType, restClient));
    }

    @Override
    public void destroy(Object instance, CreationalContext<Object> creationalContext) {

    }

    @Override
    public Set<Type> getTypes() {
        return Collections.singleton(restClientType);
    }

    @Override
    public Set<Annotation> getQualifiers() {
        Set<Annotation> qualifiers = new HashSet<>();
        qualifiers.add(new AnnotationLiteral<Default>() {});
        qualifiers.add(new AnnotationLiteral<Any>() {});
        qualifiers.add(RestClient.LITERAL);

        return qualifiers;
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return scope;
    }

    @Override
    public String getName() {
        return restClientType.getName();
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return Collections.emptySet();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    @Override
    public String getId() {
        return getName();
    }
}
