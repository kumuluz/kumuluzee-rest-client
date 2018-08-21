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

import com.kumuluz.ee.rest.client.mp.util.BeanParamProcessorUtil;
import com.kumuluz.ee.rest.client.mp.util.ProviderRegistrationUtil;
import org.eclipse.jetty.client.HttpResponseException;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Miha Jamsek
 */

public class RestClientInvoker implements InvocationHandler {
	private Client client;
	private String baseURI;
	private List<LocalProviderInfo> localProviderInstances;
	
	public RestClientInvoker(Client client, String baseURI, List<LocalProviderInfo> localProviderInstances) {
		this.client = client;
		this.baseURI = baseURI;
		this.localProviderInstances = localProviderInstances;
	}
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		
		StringBuilder serverURL = determineEndpointUrl(method);
		// ce ima subresource vrni zbildan restclient iz returntype
		if (isSubResource(method.getReturnType())) {
			Class subresourceType = method.getReturnType();
			if (subresourceType.isAnnotationPresent(Path.class)) {
				Path subResourcePathAnnotation = (Path) subresourceType.getAnnotation(Path.class);
				addPathValue(serverURL, subResourcePathAnnotation);
			}
			String subresourceURL = serverURL.toString();
			return RestClientBuilder.newBuilder()
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
			uriBuilder.queryParam(entry.getKey(), entry.getValue());
		}
		
		Map<String, Object> pathParams = paramInfo.getPathParameterValues();
		replacePathParamParameters(pathParams);
		String url = uriBuilder.buildFromMap(pathParams).toString();
		
		
		Invocation.Builder request = client.target(url).request().headers(paramInfo.getHeaderValues());
		
		for (Map.Entry<String, Object> entry : paramInfo.getCookieParameterValues().entrySet()) {
			request = request.cookie(entry.getKey(), (String) entry.getValue());
		}
		
		Invocation invocation;
		if (paramInfo.getPayload() != null) {
			invocation = request.build(httpMethod, Entity.entity(paramInfo.getPayload(), MediaType.APPLICATION_JSON));
		} else {
			invocation = request.build(httpMethod);
		}
		
		Object result = null;
		Response response;
		
		try {
			
			response = invocation.invoke();
			
		} catch (ResponseProcessingException e) {
			
			response = e.getResponse();
			
		} catch (ProcessingException e2) {
			// TODO: get response from processing exception and return it.
			
			// get root cause
			Throwable cause = e2.getCause();
			while (cause.getCause() != null) {
				cause = cause.getCause();
			}
			
			if (cause instanceof HttpResponseException) {
				response = getResponseFromHttpResponseException((HttpResponseException) cause);
			} else {
				response = Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e2.getMessage()).build();
			}
		}
		
		handleExceptionMapping(response, Arrays.asList(method.getExceptionTypes()));
		
		if (!void.class.equals(method.getReturnType())) {
			if (method.getReturnType().equals(Response.class)) {
				// get raw response
				result = response;
			} else {
				// get user defined entity
				try {
					result = readResponseEntity(response, method.getReturnType());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		
		return result;
	}
	
	private void replacePathParamParameters(Map<String, Object> pathParams) {
		List<ParamConverterProvider> providers = ProviderRegistrationUtil.getParamConverterProviders();
		for(String pathParamKey : pathParams.keySet()) {
			for(ParamConverterProvider provider : providers) {
				ParamConverter converter = provider.getConverter(String.class, Object.class, new Annotation[]{});
				pathParams.put(pathParamKey, converter.toString(pathParams.get(pathParamKey)));
			}
		}
	}
	
	private Response getResponseFromHttpResponseException(HttpResponseException ex) {
		org.eclipse.jetty.client.api.Response jettyResponse = ex.getResponse();
		Response.ResponseBuilder response = Response.status(jettyResponse.getStatus());
		for (HttpField field : jettyResponse.getHeaders()) {
			response = response.header(field.getName(), field.getValue());
		}
		response.entity(jettyResponse.getReason());
		return response.build();
	}
	
	private Object readResponseEntity(Response response, Class<?> returnType) {
		if (returnType.equals(JsonArray.class)) {
			String stringResponse = response.readEntity(String.class);
			JsonReader jsonReader = Json.createReader(new StringReader(stringResponse));
			JsonArray array = jsonReader.readArray();
			jsonReader.close();
			return array;
		} else if (returnType.equals(JsonObject.class)) {
			String stringResponse = response.readEntity(String.class);
			JsonReader jsonReader = Json.createReader(new StringReader(stringResponse));
			JsonObject object = jsonReader.readObject();
			jsonReader.close();
			return object;
		}
		return response.readEntity(returnType);
	}
	
	private void handleExceptionMapping(Response response, List<Class<?>> exceptionTypes) throws Throwable {
		int status = response.getStatus();
		MultivaluedMap<String, Object> headers = response.getHeaders();
		
		for (LocalProviderInfo localProviderInfo : localProviderInstances) {
			
			if (localProviderInfo.getLocalProvider() instanceof ResponseExceptionMapper) {
				
				ResponseExceptionMapper mapper = (ResponseExceptionMapper) localProviderInfo.getLocalProvider();
				
				if (mapper.handles(status, headers)) {
					Throwable throwable = mapper.toThrowable(response);
					if (throwable != null) {
						throwException(throwable, exceptionTypes);
					}
				}
			}
		}
	}
	
	private void throwException(Throwable throwable, List<Class<?>> exceptionTypes) throws Throwable {
		if (throwable instanceof RuntimeException || throwable instanceof Error) {
			throw throwable;
		}
		
		// seznam deklariranih tipov ki jih metoda vrze
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
					result.addHeader(((HeaderParam) annotation).value(), args[paramIndex]);
					jaxRSAnnotationFound = true;
				}
				if (CookieParam.class.equals(annotation.annotationType())) {
					result.addCookieParameter(((CookieParam) annotation).value(), args[paramIndex]);
					jaxRSAnnotationFound = true;
				}
				if (BeanParam.class.equals(annotation.annotationType())) {
					argumentInstance = args[paramIndex];
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
	
	private boolean isSubResource(Class<?> clazz) {
		if (clazz.isInterface()) {
			if (clazz.isAssignableFrom(JsonObject.class)) {
				return false;
			}
			if (clazz.isAssignableFrom(JsonArray.class)) {
				return false;
			}
			return true;
		}
		return false;
	}
	
}
