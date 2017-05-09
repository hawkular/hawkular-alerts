package org.hawkular.alerts.netty;

import static org.hawkular.alerts.netty.util.ResponseUtil.badRequest;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.hawkular.alerts.log.MsgLogger;
import org.hawkular.alerts.properties.AlertProperties;
import org.jboss.logging.Logger;
import org.reactivestreams.Publisher;

import io.netty.handler.codec.http.QueryStringDecoder;
import reactor.ipc.netty.http.server.HttpServerRequest;
import reactor.ipc.netty.http.server.HttpServerResponse;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class HandlersManager {
    private static final MsgLogger log = Logger.getMessageLogger(MsgLogger.class, HandlersManager.class.getName());
    private static final String BASE_URL = "hawkular-alerts.base-url";
    private static final String BASE_URL_DEFAULT = "/hawkular/alerts";
    public static final String TENANT_HEADER_NAME = "Hawkular-Tenant";
    public static final String ROOT = "/";

    private String baseUrl = AlertProperties.getProperty(BASE_URL, BASE_URL_DEFAULT);
    private Map<String, RestHandler> endpoints = new HashMap<>();
    private ClassLoader cl = Thread.currentThread().getContextClassLoader();

    public void start() {
        try {
            scan();
            log.info("Netty Handlers scan finished");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    public Publisher<Void> process(HttpServerRequest req, HttpServerResponse resp) {
        QueryStringDecoder query = new QueryStringDecoder(req.uri());
        String path = query.path();
        Map<String, List<String>> params = query.parameters();
        log.debugf("%s %s %s", req.method().name(), path, params);
        if (path.length() >= baseUrl.length()) {
            String base = query.path().substring(0, baseUrl.length());
            if (baseUrl.equals(base)) {
                String endpoint = query.path().substring(baseUrl.length());
                String subpath = endpoint;
                if (endpoint.lastIndexOf('/') > 0) {
                    endpoint = endpoint.substring(0, endpoint.indexOf('/', 1));
                }
                subpath = subpath.substring(endpoint.length());
                if (endpoint.isEmpty()) {
                    endpoint = ROOT;
                }
                if (subpath.isEmpty()) {
                    subpath = ROOT;
                }
                if (endpoints.get(endpoint) != null) {
                    return endpoints.get(endpoint).process(req, resp, tenantId(req), subpath, params);
                } else {
                    subpath = subpath.equals(ROOT) ? endpoint : endpoint + subpath;
                    return endpoints.get(ROOT).process(req, resp, tenantId(req), subpath, params);
                }
            }
        }
        return badRequest(resp, "Endpoint [" + path + "] is not supported.");
    }

    public String tenantId(HttpServerRequest req) {
        return req.requestHeaders().get(TENANT_HEADER_NAME);
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
