package com.kumuluz.ee.rest.client.mp.tests.interfaces;


import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.glassfish.jersey.media.multipart.FormDataParam;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@RegisterRestClient
public interface FormsClient {
    
    @POST
    @Path("/multipart")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Response multipart(@FormDataParam("value") String value);
    
    @POST
    @Path("/multipart")
    @Consumes(MediaType.APPLICATION_JSON)
    Response multipartIncorrectConsume(@FormDataParam("value") String value);
    
    @POST
    @Path("/multipart")
    Response multipartNoConsume(@FormDataParam("value") String value);
    
    @POST
    @Path("/url-encoded")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    Response urlEncoded(@FormParam("value") String value);
    
    @POST
    @Path("/url-encoded")
    @Consumes(MediaType.APPLICATION_JSON)
    Response urlEncodedIncorrectConsume(@FormParam("value") String value);
    
    @POST
    @Path("/url-encoded")
    Response urlEncodedNoConsume(@FormParam("value") String value);
    
    @POST
    @Path("/mixed")
    Response mixed(
        @FormParam("url-value") String urlValue,
        @FormDataParam("multi-value") String multiValue
    );
}
