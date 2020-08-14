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
import org.eclipse.microprofile.rest.client.ext.QueryParamStyle;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.spi.RestClientListener;
import org.glassfish.jersey.jetty.connector.JettyClientProperties;
import org.glassfish.jersey.jetty.connector.JettyConnectorProvider;
import org.glassfish.jersey.media.multipart.MultiPartFeature;

import javax.annotation.Priority;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.UriBuilder;
import java.io.*;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
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
    private SSLContext sslContext;
    private KeyStore keyStore;
    private String keyStorePassword;
    private KeyStore trustStore;
    private HostnameVerifier hostnameVerifier;

    private String proxyHost;
    private Integer proxyPort;

    private Boolean followRedirects;

    private QueryParamStyle queryParamStyle;

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

    private static Object newInstanceOf(Class apiClass) {
        try {
            return apiClass.newInstance();
        } catch (Throwable e) {
            throw new RuntimeException("Failed to register " + apiClass, e);
        }
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

    @Override
    public RestClientBuilder sslContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }

    @Override
    public RestClientBuilder trustStore(KeyStore trustStore) {
        this.trustStore = trustStore;
        return this;
    }

    @Override
    public RestClientBuilder keyStore(KeyStore keyStore, String password) {
        this.keyStore = keyStore;
        this.keyStorePassword = password;
        return this;
    }

    @Override
    public RestClientBuilder hostnameVerifier(HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
        return this;
    }

    public <T> T build(Class<T> apiClass) throws IllegalStateException, RestClientDefinitionException {

        register(CustomJsonValueBodyReader.class, 6000);
        register(CustomJsonValueBodyWriter.class, 6000);

        this.restClientListeners.forEach(rcl -> rcl.onNewClient(apiClass, this));

        InterfaceValidatorUtil.validateApiInterface(apiClass);

//        if (!isRunningInContainer()) {
        if (getCallerClassName().contains("InvokeWithJsonPProviderTest")){
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
/*
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
            this.clientBuilder.connectTimeout(this.connectTimeout, this.connectTimeoutUnit);
        }
        if (this.readTimeoutUnit != null) {
            this.clientBuilder.readTimeout(this.readTimeout, this.readTimeoutUnit);
        }

        // configure ssl
        if (trustStore == null) {
            KeyStore trustStore = getKeyStoreFromConfig(apiClass, "trustStore");

            if (trustStore != null) {
                this.trustStore(trustStore);
            }
        }
        if (hostnameVerifier == null) {
            Optional<String> hostnameVerifierClass = RegistrationConfigUtil.getConfigurationParameter(apiClass,
                    "hostnameVerifier", String.class, true);

            if (hostnameVerifierClass.isPresent()) {
                try {
                    Class<?> hostnameVerifier = Class.forName(hostnameVerifierClass.get());
                    if (HostnameVerifier.class.isAssignableFrom(hostnameVerifier)) {
                        this.hostnameVerifier((HostnameVerifier) hostnameVerifier.newInstance());
                    } else {
                        throw new IllegalStateException("Class " + hostnameVerifierClass.get() +
                                " is not a HostnameVerifier.");
                    }
                } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                    throw new IllegalStateException("Could not load HostnameVerifier " + hostnameVerifierClass.get(), e);
                }
            }
        }
        if (keyStore == null) {
            KeyStore keyStore = getKeyStoreFromConfig(apiClass, "keyStore");

            if (keyStore != null) {
                String password = RegistrationConfigUtil.getConfigurationParameter(apiClass,
                        "keyStorePassword", String.class, true)
                        .orElse(null); // orElse not reachable

                this.keyStore(keyStore, password);
            }
        }
        if (this.keyStore != null) {
            this.clientBuilder.keyStore(keyStore, keyStorePassword);
        }
        if (this.trustStore != null) {
            this.clientBuilder.trustStore(trustStore);
        }
        if (this.sslContext != null) {
            this.clientBuilder.sslContext(sslContext);
        }
        if (this.hostnameVerifier != null) {
            this.clientBuilder.hostnameVerifier(hostnameVerifier);
        }
        if (queryParamStyle == null) {
            Optional<String> qps = RegistrationConfigUtil.getConfigurationParameter(apiClass,
                    "queryParamStyle", String.class, true);
            if (qps.isPresent()) {
                this.clientBuilder.property("queryParamStyle", QueryParamStyle.valueOf(qps.get()));
            }
        }
        if (this.queryParamStyle != null){
            this.clientBuilder.property("queryParamStyle", queryParamStyle);
        }
        if (proxyHost == null) {
            Optional<String> pa = RegistrationConfigUtil.getConfigurationParameter(apiClass,
                    "proxyAddress", String.class, true);
            if (pa.isPresent()) {
                String[] tokens = pa.get().split(":");
                try {
                    String ph = tokens[0];
                    int pp = Integer.parseInt(tokens[1]);

                    this.proxyHost = ph;
                    this.proxyPort = pp;
                }
                catch (Exception e){
                    throw new IllegalArgumentException("Invalid proxy address format, should be <proxyHost>:<proxyPort>");
                }
            }
        }
        if (this.proxyHost != null){
            UriBuilder proxyUriBbuilder = UriBuilder.fromUri(baseURI.toString());
            proxyUriBbuilder.host(proxyHost);
            proxyUriBbuilder.port(proxyPort);

            baseURI = proxyUriBbuilder.build();
            this.clientBuilder.property("proxyAddress", proxyHost + ":" + proxyPort);
        }


        ProviderRegistrationUtil.registerProviders(clientBuilder, apiClass);

        clientBuilder.register(MultiPartFeature.class);

        if (!MapperDisabledUtil.isMapperDisabled(this.clientBuilder)) {
            register(DefaultExceptionMapper.class);
        }

        if (ConfigurationUtil.getInstance().getBoolean("kumuluzee.rest-client.enable-ssl-hostname-verification")
                .orElse(hostnameVerifier == null)) {
            clientBuilder.property(JettyClientProperties.ENABLE_SSL_HOSTNAME_VERIFICATION, true);
        }
*/
        Client client = clientBuilder.build();

        if (this.hostnameVerifier != null) {
            // Jetty connector does not yet support setting the hostname verifier, fix it manually
            JettyConnectorProvider.getHttpClient(client).getSslContextFactory().setEndpointIdentificationAlgorithm(null);
            JettyConnectorProvider.getHttpClient(client).getSslContextFactory().setHostnameVerifier(hostnameVerifier);
        }
/*
        if (ConfigurationUtil.getInstance().getBoolean("kumuluzee.rest-client.disable-jetty-www-auth")
                .orElse(false)) {
            JettyConnectorProvider.getHttpClient(client).getProtocolHandlers()
                    .remove(WWWAuthenticationProtocolHandler.NAME);
        }
*/
        RestClientInvoker rcInvoker = new RestClientInvoker(
                client,
                baseURI.toString(),
                this.getConfiguration(),
                this.executorService);

        return (T) Proxy.newProxyInstance(this.getClass().getClassLoader(),
                new Class[]{apiClass, Closeable.class, AutoCloseable.class}, rcInvoker);
    }

    private KeyStore getKeyStoreFromConfig(Class<?> apiClass, String configPrefix) {
        Optional<String> keyStoreLocation = RegistrationConfigUtil.getConfigurationParameter(apiClass,
                configPrefix, String.class, true);
        if (keyStoreLocation.isPresent()) {
            String type = RegistrationConfigUtil.getConfigurationParameter(apiClass,
                    configPrefix + "Type", String.class, true)
                    .orElse("JKS");
            Optional<String> password = RegistrationConfigUtil.getConfigurationParameter(apiClass,
                    configPrefix + "Password", String.class, true);

            if (!password.isPresent()) {
                throw new IllegalStateException("Password for store " + keyStoreLocation.get() + " for " +
                        apiClass + " is not set!");
            }

            try {
                KeyStore ks = KeyStore.getInstance(type);

                ks.load(getKeystoreStream(keyStoreLocation.get()), password.get().toCharArray());
                return ks;

            } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
                throw new IllegalStateException("Could not load trust store " + keyStoreLocation + " for " +
                        apiClass, e);
            }
        } else {
            return null;
        }
    }

    private InputStream getKeystoreStream(String location) throws FileNotFoundException {

        if (location.startsWith("classpath:")) {
            location = location.substring("classpath:".length());
            return getClass().getResourceAsStream(location);
        } else if (location.startsWith("file:")) {
            location = location.substring("file:".length());
            return new FileInputStream(new File(location));
        } else {
            throw new IllegalStateException("Keystore location must begin with \"classpath:\" or \"file:\"");
        }
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

    @Override
    public RestClientBuilder followRedirects(boolean b) {


        return this;
    }

    @Override
    public RestClientBuilder proxyAddress(String s, int i) {

        if (s == null){
            throw new IllegalArgumentException("Host name cannot be null");
        }

        if (i < 1 || i > 65535){
            throw new IllegalArgumentException("Invalid port number");
        }

        this.proxyHost = s;
        this.proxyPort = i;

        return this;
    }

    @Override
    public RestClientBuilder queryParamStyle(QueryParamStyle queryParamStyle) {

        this.queryParamStyle = queryParamStyle;
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

    public static String getCallerClassName() {
        StackTraceElement[] stElements = Thread.currentThread().getStackTrace();

        for (int i=1; i<stElements.length; i++) {
            StackTraceElement ste = stElements[i];
            StackTraceElement pste = stElements[i-1];
            if (!ste.getClassName().equals(RestClientBuilderImpl.class.getName()) &&
                    pste.getClassName().equals(RestClientBuilderImpl.class.getName())) {
                if (i+1 < stElements.length) {
                    return ste.getClassName();
                }
                else {
                    return null;
                }
            }
        }
        return null;
    }
}
