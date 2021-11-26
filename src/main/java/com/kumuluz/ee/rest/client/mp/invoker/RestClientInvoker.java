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
package com.kumuluz.ee.rest.client.mp.invoker;

import com.kumuluz.ee.rest.client.mp.providers.IncomingHeadersInterceptor;
import com.kumuluz.ee.rest.client.mp.util.BeanParamProcessorUtil;
import com.kumuluz.ee.rest.client.mp.util.ClientHeaderParamUtil;
import com.kumuluz.ee.rest.client.mp.util.DefaultExecutorServiceUtil;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.ext.AsyncInvocationInterceptor;
import org.eclipse.microprofile.rest.client.ext.AsyncInvocationInterceptorFactory;
import org.eclipse.microprofile.rest.client.ext.ClientHeadersFactory;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.Boundary;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.spi.CDI;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.*;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Invokes rest client for methods of interfaces annotated with
 * {@link org.eclipse.microprofile.rest.client.inject.RegisterRestClient}.
 *
 * @author Miha Jamsek
 * @since 1.0.1
 */
public class RestClientInvoker implements InvocationHandler {

    private Client client;
    private String baseURI;
    private Configuration configuration;
    private ExecutorService executorService;
    private AtomicBoolean closed;

    public RestClientInvoker(Client client, String baseURI, Configuration configuration,
                             ExecutorService executorService) {
        this.client = client;

        // Jersey uses lazy initialization for Feature configuration, MP spec requires Features to be configured
        // at registration. This call forces Jersey to initialize Features
        try {
            this.client.target("/").request().buildGet();
        } catch (Exception ignored) {
        }

        this.baseURI = baseURI;
        this.configuration = configuration;
        this.executorService = executorService;
        this.closed = new AtomicBoolean(false);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        if (method.getName().equals("close") && args == null) {
            close();
            return null;
        }

        if (this.closed.get()) {
            throw new IllegalStateException("Rest Client is closed.");
        }

        StringBuilder serverURL = determineEndpointUrl(method);
        // if subresource exists, return RestClient for subresource type
        if (isSubResource(method.getReturnType())) {
            Class subresourceType = method.getReturnType();
            if (subresourceType.isAnnotationPresent(Path.class)) {
                Path subResourcePathAnnotation = (Path) subresourceType.getAnnotation(Path.class);
                addPathValue(serverURL, subResourcePathAnnotation);
            }
            String subresourceURL = serverURL.toString();

            RestClientBuilder builder = RestClientBuilder.newBuilder();

            configuration.getInstances().forEach(builder::register);
            configuration.getProperties().forEach(builder::property);

            return builder
                    .baseUrl(new URL(subresourceURL))
                    .build(method.getReturnType());
        }

        String httpMethod = determineMethod(method);
        if (httpMethod == null) {
            throw new RuntimeException(String.format("Unknown HTTP method at %s", method));
        }
        ParamInfo paramInfo = determineParamInfo(method, args);
        UriBuilder uriBuilder = UriBuilder.fromUri(serverURL.toString());
        for (Map.Entry<String, Object> entry : paramInfo.getQueryParameterValues().entrySet()) {
            if (entry.getValue() != null) {
                Object object = entry.getValue();
                if (object instanceof Collection) {
                    Collection collection = (Collection) object;
                    if (!collection.isEmpty())
                        uriBuilder.queryParam(entry.getKey(), collection.toArray());
                } else {
                    uriBuilder.queryParam(entry.getKey(), object);
                }
            }
        }

        Map<String, Object> pathParams = paramInfo.getPathParameterValues();
        replacePathParamParameters(pathParams);
        URI uri = uriBuilder.buildFromMap(pathParams);

        MultivaluedMap<String, String> headers = paramInfo.getHeaderValues();
        ClientHeaderParamUtil.collectClientHeaderParams(method).forEach(headers::addAll);

        RegisterClientHeaders registerClientHeaders = getMethodOrClassAnnotation(method, RegisterClientHeaders.class);
        if (registerClientHeaders != null) {

            Instance<? extends ClientHeadersFactory> factoryBean = CDI.current().select(registerClientHeaders.value());
            if (factoryBean.isResolvable()) {
                headers = factoryBean.get().update(getIncomingHeaders(), headers);
            } else {
                ClientHeadersFactory clientHeadersFactory = registerClientHeaders.value().newInstance();
                headers = clientHeadersFactory.update(getIncomingHeaders(), headers);
            }
        }

        MultivaluedMap<String, Object> headersObj = new MultivaluedHashMap<>();
        headers.forEach((k, v) -> v.forEach(v1 -> headersObj.add(k, v1)));

        Client requestClient = this.client;

        Timeout timeout = getMethodOrClassAnnotation(method, Timeout.class);
        if (timeout != null) {
            ClientBuilder cb = ClientBuilder.newBuilder().withConfig(client.getConfiguration());
            cb.connectTimeout(Duration.of(timeout.value(), timeout.unit()).toMillis(), TimeUnit.MILLISECONDS);
            cb.readTimeout(Duration.of(timeout.value(), timeout.unit()).toMillis(), TimeUnit.MILLISECONDS);
            requestClient = cb.build();
        }

        Invocation.Builder request = requestClient
                .target(uri)
                .request()
                .headers(headersObj)
                .property("org.eclipse.microprofile.rest.client.invokedMethod", method);

        Consumes consumes = getMethodOrClassAnnotation(method, Consumes.class);
        Produces produces = getMethodOrClassAnnotation(method, Produces.class);
        String payloadType = MediaType.APPLICATION_JSON; // default
        if (consumes != null) {
            payloadType = String.join(",", consumes.value());
            request.header(HttpHeaders.CONTENT_TYPE, payloadType);
        }
        String acceptHeader = MediaType.APPLICATION_JSON;
        if (produces != null) {
            acceptHeader = String.join(",", produces.value());
        }
        request.header(HttpHeaders.ACCEPT, acceptHeader);

        for (Map.Entry<String, Object> entry : paramInfo.getCookieParameterValues().entrySet()) {
            request = request.cookie(entry.getKey(), (String) entry.getValue());
        }

        Invocation invocation;
        if (paramInfo.getPayload() != null) {
            invocation = request.build(httpMethod, Entity.entity(paramInfo.getPayload(), payloadType));
        } else if (!paramInfo.getFormDataParameterValues().isEmpty()) {
            FormDataMultiPart multiPart = new FormDataMultiPart();

            paramInfo.getFormDataParameterValues().forEach((name, val) -> {
                if (val instanceof String) {
                    multiPart.field(name, (String) val);
                } else if (val instanceof BodyPart) {
                    multiPart.bodyPart((BodyPart) val);
                }
            });

            MediaType mediaType = MediaType.MULTIPART_FORM_DATA_TYPE;
            mediaType = Boundary.addBoundary(mediaType);

            invocation = request.build(httpMethod, Entity.entity(multiPart, mediaType));
        } else {
            invocation = request.build(httpMethod);
        }

        return invokeRequest(invocation, method);
    }

    private void close() {
        if (closed.compareAndSet(false, true)) {
            this.client.close();
        }
    }

    private MultivaluedMap<String, String> getIncomingHeaders() {
        try {
            IncomingHeadersInterceptor interceptor = CDI.current().select(IncomingHeadersInterceptor.class).get();
            return interceptor.getIncomingHeaders();
        } catch (Exception ignored) {
        }

        return new MultivaluedHashMap<>();
    }

    private Object invokeRequest(Invocation invocation, Method method) throws Throwable {

        Type returnType = method.getGenericReturnType();

        if (returnType instanceof ParameterizedType &&
                ((ParameterizedType) returnType).getRawType().equals(CompletionStage.class)) {

            // apply interceptors
            List<AsyncInvocationInterceptor> interceptors = new ArrayList<>();
            getProviders(AsyncInvocationInterceptorFactory.class).forEach(f -> interceptors.add(f.newInterceptor()));
            interceptors.forEach(AsyncInvocationInterceptor::prepareContext);

            CompletableFuture<Object> cf = new CompletableFuture<>();

            ExecutorService executor = this.executorService;

            if (executor == null) {
                executor = DefaultExecutorServiceUtil.getExecutorService();
            }

            executor.submit(() -> {
                interceptors.forEach(AsyncInvocationInterceptor::applyContext);

                Response response = invocation.invoke();

                try {
                    handleExceptionMapping(response, Arrays.asList(method.getExceptionTypes()));
                } catch (Throwable throwable) {
                    cf.completeExceptionally(throwable);
                }

                Type[] typeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
                if (typeArguments.length != 1) {
                    cf.completeExceptionally(new IllegalArgumentException("Could not resolve type argument: " +
                            Arrays.toString(typeArguments)));
                }

                interceptors.forEach(AsyncInvocationInterceptor::removeContext);

                cf.complete(processResponse(typeArguments[0], response));
            });

            return cf;
        } else {

            Response response;

            try {
                response = invocation.invoke();
            } catch (ResponseProcessingException e) {
                response = e.getResponse();
            }

            handleExceptionMapping(response, Arrays.asList(method.getExceptionTypes()));

            return processResponse(returnType, response);
        }
    }

    private Object processResponse(Type returnType, Response response) {
        if (!void.class.equals(returnType)) {
            if (returnType.equals(Response.class)) {
                // get raw response
                return response;
            } else {
                // get user defined entity
                return response.readEntity(new GenericType<>(returnType));
            }
        }
        return null;
    }

    private void replacePathParamParameters(Map<String, Object> pathParams) {
        List<ParamConverterProvider> providers = getProviders(ParamConverterProvider.class);
        for (String pathParamKey : pathParams.keySet()) {
            for (ParamConverterProvider provider : providers) {
                ParamConverter converter = provider.getConverter(String.class, Object.class, new Annotation[]{});
                pathParams.put(pathParamKey, converter.toString(pathParams.get(pathParamKey)));
            }
        }
    }

    private <T> List<T> getProviders(Class<T> providerType) {
        List<LocalProviderInfo<T>> ls = new ArrayList<>();

        for (Object provider : configuration.getInstances()) {
            Integer priority = configuration.getContracts(provider.getClass()).get(providerType);
            if (priority != null && providerType.isInstance(provider)) {
                ls.add(new LocalProviderInfo<>(providerType.cast(provider), priority));
            }
        }

        for (Class providerClass : configuration.getClasses()) {
            Integer priority = configuration.getContracts(providerClass).get(providerType);
            if (priority != null && providerType.isAssignableFrom(providerClass)) {
                try {
                    ls.add(new LocalProviderInfo<>(providerType.cast(providerClass.newInstance()), priority));
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException("Failed to create new instance of "
                            + providerType + ": " + providerClass, e);
                }
            }
        }

        return ls.stream().sorted(Comparator.comparingInt(LocalProviderInfo::getPriority))
                .map(LocalProviderInfo::getLocalProvider).collect(Collectors.toList());
    }

    private void handleExceptionMapping(Response response, List<Class<?>> exceptionTypes) throws Throwable {
        int status = response.getStatus();
        MultivaluedMap<String, Object> headers = response.getHeaders();

        for (ResponseExceptionMapper mapper : getProviders(ResponseExceptionMapper.class)) {
            if (mapper.handles(status, headers)) {
                Throwable throwable = mapper.toThrowable(response);
                if (throwable != null) {
                    throwException(throwable, exceptionTypes);
                }
            }
        }
    }

    private void throwException(Throwable throwable, List<Class<?>> exceptionTypes) throws Throwable {
        if (throwable instanceof RuntimeException || throwable instanceof Error) {
            throw throwable;
        }

        for (Class<?> exceptionType : exceptionTypes) {
            if (exceptionType.isAssignableFrom(throwable.getClass())) {
                throw throwable;
            }
        }
    }

    private String determineMethod(Method method) {
        if (method.getAnnotation(GET.class) != null) {
            return HttpMethod.GET;
        }
        if (method.getAnnotation(PUT.class) != null) {
            return HttpMethod.PUT;
        }
        if (method.getAnnotation(POST.class) != null) {
            return HttpMethod.POST;
        }
        if (method.getAnnotation(DELETE.class) != null) {
            return HttpMethod.DELETE;
        }
        if (method.getAnnotation(OPTIONS.class) != null) {
            return HttpMethod.OPTIONS;
        }
        if (method.getAnnotation(HEAD.class) != null) {
            return HttpMethod.HEAD;
        }
        // custom http method
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(HttpMethod.class)) {
                return annotation.annotationType().getSimpleName();
            }
        }

        return null;
    }

    private ParamInfo determineParamInfo(Method method, Object[] args) {
        ParamInfo result = new ParamInfo();
        int paramIndex = 0;
        Object argumentInstance = null;
        for (Annotation[] annotatios : method.getParameterAnnotations()) {
            boolean jaxRSAnnotationFound = false;
            for (Annotation annotation : annotatios) {
                if (PathParam.class.equals(annotation.annotationType())) {
                    result.addPathParameter(((PathParam) annotation).value(), args[paramIndex]);
                    jaxRSAnnotationFound = true;
                }
                if (QueryParam.class.equals(annotation.annotationType())) {
                    result.addQueryParameter(((QueryParam) annotation).value(), args[paramIndex]);
                    jaxRSAnnotationFound = true;
                }
                if (HeaderParam.class.equals(annotation.annotationType())) {
                    result.addHeader(((HeaderParam) annotation).value(), (String) args[paramIndex]);
                    jaxRSAnnotationFound = true;
                }
                if (CookieParam.class.equals(annotation.annotationType())) {
                    result.addCookieParameter(((CookieParam) annotation).value(), args[paramIndex]);
                    jaxRSAnnotationFound = true;
                }
                if (BeanParam.class.equals(annotation.annotationType())) {
                    argumentInstance = args[paramIndex];
                    jaxRSAnnotationFound = true;
                }
                if (FormDataParam.class.equals(annotation.annotationType())) {
                    result.addFormDataParameter(((FormDataParam) annotation).value(), args[paramIndex]);
                    jaxRSAnnotationFound = true;
                }
                if (FormParam.class.equals(annotation.annotationType())) {
                    result.addFormDataParameter(((FormParam) annotation).value(), args[paramIndex]);
                    jaxRSAnnotationFound = true;
                }
            }

            if (!jaxRSAnnotationFound) {
                result.setPayload(args[paramIndex]);
            }
            paramIndex++;
        }

        for (Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(BeanParam.class)) {
                BeanParamProcessorUtil beanParamProcessor = new BeanParamProcessorUtil(parameter);
                result = beanParamProcessor.getBeanParams(result, argumentInstance);
            }
        }

        return result;
    }

    private StringBuilder determineEndpointUrl(Method method) {
        StringBuilder serverUrl = new StringBuilder();
        serverUrl.append(baseURI);
        Path classAnnotation = method.getDeclaringClass().getAnnotation(Path.class);
        addPathValue(serverUrl, classAnnotation);
        Path methodAnnotation = method.getAnnotation(Path.class);
        addPathValue(serverUrl, methodAnnotation);
        return serverUrl;
    }

    private void addPathValue(StringBuilder serverUrl, Path methodAnnotation) {
        if (methodAnnotation != null) {
            String value = methodAnnotation.value();

            if (serverUrl.toString().endsWith("/")) {
                if (!value.isEmpty() && !value.equals("/")) {
                    if (value.startsWith("/")) {
                        serverUrl.append(value.substring(1));
                    } else {
                        serverUrl.append(value);
                    }
                }
            } else {
                if (!value.isEmpty() && !value.equals("/")) {
                    if (value.startsWith("/")) {
                        serverUrl.append(value);
                    } else {
                        serverUrl.append("/").append(value);
                    }
                }
            }
        }
    }

    private <T extends Annotation> T getMethodOrClassAnnotation(Method m, Class<T> tClass) {
        T annotation = m.getAnnotation(tClass);

        if (annotation == null) {
            annotation = m.getDeclaringClass().getAnnotation(tClass);
        }

        return annotation;
    }

    private boolean isSubResource(Class<?> clazz) {
        if (clazz.isInterface()) {
            if (clazz.isAssignableFrom(JsonObject.class) ||
                    clazz.isAssignableFrom(JsonArray.class) ||
                    clazz.isAssignableFrom(CompletionStage.class)) {
                return false;
            }

            return true;
        }
        return false;
    }

}
