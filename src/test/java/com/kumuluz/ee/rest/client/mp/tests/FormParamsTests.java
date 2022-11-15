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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test
public class FormParamsTests extends Arquillian {
    
    @Deployment
    public static JavaArchive createDeployment() {
        return ShrinkWrap.create(JavaArchive.class, FormParamsTests.class.getSimpleName() + ".war")
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
        
        Response r = client.multipart("testvalue");
        String contentTypeHeader = r.getHeaderString("Sent-ContentType");
        
        assertTrue(contentTypeHeader.contains(MediaType.MULTIPART_FORM_DATA));
        r = client.multipartIncorrectConsume("testvalue");
        contentTypeHeader = r.getHeaderString("Sent-ContentType");
        assertTrue(contentTypeHeader.contains(MediaType.MULTIPART_FORM_DATA));
    
        r = client.multipartNoConsume("testvalue");
        contentTypeHeader = r.getHeaderString("Sent-ContentType");
        assertTrue(contentTypeHeader.contains(MediaType.MULTIPART_FORM_DATA));
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
        
        Response r = client.urlEncoded("testvalue");
        String contentTypeHeader = r.getHeaderString("Sent-ContentType");
        assertEquals(contentTypeHeader, MediaType.APPLICATION_FORM_URLENCODED);
        
        r = client.urlEncodedIncorrectConsume("testvalue");
        contentTypeHeader = r.getHeaderString("Sent-ContentType");
        assertEquals(contentTypeHeader, MediaType.APPLICATION_FORM_URLENCODED);
        
        r = client.urlEncodedNoConsume("testvalue");
        contentTypeHeader = r.getHeaderString("Sent-ContentType");
        assertEquals(contentTypeHeader, MediaType.APPLICATION_FORM_URLENCODED);
    }
    
    /**
     * If there are present both FormDataParam and FormParam annotations,
     * FormDataParam will take precedence
     */
    @Test
    public void testMixedPriority() {
        FormsClient client = RestClientBuilder.newBuilder()
            .baseUri(URI.create("http://localhost:8080/null"))
            .register(ProducesConsumesFilter.class)
            .build(FormsClient.class);
        
        Response r = client.mixed("testvalue", "testvalue2");
        String contentTypeHeader = r.getHeaderString("Sent-ContentType");
        assertTrue(contentTypeHeader.contains(MediaType.MULTIPART_FORM_DATA));
    }
}
