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
