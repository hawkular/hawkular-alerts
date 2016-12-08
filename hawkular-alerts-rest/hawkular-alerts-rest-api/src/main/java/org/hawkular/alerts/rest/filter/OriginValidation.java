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

import java.util.function.Predicate;

import javax.annotation.PostConstruct;
import javax.inject.Singleton;

import org.hawkular.alerts.engine.impl.AlertProperties;
import org.hawkular.jaxrs.filter.cors.OriginPredicate;

/**
 * @author Jay Shaughnessy
 */
@Singleton
public class OriginValidation {

    // Note, this is prefixed as 'hawkular,' because eventually this may become a hawkular-wide setting.
    private static final String ALLOWED_CORS_ORIGINS_PROP = "hawkular.allowed-cors-origins";
    private static final String ALLOWED_CORS_ORIGINS_ENV = "ALLOWED_CORS_ORIGINS";

    private Predicate<String> predicate;

    @PostConstruct
    protected void init() {
        String allowedCorsOrigins = AlertProperties.getProperty(ALLOWED_CORS_ORIGINS_PROP, ALLOWED_CORS_ORIGINS_ENV, "*");
        predicate = new OriginPredicate(allowedCorsOrigins);
    }

    public Predicate<String> getPredicate() {
        return predicate;
    }
}
