/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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

import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.core.Response;

/**
 * Helper class used to build REST responses and deal with errors.
 *
 * @author Lucas Ponce
 */
public class ResponseUtil {

    public static Response internalError(String message) {
        Map<String, String> errors = new HashMap<>();
        errors.put("errorMsg", "Internal Error: " + message);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(errors).type(APPLICATION_JSON_TYPE).build();
    }

    public static Response noContent() {
        return Response.status(Response.Status.NO_CONTENT).type(APPLICATION_JSON_TYPE).build();
    }

    public static Response notFound(Object entity) {
        return Response.status(Response.Status.NOT_FOUND).entity(entity).type(APPLICATION_JSON_TYPE).build();
    }

    public static Response ok(Object entity) {
        return Response.status(Response.Status.OK).entity(entity).type(APPLICATION_JSON_TYPE).build();
    }

    public static Response ok() {
        return Response.status(Response.Status.OK).type(APPLICATION_JSON_TYPE).build();
    }

    public static Response badRequest(String message) {
        Map<String, String> errors = new HashMap<>();
        errors.put("errorMsg", "Internal Error: " + message);
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(errors).type(APPLICATION_JSON_TYPE).build();
    }
}
