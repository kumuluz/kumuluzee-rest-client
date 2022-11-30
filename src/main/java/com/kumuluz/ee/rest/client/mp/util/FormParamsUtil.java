package com.kumuluz.ee.rest.client.mp.util;

import org.glassfish.jersey.media.multipart.BodyPart;
import org.glassfish.jersey.media.multipart.Boundary;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import java.util.Map;

public class FormParamsUtil {
    
    private FormParamsUtil() {
    
    }
    
    public static Entity<FormDataMultiPart> processMultipartFormParams(Map<String, Object> formParams) {
        FormDataMultiPart multiPart = new FormDataMultiPart();
        formParams.forEach((name, val) -> {
            if (val instanceof String) {
                multiPart.field(name, (String) val);
            } else if (val instanceof BodyPart) {
                multiPart.bodyPart((BodyPart) val);
            }
        });
        MediaType multipartMediaType = Boundary.addBoundary(MediaType.MULTIPART_FORM_DATA_TYPE);
        return Entity.entity(multiPart, multipartMediaType);
    }
    
    public static Entity<Form> processUrlEncodedFormParams(Map<String, Object> formParams) {
        Form form = new Form();
        formParams.forEach((name, val) -> {
            form.param(name, val.toString());
        });
        return Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE);
    }
    
}
