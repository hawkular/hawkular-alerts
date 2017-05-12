package org.hawkular.alerts.netty;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hawkular.alerts.log.MsgLogger;
import org.hawkular.alerts.properties.AlertProperties;
import org.jboss.logging.Logger;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class HandlersManager {
    private static final MsgLogger log = Logger.getMessageLogger(MsgLogger.class, HandlersManager.class.getName());
    private static final String BASE_URL = "hawkular-alerts.base-url";
    private static final String BASE_URL_DEFAULT = "/hawkular/alerts";
    public static final String TENANT_HEADER_NAME = "Hawkular-Tenant";

    private Router router;
    private String baseUrl = AlertProperties.getProperty(BASE_URL, BASE_URL_DEFAULT);
    private Map<String, RestHandler> endpoints = new HashMap<>();
    private ClassLoader cl = Thread.currentThread().getContextClassLoader();

    public HandlersManager(Vertx vertx) {
        this.router = Router.router(vertx);
    }

    public void start() {
        try {
            scan();
            log.info("Netty Handlers scan finished");
            router.route(baseUrl + "*").handler(BodyHandler.create());
            endpoints.entrySet().stream().forEach(endpoint -> endpoint.getValue().initRoutes(baseUrl, router));
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    public void handle(HttpServerRequest req) {
        log.debugf("%s %s %s", req.method().name(), req.path(), req.params());
        router.accept(req);
    }

    private void scan() throws IOException {
        String[] classpath = System.getProperty("java.class.path").split(":");
        for (int i=0; i<classpath.length; i++) {
            if (classpath[i].contains("hawkular") && classpath[i].endsWith("jar")) {
                ZipInputStream zip = new ZipInputStream(new FileInputStream(classpath[i]));
                for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                    if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                        String className = entry.getName().replace('/', '.'); // including ".class"
                        className = className.substring(0, className.length() - 6);
                        try {
                            Class clazz = cl.loadClass(className);
                            if (clazz.isAnnotationPresent(RestEndpoint.class)) {
                                RestEndpoint endpoint = (RestEndpoint)clazz.getAnnotation(RestEndpoint.class);
                                Class[] interfaces = clazz.getInterfaces();
                                for (int j=0; j<interfaces.length; j++) {
                                    if (interfaces[j].equals(RestHandler.class)) {
                                        log.infof("Endpoint [ %s ] - Handler [%s]", endpoint.path(), clazz.getName());
                                        endpoints.put(endpoint.path(), ((RestHandler) clazz.newInstance()));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.errorf(e,"Error loading Handler [%s]. Reason: %s", className, e.toString());
                            System.exit(1);
                        }
                    }
                }
            }
        }
    }
}
