/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.alerts.api.model.paging;

import java.util.ArrayList;
import java.util.List;

/**
 * Specifies the requirements on the paging of some results.
 *
 * @author Lukas Krejci
 * @since 0.0.1
 */
public final class Pager extends PageContext {
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The result should not be paged.
     *
     * @param order the ordering of the results
     * @return a new pager instance
     */
    public static Pager unlimited(Order... order) {
        return new Pager(0, UNLIMITED_PAGE_SIZE, order);
    }

    /**
     * Same as {@link #unlimited(Order...)} but the ordering represented by a collection.
     *
     * @param order the ordering of the results
     * @return a new pager instance
     */
    public static Pager unlimited(Iterable<Order> order) {
        return new Pager(0, UNLIMITED_PAGE_SIZE, order);
    }

    /**
     * @param pageNumber the number of the page to fetch
     * @param pageSize   the number of the elements on the page
     * @param orders     the ordering of the results required
     */
    public Pager(int pageNumber, int pageSize, Order... orders) {
        super(pageNumber, pageSize, orders);
    }

    /**
     * @param pageNumber see {@link #Pager(int, int, Order...)}
     * @param pageSize see {@link #Pager(int, int, Order...)}
     * @param orders see {@link #Pager(int, int, Order...)}
     * @see #Pager(int, int, Order...)
     */
    public Pager(int pageNumber, int pageSize, Iterable<Order> orders) {
        super(pageNumber, pageSize, orders);
    }

    /**
     * If this is a limited pager ({@link #isLimited()}), returns the pager pointing to the next page of the results.
     *
     * If this is an unlimited pager, then simply returns this very pager because there can be no other page of the
     * results.
     *
     * @return a new pager instance
     */
    public Pager nextPage() {
        if (getPageSize() >= 0) {
            return new Pager(getPageNumber() + 1, getPageSize(), getOrder());
        } else {
            return this;
        }
    }

    /**
     * If this is a limited pager ({@link #isLimited()}), returns the pager pointing to the previous page of the
     * results.
     *
     * If this is an unlimited pager, then simply returns this very pager because there can be no other page of the
     * results.
     *
     * @return a new pager instance
     */
    public Pager previousPage() {
        if (getPageNumber() > 0 && getPageSize() >= 0) {
            return new Pager(getPageNumber() - 1, getPageSize(), getOrder());
        } else {
            return this;
        }
    }

    public static final class Builder {
        private int pageNumber;
        private int pageSize;
        private List<Order> order = new ArrayList<>();

        private Builder() {
        }

        public Builder withPageSize(int size) {
            pageSize = size;
            return this;
        }

        public Builder withStartPage(int pageNumber) {
            this.pageNumber = pageNumber;
            return this;
        }

        public Builder orderBy(String field, Order.Direction direction) {
            order.add(Order.by(field, direction));
            return this;
        }

        public Builder orderByAscending(String field) {
            return orderBy(field, Order.Direction.ASCENDING);
        }

        public Builder orderByDescending(String field) {
            return orderBy(field, Order.Direction.DESCENDING);
        }

        public Builder orderBy(Order order) {
            this.order.add(order);
            return this;
        }

        public Pager build() {
            return new Pager(pageNumber, pageSize, order);
        }
    }
}
