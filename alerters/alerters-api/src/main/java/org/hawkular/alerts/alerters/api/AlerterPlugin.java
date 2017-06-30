package org.hawkular.alerts.alerters.api;

import java.util.concurrent.ExecutorService;

import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.DefinitionsService;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface AlerterPlugin {

    void init(DefinitionsService definitions, AlertsService alerts, ExecutorService executor);
    void stop();
}
