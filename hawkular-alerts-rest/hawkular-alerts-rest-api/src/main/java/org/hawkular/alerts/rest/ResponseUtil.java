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
package org.hawkular.alerts.rest;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.hawkular.alerts.api.exception.FoundException;
import org.hawkular.alerts.api.exception.NotFoundException;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.PageContext;
import org.hawkular.alerts.rest.json.Link;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * Helper class used to build REST responses and deal with errors.
 *
 * @author Lucas Ponce
 */
public class ResponseUtil {

    public static Response internalError(Exception e) {
        if (e.getMessage() == null) {
            return internalError(e.toString());
        } else {
            return internalError(e.getMessage());
        }
    }

    public static Response internalError(String message) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(new ApiError(message)).type(APPLICATION_JSON_TYPE).build();
    }

    public static Response notFound(String message) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiError(message)).type(APPLICATION_JSON_TYPE).build();
    }

    public static Response ok(Object entity) {
        return Response.status(Response.Status.OK).entity(entity).type(APPLICATION_JSON_TYPE).build();
    }

    public static <T> Response paginatedOk(Page<T> page, UriInfo uri) {

        //extract the data out of the page
        List<T> data = new ArrayList<>(page);

        Response.ResponseBuilder response = Response.status(Response.Status.OK).entity(data);

        createPagingHeader(response, uri, page);

        return response.build();
    }

    public static Response ok() {
        return Response.status(Response.Status.OK).type(APPLICATION_JSON_TYPE).build();
    }

    public static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ApiError(message)).type(APPLICATION_JSON_TYPE).build();
    }

    public static Response onException(Exception e, Logger log) {
        if (e instanceof NotFoundException) {
            return notFound(e.getMessage());
        }
        if (null != e.getCause() && e.getCause() instanceof NotFoundException) {
            return notFound(e.getCause().getMessage());
        }
        if (e instanceof FoundException) {
            return badRequest(e.getMessage());
        }
        if (null != e.getCause() && e.getCause() instanceof FoundException) {
            return badRequest(e.getCause().getMessage());
        }
        if (e instanceof IllegalArgumentException) {
            return badRequest(e.getMessage());
        }
        if (null != e.getCause() && e.getCause() instanceof IllegalArgumentException) {
            return badRequest(e.getCause().getMessage());
        }
        if (null != log) {
            log.debug(e.getMessage(), e);
        }
        return internalError(e);
    }

    public static void populateQueryParamsMap(Class<?> clazz, Map<String, Set<String>> queryParamValidationMap) {
        Arrays.asList(clazz.getDeclaredMethods()).stream()
                .filter(m -> m.isAnnotationPresent(QueryParamValidation.class))
                .forEach(m -> {
                    String name = m.getAnnotation(QueryParamValidation.class).name();
                    Set<String> queryParams = Arrays.asList(m.getParameters()).stream()
                            .filter(p -> p.isAnnotationPresent(QueryParam.class))
                            .map(p -> p.getAnnotation(QueryParam.class).value())
                            .collect(Collectors.toSet());
                    queryParamValidationMap.put(name, queryParams);
                });
    }

    public static void checkForUnknownQueryParams(final UriInfo uri, final Set<String> expected) {
        Set<String> received = uri.getQueryParameters().keySet();
        if (received.contains("ignoreUnknownQueryParams")) {
            return;
        }

        Set<String> unknown = received.stream()
                .filter(p -> !expected.contains(p) && !RequestUtil.PARAMS_PAGING.contains(p))
                .collect(Collectors.toSet());
        if (!unknown.isEmpty()) {
            String message = "Unknown Query Parameter(s): " + unknown.toString();
            throw new IllegalArgumentException(message);
        }

        return;
    }

    /**
     * Create the paging headers for collections and attach them to the passed builder. Those are represented as
     * <i>Link:</i> http headers that carry the URL for the pages and the respective relation.
     * <p>In addition a <i>X-Total-Count</i> header is created that contains the whole collection size.</p>
     *
     * @param builder    The ResponseBuilder that receives the headers
     * @param uriInfo    The uriInfo of the incoming request to build the urls
     * @param resultList The collection with its paging information
     */
    public static void createPagingHeader(final Response.ResponseBuilder builder, final UriInfo uriInfo,
            final Page<?> resultList) {

        UriBuilder uriBuilder;

        PageContext pc = resultList.getPageContext();
        int page = pc.getPageNumber();

        List<Link> links = new ArrayList<>();

        if (pc.isLimited() && resultList.getTotalSize() > (pc.getPageNumber() + 1) * pc.getPageSize()) {
            int nextPage = page + 1;
            uriBuilder = uriInfo.getRequestUriBuilder(); // adds ?q, ?per_page, ?page, etc. if needed
            uriBuilder.replaceQueryParam("page", nextPage);

            links.add(new Link("next", uriBuilder.build().toString()));
        }

        if (page > 0) {
            int prevPage = page - 1;
            uriBuilder = uriInfo.getRequestUriBuilder(); // adds ?q, ?per_page, ?page, etc. if needed
            uriBuilder.replaceQueryParam("page", prevPage);
            links.add(new Link("prev", uriBuilder.build().toString()));
        }

        // A link to the last page
        if (pc.isLimited()) {
            long lastPage = resultList.getTotalSize() / pc.getPageSize();
            if (resultList.getTotalSize() % pc.getPageSize() == 0) {
                lastPage -= 1;
            }

            uriBuilder = uriInfo.getRequestUriBuilder(); // adds ?q, ?per_page, ?page, etc. if needed
            uriBuilder.replaceQueryParam("page", lastPage);
            links.add(new Link("last", uriBuilder.build().toString()));
        }

        // A link to the current page
        uriBuilder = uriInfo.getRequestUriBuilder(); // adds ?q, ?per_page, ?page, etc. if needed

        StringBuilder linkHeader = new StringBuilder(new Link("current", uriBuilder.build().toString())
                .rfc5988String());

        //followed by the rest of the link defined above
        links.forEach((l) -> linkHeader.append(", ").append(l.rfc5988String()));

        //add that all as a single Link header to the response
        builder.header("Link", linkHeader.toString());

        // Create a total size header
        builder.header("X-Total-Count", resultList.getTotalSize());
    }

    @ApiModel(description = "Payload for a simple REST deleted number response.")
    public static class ApiDeleted {

        @ApiModelProperty(value = "Deleted items.")
        @JsonInclude
        private final Integer deleted;

        public ApiDeleted(Integer deleted) {
            this.deleted = deleted;
        }

        public Integer getDeleted() {
            return deleted;
        }
    }

    @ApiModel(description = "Payload for a REST error response.")
    public static class ApiError {

        @ApiModelProperty(value = "Description of the error message.")
        @JsonInclude
        private final String errorMsg;

        public ApiError(String errorMsg) {
            this.errorMsg = errorMsg != null && !errorMsg.trim().isEmpty() ? errorMsg : "No details";
        }

        public String getErrorMsg() {
            return errorMsg;
        }
    }
}
