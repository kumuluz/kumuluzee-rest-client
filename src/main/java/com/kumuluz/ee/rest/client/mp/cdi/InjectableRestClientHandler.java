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

import javax.enterprise.context.Dependent;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Miha Jamsek
 */

@Dependent
public class InjectableRestClientHandler implements InvocationHandler {
	
	private Map<Class, Object> restClientInvokerCache = new HashMap<>();
	
	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		Object restClientInvoker = restClientInvokerCache.get(method.getDeclaringClass());
		
		if (restClientInvoker == null) {
			RestClientBuilder restClientBuilder = RestClientBuilder.newBuilder();
			
			ProviderRegistrationUtil.registerProviders(restClientBuilder, method);
			
			restClientInvoker = restClientBuilder.build(method.getDeclaringClass());
			restClientInvokerCache.put(method.getDeclaringClass(), restClientInvoker);
		}
		
		try {
			return method.invoke(restClientInvoker, args);
		} catch (InvocationTargetException exc) {
			Throwable cause = exc.getCause();
			if (cause == null) {
				cause = exc;
			}
			throw cause;
		}
	}
}
