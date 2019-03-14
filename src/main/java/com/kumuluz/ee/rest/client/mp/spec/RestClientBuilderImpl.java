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
package com.kumuluz.ee.rest.client.mp.spec;

import com.kumuluz.ee.configuration.utils.ConfigurationUtil;
import com.kumuluz.ee.rest.client.mp.invoker.RestClientInvoker;
import com.kumuluz.ee.rest.client.mp.providers.CustomJsonValueBodyReader;
import com.kumuluz.ee.rest.client.mp.providers.CustomJsonValueBodyWriter;
import com.kumuluz.ee.rest.client.mp.util.*;
import org.eclipse.jetty.client.WWWAuthenticationProtocolHandler;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.eclipse.microprofile.rest.client.ext.AsyncInvocationInterceptorFactory;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.spi.RestClientListener;
import org.glassfish.jersey.jetty.connector.JettyConnectorProvider;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Configuration;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Implementation of MicroProfile {@link RestClientBuilder}.
 *
 * @author Miha Jamsek
 * @since 1.0.1
 */
public class RestClientBuilderImpl implements RestClientBuilder {

    private static final Logger LOG = Logger.getLogger(RestClientBuilderImpl.class.getSimpleName());

    private ClientBuilder clientBuilder;
    private URI baseURI;
    private ExecutorService executorService;
    private long connectTimeout;
    private TimeUnit connectTimeoutUnit;
    private long readTimeout;
    private TimeUnit readTimeoutUnit;

    private Set<Object> customProviders;
    private Map<Class, Map<Class<?>, Integer>> customProvidersContracts;

    private List<RestClientListener> restClientListeners;

    RestClientBuilderImpl() {
        clientBuilder = ClientBuilder.newBuilder();

        customProviders = new HashSet<>();
        customProvidersContracts = new HashMap<>();

        restClientListeners = new ArrayList<>();
        ServiceLoader.load(RestClientListener.class).iterator().forEachRemaining(rcl -> restClientListeners.add(rcl));
    }

    @Override
    public RestClientBuilder baseUrl(URL url) {
        try {
            this.baseURI = url.toURI();
            return this;
        } catch (URISyntaxException exc) {
            throw new RuntimeException(exc.getMessage());
        }
    }

    @Override
    public RestClientBuilder baseUri(URI uri) {
        this.baseURI = uri;
        return this;
    }

    @Override
    public RestClientBuilder connectTimeout(long l, TimeUnit timeUnit) {
        this.connectTimeout = l;
        this.connectTimeoutUnit = timeUnit;
        return this;
    }

    @Override
    public RestClientBuilder readTimeout(long l, TimeUnit timeUnit) {
        this.readTimeout = l;
        this.readTimeoutUnit = timeUnit;
        return this;
    }

    @Override
    public RestClientBuilder executorService(ExecutorService executorService) {
        if (executorService == null) {
            throw new IllegalArgumentException("ExecutorService cannot be null. If you wish to use default executor " +
                    "service, do not call this method.");
        }

        this.executorService = executorService;
        return this;
    }

    public <T> T build(Class<T> apiClass) throws IllegalStateException, RestClientDefinitionException {

        register(CustomJsonValueBodyReader.class, 6000);
        register(CustomJsonValueBodyWriter.class, 6000);

        this.restClientListeners.forEach(rcl -> rcl.onNewClient(apiClass, this));

        InterfaceValidatorUtil.validateApiInterface(apiClass);

        if (!isRunningInContainer()) {
            // fixes exception in InvokeWithJsonPProviderTest, which happens when @BeforeTest gets executed on client
            // see: https://developer.jboss.org/thread/198706
            // CDI BeanManager is not accessible outside container and getBeanManager throws exception
            // this shouldn't be a problem because Rest Client cannot be used without KumuluzEE
            // if running TCK, this log message should be ignored
            LOG.severe("Rest Client is running outside container, build method cannot be used.");
            return null;
        }

        return this.create(apiClass);
    }

    private <T> T create(Class<T> apiClass) {

        if (baseURI == null) {
            Optional<URL> baseUrl = RegistrationConfigUtil.getConfigurationParameter(apiClass, "url",
                    URL.class, true);
            Optional<URI> baseUri = RegistrationConfigUtil.getConfigurationParameter(apiClass, "uri",
                    URI.class, true);

            if (baseUri.isPresent()) {
                this.baseUri(baseUri.get());
            } else if (baseUrl.isPresent()) {
                this.baseUrl(baseUrl.get());
            }
        }
        if (baseURI == null) {
            if (apiClass.getAnnotation(RegisterRestClient.class) != null) {
                String baseUri = apiClass.getAnnotation(RegisterRestClient.class).baseUri();
                if (!baseUri.isEmpty()) {
                    try {
                        this.baseUri(new URI(baseUri));
                    } catch (URISyntaxException ignored) {
                    }
                }
            }
        }
        if (baseURI == null) {
            throw new IllegalStateException("Base URL for " + apiClass + " is not set!");
        }

        if (connectTimeoutUnit == null) {
            Optional<Long> connectTimeout = RegistrationConfigUtil.getConfigurationParameter(apiClass,
                    "connectTimeout", Long.class, true);
            if (connectTimeout.isPresent()) {
                this.connectTimeout = connectTimeout.get();
                this.connectTimeoutUnit = TimeUnit.MILLISECONDS;
            }
        }
        if (readTimeoutUnit == null) {
            Optional<Long> readTimeout = RegistrationConfigUtil.getConfigurationParameter(apiClass,
                    "readTimeout", Long.class, true);
            if (readTimeout.isPresent()) {
                this.readTimeout = readTimeout.get();
                this.readTimeoutUnit = TimeUnit.MILLISECONDS;
            }
        }
        if (this.connectTimeoutUnit != null) {
            this.clientBuilder.connectTimeout(TimeUnit.MILLISECONDS.convert(connectTimeout, connectTimeoutUnit) / 4,
                    TimeUnit.MILLISECONDS);
        }
        if (this.readTimeoutUnit != null) {
            this.clientBuilder.readTimeout(readTimeout, readTimeoutUnit);
        }

        ProviderRegistrationUtil.registerProviders(clientBuilder, apiClass);

        if (!MapperDisabledUtil.isMapperDisabled(this.clientBuilder)) {
            register(DefaultExceptionMapper.class);
        }

        Client client = clientBuilder.build();

        if (ConfigurationUtil.getInstance().getBoolean("kumuluzee.rest-client.disable-jetty-www-auth")
                .orElse(false)) {
            JettyConnectorProvider.getHttpClient(client).getProtocolHandlers()
                    .remove(WWWAuthenticationProtocolHandler.NAME);
        }

        RestClientInvoker rcInvoker = new RestClientInvoker(
                client,
                baseURI.toString(),
                this.getConfiguration(),
                this.executorService);

        return (T) Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[] {apiClass}, rcInvoker);
    }

    @Override
    public Configuration getConfiguration() {
        return new ExtendedConfiguration(this.clientBuilder.getConfiguration(),
                this.customProviders, this.customProvidersContracts);
    }

    @Override
    public RestClientBuilder property(String s, Object o) {
        this.clientBuilder.property(s, o);
        return this;
    }

    private static Object newInstanceOf(Class apiClass) {
        try {
            return apiClass.newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to register " + apiClass, e);
        }
    }

    @Override
    public RestClientBuilder register(Class<?> aClass) {
        this.register(newInstanceOf(aClass));
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> aClass, int i) {
        this.register(newInstanceOf(aClass), i);
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> aClass, Class<?>... classes) {
        this.register(newInstanceOf(aClass), classes);
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> aClass, Map<Class<?>, Integer> map) {
        this.register(newInstanceOf(aClass), map);
        return this;
    }


    @Override
    public RestClientBuilder register(Object o) {

        if (o instanceof ResponseExceptionMapper) {
            ResponseExceptionMapper mapper = (ResponseExceptionMapper) o;
            register(mapper, mapper.getPriority());
        }
        if (o instanceof AsyncInvocationInterceptorFactory) {
            registerCustomProvider(o, AsyncInvocationInterceptorFactory.class, getProviderPriority(o));
        }

        this.clientBuilder.register(o);

        return this;
    }

    @Override
    public RestClientBuilder register(Object o, int i) {

        if (o instanceof ResponseExceptionMapper) {
            registerCustomProvider(o, ResponseExceptionMapper.class, i);
        }
        if (o instanceof AsyncInvocationInterceptorFactory) {
            registerCustomProvider(o, AsyncInvocationInterceptorFactory.class, i);
        }

        this.clientBuilder.register(o, i);

        return this;
    }

    @Override
    public RestClientBuilder register(Object o, Class<?>... classes) {
        List<Class<?>> nonCustomProviders = new ArrayList<>();

        for (Class<?> clazz : classes) {
            if (clazz.isAssignableFrom(ResponseExceptionMapper.class)) {
                int priority = Priorities.USER;
                if (o instanceof ResponseExceptionMapper) {
                    priority = ((ResponseExceptionMapper) o).getPriority();
                }
                registerCustomProvider(o, ResponseExceptionMapper.class, priority);
            } else if (clazz.isAssignableFrom(AsyncInvocationInterceptorFactory.class)) {
                registerCustomProvider(o, AsyncInvocationInterceptorFactory.class, Priorities.USER);
            } else {
                nonCustomProviders.add(clazz);
            }
        }

        this.clientBuilder.register(o, nonCustomProviders.toArray(new Class[]{}));
        return this;
    }

    @Override
    public RestClientBuilder register(Object o, Map<Class<?>, Integer> map) {

        Map<Class<?>, Integer> nonCustomProviders = new HashMap<>();

        for (Class<?> clazz : map.keySet()) {
            if (clazz.isAssignableFrom(ResponseExceptionMapper.class)) {
                registerCustomProvider(o, ResponseExceptionMapper.class, map.get(clazz));
            } else if (clazz.isAssignableFrom(AsyncInvocationInterceptorFactory.class)) {
                registerCustomProvider(o, AsyncInvocationInterceptorFactory.class, map.get(clazz));
            } else {
                nonCustomProviders.put(clazz, map.get(clazz));
            }
        }

        this.clientBuilder.register(o, nonCustomProviders);
        return this;
    }

    private void registerCustomProvider(Object providerImpl, Class providerClass, Integer priority) {
        this.customProviders.add(providerImpl);

        Map<Class<?>, Integer> contracts = this.customProvidersContracts
                .computeIfAbsent(providerImpl.getClass(), k -> new HashMap<>());

        contracts.put(providerClass, priority);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private boolean isRunningInContainer() {
        try {
            ConfigurationUtil.getInstance();
        } catch (IllegalStateException e) {
            return false;
        }

        return true;
    }

    private int getProviderPriority(Object provider) {
        Priority p = provider.getClass().getAnnotation(Priority.class);

        if (p != null) {
            return p.value();
        }

        return Priorities.USER;
    }
}
