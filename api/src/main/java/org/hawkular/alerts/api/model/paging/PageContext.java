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
import java.util.Collections;
import java.util.List;

/**
 * @author Lukas Krejci
 * @since 0.0.1
 */
public class PageContext {
    public static final int UNLIMITED_PAGE_SIZE = -1;

    private final int pageSize;
    private final int pageNumber;
    private final List<Order> order;

    public PageContext(int pageNumber, int pageSize, Order... orders) {
        if (orders.length == 0) {
            throw new IllegalArgumentException("At least one order specification must be supplied.");
        }
        this.pageNumber = pageSize >= 0 ? pageNumber : 0;
        this.pageSize = pageSize;
        List<Order> tmp = new ArrayList<>();
        Collections.addAll(tmp, orders);
        this.order = Collections.unmodifiableList(tmp);
    }

    public PageContext(int pageNumber, int pageSize, Iterable<Order> orders) {
        this.pageNumber = pageSize >= 0 ? pageNumber : 0;
        this.pageSize = pageSize;
        List<Order> tmp = new ArrayList<>();
        orders.forEach(tmp::add);
        if (tmp.size() == 0) {
            throw new IllegalArgumentException("At least one order specification must be supplied.");
        }

        this.order = Collections.unmodifiableList(tmp);
    }

    /**
     * @return the number of the page this context represents (0-based)
     */
    public int getPageNumber() {
        return pageNumber;
    }

    /**
     * The associated {@link Page} objects should contain at most this number of elements.
     *
     * @return the page size that is being used
     */
    public int getPageSize() {
        return pageSize;
    }

    /**
     * This trivially translates to {@code pageNumber * pageSize}.
     *
     * @return the index of the first element in the overall results that belongs to the page designated by this context
     */
    public int getStart() {
        return pageNumber * pageSize;
    }

    /**
     * If this page context is {@link #isLimited() limited}, then this returns {@code getStart() + pageSize}. If this
     * page context IS unlimited, {@link Integer#MAX_VALUE} is returned.
     *
     * @return the index just after the last element in the overall results the page designated by this context
     * represents
     */
    public int getEnd() {
        if (isLimited()) {
            return getStart() + pageSize;
        } else {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * Page size less then zero represents no limit on the size of the page. Effectively, page contexts like that will
     * always represent the first (and only) page of the results.
     *
     * @return true if the page size is less than zero
     */
    public boolean isLimited() {
        return pageSize >= 0;
    }

    /**
     * This list of ordering fields.
     *
     * @return the unmodifiable list of ordering fields.
     */
    public List<Order> getOrder() {
        return order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PageContext)) return false;

        PageContext that = (PageContext) o;

        return pageSize == that.pageSize && pageNumber == that.pageNumber && this.order.equals(that.order);

    }

    @Override
    public int hashCode() {
        int result = pageSize;
        result = 31 * result + pageNumber;
        return result;
    }

    @Override public String toString() {
        return "PagingState[" + "order=" + order + ", pageNumber=" + pageNumber + ", pageSize=" +
                pageSize + ']';
    }

}
