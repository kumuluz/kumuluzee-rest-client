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

import com.kumuluz.ee.rest.client.mp.proxy.RestClientProxyFactory;
import com.kumuluz.ee.rest.client.mp.util.ProviderRegistrationUtil;
import com.kumuluz.ee.rest.client.mp.util.RegistrationConfigUtil;
import org.apache.deltaspike.core.api.literal.AnyLiteral;
import org.apache.deltaspike.core.api.literal.DefaultLiteral;
import org.apache.deltaspike.core.util.bean.BeanBuilder;
import org.apache.deltaspike.partialbean.impl.PartialBeanProxyFactory;
import org.apache.deltaspike.proxy.api.DeltaSpikeProxyContextualLifecycle;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.*;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.*;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * CDI {@link Extension} that adds dynamically created beans from interfaces annotated with {@link RegisterRestClient}.
 *
 * @author Miha Jamsek
 * @since 1.0.1
 */

public class RestClientExtension implements Extension {

    private Set<AnnotatedType> classes;

    public RestClientExtension() {
        this.classes = new HashSet<>();
    }

    public <T> void processAnnotatedType(@Observes @WithAnnotations(RegisterRestClient.class) ProcessAnnotatedType<T> anType, BeanManager beanManager) {
        Class<T> typeDef = anType.getAnnotatedType().getJavaClass();

        if (!typeDef.isInterface()) {
            throw new IllegalArgumentException("Rest client needs to be interface: " + typeDef);
        }

        this.addAnnotatedType(anType.getAnnotatedType());
        anType.veto();
    }

    public <T> void afterBean(@Observes AfterBeanDiscovery afterBeanDiscovery, BeanManager beanManager) {
        for (AnnotatedType anType : this.classes) {
            DeltaSpikeProxyContextualLifecycle<T, InjectableRestClientHandler> lifecycle = new DeltaSpikeProxyContextualLifecycle<>(
                    (Class<T>) anType.getJavaClass(),
                    InjectableRestClientHandler.class,
                    RestClientProxyFactory.getInstance(),
                    beanManager
            );

            Class<? extends Annotation> scopeClass = resolveScope(anType.getJavaClass());

            BeanBuilder<T> beanBuilder = new BeanBuilder<T>(beanManager)
                    .readFromType((AnnotatedType<T>) anType)
                    .qualifiers(new RestClient.RestClientLiteral(), new DefaultLiteral(), new AnyLiteral())
                    .passivationCapable(true)
                    .scope(scopeClass)
                    .beanLifecycle(lifecycle);

            afterBeanDiscovery.addBean(beanBuilder.create());
        }
    }

    public void afterDeploymentValidation(@Observes AfterDeploymentValidation event) {
        for (AnnotatedType anType : this.classes) {
            try {
                // create invoker and add it to cache
                RestClientBuilder restClientBuilder = RestClientBuilder.newBuilder();

                ProviderRegistrationUtil.registerProviders(restClientBuilder, anType.getJavaClass());

                Object restClientInvoker = restClientBuilder.build(anType.getJavaClass());
                InjectableRestClientHandler.addRestClientInvoker(anType.getJavaClass(), restClientInvoker);
            } catch (Exception e) {
                InjectableRestClientHandler.addRestClientInvokerException(anType.getJavaClass(),
                        new RestClientDefinitionException(e));
            }
        }
    }

    private void addAnnotatedType(AnnotatedType annotatedType) {
        if (this.classes.stream().map(AnnotatedType::getJavaClass).anyMatch(annotatedType.getJavaClass()::equals)) {
            return;
        }

        this.classes.add(annotatedType);
    }

    private Class<? extends Annotation> resolveScope(Class interfaceClass) {

        Optional<String> scopeConfig = RegistrationConfigUtil.getConfigurationParameter(interfaceClass, "scope",
                String.class, true);

        if (scopeConfig.isPresent()) {
            try {
                return (Class<? extends Annotation>) Class.forName(scopeConfig.get());
            } catch (ClassNotFoundException e) {
                return Dependent.class;
            }
        } else {

            if (interfaceClass.isAnnotationPresent(RequestScoped.class)) {
                return RequestScoped.class;
            } else if (interfaceClass.isAnnotationPresent(ApplicationScoped.class)) {
                return ApplicationScoped.class;
            } else if (interfaceClass.isAnnotationPresent(SessionScoped.class)) {
                return SessionScoped.class;
            } else if (interfaceClass.isAnnotationPresent(ConversationScoped.class)) {
                return ConversationScoped.class;
            } else if (interfaceClass.isAnnotationPresent(Singleton.class)) {
                return Singleton.class;
            } else {
                return Dependent.class;
            }
        }

    }
}
