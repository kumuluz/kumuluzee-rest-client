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
package com.kumuluz.ee.rest.client.mp.integrations;

import com.kumuluz.ee.rest.enums.FilterOperation;
import com.kumuluz.ee.rest.enums.OrderDirection;

import javax.ws.rs.QueryParam;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Used as bean parameter in rest client requests
 *
 * @author Klemen Kobau
 * @since 1.3.0
 */
public class KeeRestParameters {

    private Logger logger = Logger.getLogger(KeeRestParameters.class.getName());

    @QueryParam("filter")
    private String filter;
    @QueryParam("order")
    private String order;
    @QueryParam("fields")
    private String fields;
    @QueryParam("offset")
    private int offset;
    @QueryParam("limit")
    private int limit;

    private KeeRestParameters(KeeRestParametersBuilder builder) {
        this.filter = String.join(" ", builder.filter);
        this.order = String.join(", ", builder.order);
        this.fields = String.join(", ", builder.fields);
        this.offset = builder.offset;
        this.limit = builder.limit;

        try{
            this.filter = URLEncoder.encode(this.filter,StandardCharsets.UTF_8.toString());
            this.order = URLEncoder.encode(this.order,StandardCharsets.UTF_8.toString());
            this.fields = URLEncoder.encode(this.fields,StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e){
            logger.severe(e.getMessage());
        }
    }

    public static class KeeRestParametersBuilder {
        private List<String> filter;
        private List<String> order;
        private List<String> fields;
        private int offset = 0;
        private int limit = 100;

        public KeeRestParametersBuilder() {
            filter = new LinkedList<>();
            order = new LinkedList<>();
            fields = new LinkedList<>();
        }

        // filters
        public KeeRestParametersBuilder addFilter(String field, FilterOperation filterOperation) {
            if (!filterOperation.equals(FilterOperation.ISNULL) && !filterOperation.equals(FilterOperation.ISNOTNULL)) {
                throw new IllegalArgumentException(filterOperation.toString() + " requires a value parameter");
            }

            filter.add(field + ":" + filterOperation.toString());
            return this;
        }

        public KeeRestParametersBuilder addFilter(String field, FilterOperation filterOperation, String value) {
            if (filterOperation.equals(FilterOperation.ISNULL) || filterOperation.equals(FilterOperation.ISNOTNULL)) {
                throw new IllegalArgumentException(filterOperation.toString() + " doesn't take the value parameter");
            }
            filter.add(field + ":" + filterOperation.toString() + ":" + value);
            return this;
        }

        // fields
        public KeeRestParametersBuilder addField(String field) {
            fields.add(field);
            return this;
        }

        public KeeRestParametersBuilder addFields(List<String> fields) {
            this.fields.addAll(fields);
            return this;
        }

        //ordering
        public KeeRestParametersBuilder addOrdering(String field, OrderDirection direction) {
            order.add(field + " " + direction.toString());
            return this;
        }

        // pagination
        public KeeRestParametersBuilder setOffset(int offset) {
            this.offset = offset;
            return this;
        }

        public KeeRestParametersBuilder setLimit(int limit) {
            this.limit = limit;
            return this;
        }

        public KeeRestParameters build() {
            return new KeeRestParameters(this);
        }
    }
}
