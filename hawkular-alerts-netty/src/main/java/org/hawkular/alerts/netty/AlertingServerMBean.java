package org.hawkular.alerts.netty;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface AlertingServerMBean {
    String getStatus();
    void stop();
}
