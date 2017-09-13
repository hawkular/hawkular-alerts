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
package org.hawkular.alerter.prometheus.rest;

import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.commons.properties.HawkularProperties;
import org.hawkular.handlers.BaseApplication;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class PrometheusApp implements BaseApplication {
    private static final MsgLogger log = MsgLogging.getMsgLogger(PrometheusApp.class);
    private static final String BASE_URL = "hawkular-alerts.base-url-alerter";
    private static final String BASE_URL_DEFAULT = "/hawkular/alerter";

    String baseUrl = HawkularProperties.getProperty(BASE_URL, BASE_URL_DEFAULT) + "/prometheus";

    @Override
    public void start() {
        log.infof("Prometheus app started on [ %s ] ", baseUrl());
    }

    @Override
    public void stop() {
        log.infof("Prometheus app stopped", baseUrl());
    }

    @Override
    public String baseUrl() {
        return baseUrl;
    }
}
