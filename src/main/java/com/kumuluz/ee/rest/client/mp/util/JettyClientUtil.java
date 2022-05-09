package com.kumuluz.ee.rest.client.mp.util;

import org.eclipse.jetty.client.HttpClient;
import org.glassfish.jersey.client.Initializable;
import org.glassfish.jersey.client.spi.Connector;
import org.glassfish.jersey.jetty.connector.Jetty10Connector;
import org.glassfish.jersey.jetty.connector.LocalizationMessages;

import javax.ws.rs.core.Configurable;

public class JettyClientUtil {

    public static HttpClient getHttpClient(Configurable<?> component) {

        if (!(component instanceof Initializable)) {
            throw new IllegalArgumentException(
                    LocalizationMessages.INVALID_CONFIGURABLE_COMPONENT_TYPE(component.getClass().getName()));
        }

        final Initializable<?> initializable = (Initializable<?>) component;
        Connector connector = initializable.getConfiguration().getConnector();
        if (connector == null) {
            initializable.preInitialize();
            connector = initializable.getConfiguration().getConnector();
        }

        if (connector instanceof Jetty10Connector) {
            return ((Jetty10Connector) connector).getHttpClient();
        }

        throw new IllegalArgumentException(LocalizationMessages.EXPECTED_CONNECTOR_PROVIDER_NOT_USED());
    }
}
