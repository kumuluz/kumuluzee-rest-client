package com.kumuluz.ee.rest.client.mp.providers;

import javax.annotation.Priority;
import javax.enterprise.context.RequestScoped;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
@Priority(Priorities.USER + 5000)
@RequestScoped
public class IncomingHeadersInterceptor implements ContainerRequestFilter {

    private MultivaluedMap<String, String> incomingHeaders = new MultivaluedHashMap<>();

    public MultivaluedMap<String, String> getIncomingHeaders() {
        return incomingHeaders;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        requestContext.getHeaders().forEach((k, v) -> this.incomingHeaders.addAll(k, v));
    }
}
