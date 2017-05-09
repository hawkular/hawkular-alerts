package org.hawkular.alerts.netty;

import java.util.concurrent.ThreadFactory;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AlertingServerThreadFactory implements ThreadFactory {
    private int count = 0;

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r, "HawkularAlerts-" + (++count));
    }
}
