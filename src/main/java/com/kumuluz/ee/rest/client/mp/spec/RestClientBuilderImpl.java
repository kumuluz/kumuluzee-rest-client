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
import com.kumuluz.ee.rest.client.mp.invoker.LocalProviderInfo;
import com.kumuluz.ee.rest.client.mp.invoker.RestClientInvoker;
import com.kumuluz.ee.rest.client.mp.proxy.RestClientProxyFactory;
import com.kumuluz.ee.rest.client.mp.util.InterfaceValidatorUtil;
import com.kumuluz.ee.rest.client.mp.util.MapperDisabledUtil;
import com.kumuluz.ee.rest.client.mp.util.ProviderRegistrationUtil;
import org.apache.deltaspike.core.api.provider.BeanProvider;
import org.apache.deltaspike.proxy.spi.DeltaSpikeProxy;
import org.apache.deltaspike.proxy.spi.invocation.DeltaSpikeProxyInvocationHandler;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.ext.ParamConverterProvider;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

/**
 * @author Miha Jamsek
 */
public class RestClientBuilderImpl implements RestClientBuilder {
	
	private Logger LOG = Logger.getLogger(RestClientBuilderImpl.class.getSimpleName());
	
	private ClientBuilder clientBuilder;
	private DeltaSpikeProxyInvocationHandler deltaSpikeProxyInvocationHandler;
	private BeanManager beanManager;
	private URI baseURI;
	private Set<LocalProviderInfo> localProviderInstances = new HashSet<>();
	
	RestClientBuilderImpl() {
		clientBuilder = ClientBuilder.newBuilder();
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
	
	public <T> T build(Class<T> apiClass) throws IllegalStateException, RestClientDefinitionException {
		InterfaceValidatorUtil.validateApiInterface(apiClass);
		
		RestClientProxyFactory proxyFactory = RestClientProxyFactory.getInstance();

		if (!isRunningInContainer()) {
			// fixes exception in InvokeWithJsonPProviderTest, which happens when @BeforeTest gets executed on client
			// see: https://developer.jboss.org/thread/198706
			// CDI BeanManager is not accessible outside container and getBeanManager throws exception
			// this shouldn't be a problem because Rest Client cannot be used without KumuluzEE
			// if running TCK, this log message should be ignored
			LOG.severe("Rest Client is running outside container, build method cannot be used.");
			return null;
		}

		beanManager = CDI.current().getBeanManager();

		Class<T> proxyClass = proxyFactory.getProxyClass(beanManager, apiClass);
		Method[] delegateMethods = proxyFactory.getDelegateMethods(apiClass);
		
		return this.create(apiClass, proxyClass, delegateMethods);
	}
	
	private <T> T create(Class<T> apiClass, Class<T> proxyClass, Method[] delegateMethods) {
		try {
			
			lazyinit();
			
			T instance = proxyClass.newInstance();
			
			DeltaSpikeProxy deltaSpikeProxy = (DeltaSpikeProxy) instance;
			deltaSpikeProxy.setInvocationHandler(deltaSpikeProxyInvocationHandler);
			
			deltaSpikeProxy.setDelegateMethods(delegateMethods);
			
			if (baseURI == null) {
				String urlFormat = String.format("%s/mp-rest/url", apiClass.getName());
				URL urlConfig = ConfigProvider.getConfig()
					.getOptionalValue(urlFormat, URL.class)
					.orElseThrow(() -> new RestClientDefinitionException("Configuration key '" + urlFormat + "' is not set!"));
				this.baseUrl(urlConfig);
			}
			
			ProviderRegistrationUtil.registerProviders(clientBuilder, apiClass);
			this.addParamConverters();
			
			RestClientInvoker rcInvoker = new RestClientInvoker(
				clientBuilder.build(),
				baseURI.toString(),
				defineLocalProviderInstances());
			deltaSpikeProxy.setDelegateInvocationHandler(rcInvoker);
			
			return instance;
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private void addParamConverters() {
		List<ParamConverterProvider> paramConverterProviders = ProviderRegistrationUtil.getParamConverterProviders();
		for(ParamConverterProvider provider : paramConverterProviders) {
			this.localProviderInstances.add(new LocalProviderInfo(provider, Priorities.USER));
		}
	}
	
	private List<LocalProviderInfo> defineLocalProviderInstances() {
		if (!MapperDisabledUtil.isMapperDisabled(this.clientBuilder)) {
			register(DefaultExceptionMapper.class);
		}
		
		List<LocalProviderInfo> result = new ArrayList<>(this.localProviderInstances);
		result.sort((o1, o2) -> {
			Integer p1 = o1.getPriority();
			Integer p2 = o2.getPriority();
			return p1.compareTo(p2);
		});
		return result;
	}
	
	private void lazyinit() {
		if (deltaSpikeProxyInvocationHandler == null) {
			init();
		}
	}
	
	private synchronized void init() {
		if (deltaSpikeProxyInvocationHandler == null) {
			deltaSpikeProxyInvocationHandler = BeanProvider.getContextualReference(beanManager, DeltaSpikeProxyInvocationHandler.class, false);
		}
	}
	
	@Override
	public Configuration getConfiguration() {
		return clientBuilder.getConfiguration();
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
		} else if (o instanceof ParamConverterProvider) {
			register(o, Priorities.USER);
		} else {
			this.clientBuilder.register(o);
		}
		return this;
	}
	
	@Override
	public RestClientBuilder register(Object o, int i) {
		
		if (o instanceof ResponseExceptionMapper || o instanceof  ParamConverterProvider) {
			registerLocalProviderInstance(o, i);
		}
		if (o instanceof ParamConverterProvider) {
			ParamConverterProvider provider = (ParamConverterProvider)o;
			ProviderRegistrationUtil.addToParamConverterList(provider);
		}
		this.clientBuilder.register(o, i);
		return this;
	}
	
	@Override
	public RestClientBuilder register(Object o, Class<?>... classes) {
		// add ResponseExceptionMapper - otherwise is not added
		for (Class<?> clazz : classes) {
			if (clazz.isAssignableFrom(ResponseExceptionMapper.class)) {
				register(o);
			}
		}
		this.clientBuilder.register(o, classes);
		return this;
	}
	
	@Override
	public RestClientBuilder register(Object o, Map<Class<?>, Integer> map) {
		if (o instanceof ResponseExceptionMapper) {
			ResponseExceptionMapper mapper = (ResponseExceptionMapper) o;
			registerLocalProviderInstance(mapper, mapper.getPriority());
		}
		if (o instanceof ParamConverterProvider) {
			ParamConverterProvider provider = (ParamConverterProvider)o;
			ProviderRegistrationUtil.addToParamConverterList(provider);
			registerLocalProviderInstance(o, Priorities.USER);
		}
		this.clientBuilder.register(o, map);
		return this;
	}
	
	private void registerLocalProviderInstance(Object provider, int priority) {
		if (localProviderInstances.stream().map(LocalProviderInfo::getLocalProvider).anyMatch(provider::equals)) {
			LOG.warning("Provider already registered! " + provider.getClass().getName());
			return;
		}

		localProviderInstances.add(new LocalProviderInfo(provider, priority));
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
}
