package com.kumuluz.ee.rest.client.mp.integrations;

import com.kumuluz.ee.rest.enums.FilterOperation;
import com.kumuluz.ee.rest.enums.OrderDirection;

import javax.ws.rs.QueryParam;
import java.util.LinkedList;
import java.util.List;

/**
 * Used as bean parameter in rest client requests
 */
public class KeeRestParameters {
    @QueryParam("where")
    private String filter;
    @QueryParam("order")
    private String order;
    @QueryParam("fields")
    private String fields;
    @QueryParam("offset")
    private int offset;
    @QueryParam("limit")
    private int limit;

    //Jackson doesn't like serializing empty beans, throws exception, this getter and setter prevent that
    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    private KeeRestParameters(KeeRestParametersBuilder builder) {
        this.filter = String.join(" ", builder.filter);
        this.order = String.join(", ", builder.order);
        this.fields = String.join(", ", builder.fields);
        this.offset = builder.offset;
        this.limit = builder.limit;
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
            if (filterOperation.equals(FilterOperation.LIKE) || filterOperation.equals(FilterOperation.LIKEIC)){
                value = value.replace("%","%25");
                value = value.replace("+","%2B");
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
