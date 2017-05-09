package org.hawkular.alerts.netty.handlers;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hawkular.alerts.api.json.JsonUtil.toJson;
import static org.hawkular.alerts.netty.HandlersManager.TENANT_HEADER_NAME;
import static org.hawkular.alerts.netty.util.ResponseUtil.badRequest;
import static org.hawkular.alerts.netty.util.ResponseUtil.handleExceptions;
import static org.hawkular.alerts.netty.util.ResponseUtil.internalServerError;
import static org.hawkular.alerts.netty.util.ResponseUtil.isEmpty;
import static org.hawkular.alerts.netty.util.ResponseUtil.notFound;
import static org.hawkular.alerts.netty.util.ResponseUtil.ok;
import static reactor.core.publisher.Mono.just;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.log.MsgLogger;
import org.hawkular.alerts.netty.RestEndpoint;
import org.hawkular.alerts.netty.RestHandler;
import org.hawkular.alerts.netty.util.ResponseUtil;
import org.hawkular.alerts.netty.util.ResponseUtil.ApiError;
import org.hawkular.alerts.netty.util.ResponseUtil.InternalServerException;
import org.jboss.logging.Logger;
import org.reactivestreams.Publisher;

import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.netty.http.server.HttpServerRequest;
import reactor.ipc.netty.http.server.HttpServerResponse;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/plugins")
public class ActionPluginHandler implements RestHandler {
    private static final MsgLogger log = Logger.getMessageLogger(MsgLogger.class, ActionPluginHandler.class.getName());
    private static final String ROOT = "/";

    DefinitionsService definitionsService;

    public ActionPluginHandler() {
        definitionsService = StandaloneAlerts.getDefinitionsService();
    }

    @Override
    public Publisher<Void> process(HttpServerRequest req,
                                   HttpServerResponse resp,
                                   String tenantId,
                                   String subpath,
                                   Map<String, List<String>> params) {
        HttpMethod method = req.method();
        if (isEmpty(tenantId)) {
            return badRequest(resp, TENANT_HEADER_NAME + " header is required");
        }

        // GET /
        if (method == GET && subpath.equals(ROOT)) {
            return findActionPlugins(req, resp);
        }
        // GET /{actionPlugin}
        if (method == GET && subpath.indexOf('/', 1) == -1) {
            String actionPlugin = subpath.substring(1);
            return getActionPlugin(req, resp, actionPlugin);
        }
        return badRequest(resp, "Wrong path " + method + " " + subpath);
    }

    Publisher<Void> findActionPlugins(HttpServerRequest req, HttpServerResponse resp) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    try {
                        Collection<String> actionPlugins = definitionsService.getActionPlugins();
                        log.debugf("ActionPlugins: %s", actionPlugins);
                        return actionPlugins;
                    } catch (Exception e) {
                        log.errorf(e, "Error querying all plugins. Reason: %s", e.toString());
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(actionPlugins -> ok(resp, actionPlugins))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> getActionPlugin(HttpServerRequest req, HttpServerResponse resp, String actionPlugin) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    Set<String> actionPluginProps;
                    try {
                        actionPluginProps = definitionsService.getActionPlugin(actionPlugin);
                        log.debugf("ActionPlugin: %s - Properties: %s", actionPlugin, actionPluginProps);
                    } catch (Exception e) {
                        log.errorf(e, "Error querying plugin %s. Reason: %s", actionPlugin, e.toString());
                        throw new InternalServerException(e.toString());
                    }
                    if (actionPluginProps == null) {
                        throw new ResponseUtil.NotFoundException("Not found action plugin: " + actionPlugin);
                    }
                    return actionPluginProps;
                }))
                .flatMap(actionPluginProps -> ok(resp, actionPluginProps))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }
}
