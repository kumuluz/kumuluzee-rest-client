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

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.annotation.RegisterProviders;

import javax.annotation.Priority;
import javax.ws.rs.client.ClientBuilder;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author Miha Jamsek
 */
public class ProviderRegistrationUtil {

	private static final Logger LOG = Logger.getLogger(ProviderRegistrationUtil.class.getSimpleName());
	
	public static void registerProviders(ClientBuilder clientBuilder, Class interfaceType) {
		// annotation providers
		RegisterProvider registerProvider = (RegisterProvider) interfaceType.getAnnotation(RegisterProvider.class);
		if (registerProvider != null) {
			registerSingleProvider(clientBuilder, registerProvider);
		}
		RegisterProviders registerProviders = (RegisterProviders) interfaceType.getAnnotation(RegisterProviders.class);
		if (registerProviders != null) {
			for(RegisterProvider provider : registerProviders.value()) {
				registerSingleProvider(clientBuilder, provider);
			}
		}

		// mp config providers
		Optional<String> definedProviders = RegistrationConfigUtil.getConfigurationParameter(interfaceType,
				"providers", String.class);
		if (definedProviders.isPresent()) {
			String[] providerNames = definedProviders.get().split(",");

			for (String providerName : providerNames) {
				Optional<Integer> providerPriority = RegistrationConfigUtil.getConfigurationParameter(interfaceType,
						"providers/" + providerName + "/priority", Integer.class);
				try {
					if (providerPriority.isPresent()) {
						clientBuilder.register(Class.forName(providerName), providerPriority.get());
					} else {
						clientBuilder.register(Class.forName(providerName));
					}
				} catch (ClassNotFoundException e) {
					LOG.warning("Could not register provider " + providerName + ". Class not found.");
				}
			}
		}
	}
	
	public static void registerSingleProvider(ClientBuilder clientBuilder, RegisterProvider provider) {
		Class<?> providerClass = provider.value();
		int priority = provider.priority();
		if (priority == -1) {
			Priority priorityAnnotation = providerClass.getAnnotation(Priority.class);
			if (priorityAnnotation != null) {
				priority = priorityAnnotation.value();
			}
		}
		
		if (priority == -1) {
			clientBuilder.register(providerClass);
		} else {
			clientBuilder.register(providerClass, priority);
		}
	}
	
	public static void registerProviders(RestClientBuilder restClientBuilder, Class interfaceType) {
		RegisterProvider registerProvider = (RegisterProvider) interfaceType.getAnnotation(RegisterProvider.class);
		if (registerProvider != null) {
			registerSingleProvider(restClientBuilder, registerProvider);
		}
		RegisterProviders registerProviders = (RegisterProviders) interfaceType.getAnnotation(RegisterProviders.class);
		if (registerProviders != null) {
			for(RegisterProvider provider : registerProviders.value()) {
				registerSingleProvider(restClientBuilder, provider);
			}
		}
	}
	
	public static void registerProviders(RestClientBuilder restClientBuilder, Method method) {
		registerProviders(restClientBuilder, method.getDeclaringClass());
	}
	
	public static void registerSingleProvider(RestClientBuilder restClientBuilder, RegisterProvider provider) {
		Class<?> providerClass = provider.value();
		int priority = provider.priority();
		if (priority == -1) {
			Priority priorityAnnotation = providerClass.getAnnotation(Priority.class);
			if (priorityAnnotation != null) {
				priority = priorityAnnotation.value();
			}
		}
		
		if (priority == -1) {
			restClientBuilder.register(providerClass);
		} else {
			restClientBuilder.register(providerClass, priority);
		}
	}
}
