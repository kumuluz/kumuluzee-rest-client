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
package com.kumuluz.ee.rest.client.mp.invoker;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Model for query parameter information.
 *
 * @author Miha Jamsek
 * @since 1.0.1
 */

public class ParamInfo {

    private Map<String, Object> pathParameterValues = new HashMap<>();
    private Map<String, Object> queryParameterValues = new HashMap<>();
    private Map<String, Object> cookieParameterValues = new HashMap<>();
    private MultivaluedMap<String, Object> headerValues = new MultivaluedHashMap<>();
    private Object payload = null;

    public void addPathParameter(String name, Object val) {
        pathParameterValues.put(name, val);
    }

    public void addQueryParameter(String name, Object val) {
        queryParameterValues.put(name, val);
    }

    public void addCookieParameter(String name, Object val) {
        cookieParameterValues.put(name, val);
    }

    public void addHeader(String name, Object val) {
        headerValues.add(name, val);
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public Map<String, Object> getPathParameterValues() {
        return pathParameterValues;
    }

    public Map<String, Object> getQueryParameterValues() {
        return queryParameterValues;
    }

    public MultivaluedMap<String, Object> getHeaderValues() {
        return headerValues;
    }

    public Map<String, Object> getCookieParameterValues() {
        return cookieParameterValues;
    }

    public Object getPayload() {
        if (payload == null) {
            return null;
        }
        if (payload instanceof JsonObject || payload instanceof JsonArray) {
            return payload.toString();
        }
        return payload;
    }
}
