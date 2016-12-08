/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.alerts.rest.filter;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

import org.hawkular.alerts.engine.impl.AlertProperties;
import org.hawkular.jaxrs.filter.cors.CorsFilters;

/**
 * @author Jay Shaughnessy
 */
@Provider
public class CorsResponseFilter implements ContainerResponseFilter {

    // Note, this is prefixed as 'hawkular,' because eventually this may become a hawkular-wide setting.
    private static final String ALLOWED_CORS_ACCESS_CONTROL_ALLOW_HEADERS_PROP = "hawkular.allowed-cors-access-control-allow-headers";
    private static final String ALLOWED_CORS_ACCESS_CONTROL_ALLOW_HEADERS_ENV = "ALLOWED_CORS_ACCESS_CONTROL_ALLOW_HEADERS";

    private String extraAccesControlAllowHeaders = "";

    @Override public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        if ("".equals(extraAccesControlAllowHeaders)) {
            extraAccesControlAllowHeaders = AlertProperties.getProperty(ALLOWED_CORS_ACCESS_CONTROL_ALLOW_HEADERS_PROP,
                    ALLOWED_CORS_ACCESS_CONTROL_ALLOW_HEADERS_ENV, null);
        }

        CorsFilters.filterResponse(requestContext, responseContext, extraAccesControlAllowHeaders);
    }
}
