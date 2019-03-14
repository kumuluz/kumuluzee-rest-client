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
package com.kumuluz.ee.rest.client.mp.providers;

import org.glassfish.json.jaxrs.JsonValueBodyReader;

import javax.json.JsonValue;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * {@link javax.ws.rs.ext.MessageBodyReader} for {@link JsonValue} that also accepts default MediaType.
 *
 * @author Urban Malc
 * @since 1.2.0
 */
@Produces(MediaType.APPLICATION_JSON)
public class CustomJsonValueBodyReader extends JsonValueBodyReader {

    @Override
    public boolean isReadable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
        return JsonValue.class.isAssignableFrom(aClass) && mediaType == MediaType.APPLICATION_OCTET_STREAM_TYPE ||
                super.isReadable(aClass, type, annotations, mediaType);
    }
}
