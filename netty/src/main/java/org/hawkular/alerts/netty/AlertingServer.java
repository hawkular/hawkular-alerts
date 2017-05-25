package org.hawkular.alerts.netty;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.management.ObjectName;

import org.hawkular.alerts.actions.standalone.StandaloneActionPluginRegister;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.log.MsgLogger;
import org.hawkular.alerts.properties.AlertProperties;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class AlertingServer implements AlertingServerMBean {
    private static final MsgLogger log = MsgLogger.getLogger(AlertingServer.class);

    private static final String BIND_ADDRESS = "hawkular-alerts.bind-address";
    private static final String BIND_ADDRESS_DEFAULT = "127.0.0.1";
    private static final String PORT = "hawkular-alerts.port";
    private static final String PORT_DEFAULT = "8080";
    private static final String JMX_NAME = "org.hawkular.alerting:name=AlertingServer";

    private Vertx vertx;
    private HttpServer server;
    private HandlersManager handlers;
    private AlertingServerThreadFactory threadFactory;
    private ExecutorService executor;

    public void start() {
        String bindAdress = AlertProperties.getProperty(BIND_ADDRESS, BIND_ADDRESS_DEFAULT);
        Integer port = Integer.valueOf(AlertProperties.getProperty(PORT, PORT_DEFAULT));

        try {
            threadFactory = new AlertingServerThreadFactory();
            executor = Executors.newCachedThreadPool(threadFactory);
            StandaloneAlerts.setExecutor(executor);
            StandaloneAlerts.start();
            StandaloneActionPluginRegister.setExecutor(executor);
            StandaloneActionPluginRegister.start();

            vertx = Vertx.vertx();
            handlers = new HandlersManager(vertx);
            handlers.start();
            server = vertx.createHttpServer();
            log.info("Starting Server at http://{}:{}", bindAdress, port);
            server.requestHandler(handlers::handle).listen(port, bindAdress);
        } catch (Exception e) {
            log.error(e);
            log.error("Forcing exit");
            StandaloneActionPluginRegister.stop();
            StandaloneAlerts.stop();
            System.exit(1);
        }

    }

    public String getStatus() {
        return server != null ? "STARTED" : "STOPPED";
    }

    public void stop() {
        log.info("Stopping Server");
        server.close();
        StandaloneActionPluginRegister.stop();
        StandaloneAlerts.stop();
        log.info("Server stopped");
        System.exit(0);
    }

    public static void registerMBean(AlertingServer server) {
        try {
            ObjectName jmxName = new ObjectName(JMX_NAME);
            ManagementFactory.getPlatformMBeanServer().registerMBean(server, jmxName);
        } catch (Exception exception) {
            log.error("Unable to register JMX Bean");
        }
    }

    public static void main(String[] args) {
        AlertingServer server = new AlertingServer();
        registerMBean(server);
        server.start();
    }
}
