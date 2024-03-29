package com.kumuluz.ee.rest.client.mp.tests;

import com.kumuluz.ee.rest.client.mp.tests.interfaces.FormsClient;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.tck.providers.ProducesConsumesFilter;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.testng.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.EmptyAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.testng.annotations.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

import static org.testng.Assert.*;

@Test
public class FormParamsTests extends Arquillian {
    
    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, FormParamsTests.class.getSimpleName() + ".jar")
            .addClasses(FormsClient.class, ProducesConsumesFilter.class)
            .addAsManifestResource(EmptyAsset.INSTANCE, "beans.xml");
    }
    
    /**
     * If there is any FormDataParam, content type will be multipart/form-data, regardless of
     * set Consumes annotation
     */
    @Test
    public void testMultipart() {

        FormsClient client = RestClientBuilder.newBuilder()
            .baseUri(URI.create("http://localhost:8080/null"))
            .register(ProducesConsumesFilter.class)
            .build(FormsClient.class);

        try (Response r = client.multipart("testvalue")) {
            String contentTypeHeader = r.getHeaderString("Sent-ContentType");
            assertTrue(contentTypeHeader.contains(MediaType.MULTIPART_FORM_DATA));
        }

        try (Response r = client.multipartIncorrectConsume("testvalue")) {
            String contentTypeHeader = r.getHeaderString("Sent-ContentType");
            assertTrue(contentTypeHeader.contains(MediaType.MULTIPART_FORM_DATA));
        }

        try (Response r = client.multipartNoConsume("testvalue")) {
            String contentTypeHeader = r.getHeaderString("Sent-ContentType");
            assertTrue(contentTypeHeader.contains(MediaType.MULTIPART_FORM_DATA));
        }
    }
    
    /**
     * If there is any FormData, content type will be application/x-www-form-urlencoded, regardless of
     * set Consumes annotation
     */
    @Test
    public void testUrlEncoded() {

        FormsClient client = RestClientBuilder.newBuilder()
            .baseUri(URI.create("http://localhost:8080/null"))
            .register(ProducesConsumesFilter.class)
            .build(FormsClient.class);

        try (Response r = client.urlEncoded("testvalue");) {
            String contentTypeHeader = r.getHeaderString("Sent-ContentType");
            assertEquals(contentTypeHeader, MediaType.APPLICATION_FORM_URLENCODED);
        }

        try (Response r = client.urlEncodedIncorrectConsume("testvalue")) {
            String contentTypeHeader = r.getHeaderString("Sent-ContentType");
            assertEquals(contentTypeHeader, MediaType.APPLICATION_FORM_URLENCODED);
        }

        try (Response r = client.urlEncodedNoConsume("testvalue")) {
            String contentTypeHeader = r.getHeaderString("Sent-ContentType");
            assertEquals(contentTypeHeader, MediaType.APPLICATION_FORM_URLENCODED);
        }
    }
    
    /**
     * If there are present both FormDataParam and FormParam annotations,
     * FormDataParam will take precedence
     */
    @Test
    public void testMixedThrows() {
        FormsClient client = RestClientBuilder.newBuilder()
            .baseUri(URI.create("http://localhost:8080/null"))
            .register(ProducesConsumesFilter.class)
            .build(FormsClient.class);
        
        assertThrows(IllegalStateException.class, () -> {
            //noinspection EmptyTryBlock
            try (Response ignored = client.mixed("testvalue", "testvalue2")) {
            }
        });
    }
}
