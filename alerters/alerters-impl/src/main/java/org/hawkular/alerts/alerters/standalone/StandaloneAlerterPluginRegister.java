package org.hawkular.alerts.alerters.standalone;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.hawkular.alerts.alerters.api.AlerterPlugin;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class StandaloneAlerterPluginRegister {
    private static final MsgLogger log = MsgLogging.getMsgLogger(StandaloneAlerterPluginRegister.class);

    private static StandaloneAlerterPluginRegister instance;
    private static ExecutorService executor;

    Map<String, AlerterPlugin> plugins;

    private StandaloneAlerterPluginRegister() {
        init();
    }

    public static void setExecutor(ExecutorService executor) {
        StandaloneAlerterPluginRegister.executor = executor;
    }

    public void init() {
        plugins = AlerterPlugins.getPlugins();
        log.info("Alerter Plugins load finished");
    }

    public static synchronized void start() {
        if (instance == null) {
            instance = new StandaloneAlerterPluginRegister();
        }
    }

    public static synchronized void stop() {
        if (instance != null && instance.plugins != null) {
            instance.plugins.entrySet().stream().forEach(pluginEntry -> {
                log.infof("Stopping Alerter %s", pluginEntry.getKey());
                pluginEntry.getValue().stop();
            });
        }
        instance = null;
    }

}
