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

import com.kumuluz.ee.rest.client.mp.util.RegistrationConfigUtil;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

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

    private final Set<AnnotatedType<?>> classes;

    public RestClientExtension() {
        this.classes = new HashSet<>();
    }

    public <T> void processAnnotatedType(@Observes @WithAnnotations(RegisterRestClient.class) ProcessAnnotatedType<T> anType) {
        Class<T> typeDef = anType.getAnnotatedType().getJavaClass();

        if (!typeDef.isInterface()) {
            throw new IllegalArgumentException("Rest client needs to be interface: " + typeDef);
        }

        this.addAnnotatedType(anType.getAnnotatedType());
        anType.veto();
    }

    public void afterBean(@Observes AfterBeanDiscovery afterBeanDiscovery) {
        for (AnnotatedType<?> anType : this.classes) {

            Class<? extends Annotation> scopeClass = resolveScope(anType.getJavaClass());
            afterBeanDiscovery.addBean(new InvokerDelegateBean(anType.getJavaClass(), scopeClass));
        }
    }

    private void addAnnotatedType(AnnotatedType<?> annotatedType) {
        if (this.classes.stream().map(AnnotatedType::getJavaClass).anyMatch(annotatedType.getJavaClass()::equals)) {
            return;
        }

        this.classes.add(annotatedType);
    }

    private Class<? extends Annotation> resolveScope(Class<?> interfaceClass) {

        Optional<String> scopeConfig = RegistrationConfigUtil.getConfigurationParameter(interfaceClass, "scope",
                String.class, true);

        if (scopeConfig.isPresent()) {
            try {
                //noinspection unchecked
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
