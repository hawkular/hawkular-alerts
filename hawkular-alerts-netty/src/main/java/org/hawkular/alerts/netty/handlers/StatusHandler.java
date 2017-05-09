package org.hawkular.alerts.netty.handlers;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hawkular.alerts.api.json.JsonUtil.toJson;
import static org.hawkular.alerts.netty.util.ResponseUtil.ok;
import static reactor.core.publisher.Mono.just;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hawkular.alerts.api.services.StatusService;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.netty.RestEndpoint;
import org.hawkular.alerts.netty.RestHandler;
import org.hawkular.alerts.netty.util.ManifestUtil;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.netty.http.server.HttpServerRequest;
import reactor.ipc.netty.http.server.HttpServerResponse;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/status")
public class StatusHandler implements RestHandler {
    static final String STATUS = "status";
    static final String STARTED = "STARTED";
    static final String FAILED = "FAILED";
    static final String DISTRIBUTED = "distributed";

    StatusService statusService;
    ManifestUtil manifestUtil;

    public StatusHandler() {
        manifestUtil = new ManifestUtil();
        statusService = StandaloneAlerts.getStatusService();
    }

    @Override
    public Publisher<Void> process(HttpServerRequest req,
                                   HttpServerResponse resp,
                                   String tenantId,
                                   String subpath,
                                   Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    Map<String, String> status = new HashMap<>();
                    status.putAll(manifestUtil.getFrom());
                    if (statusService.isStarted()) {
                        status.put(STATUS, STARTED);
                    } else {
                        status.put(STATUS, FAILED);
                    }
                    boolean distributed = statusService.isDistributed();
                    status.put(DISTRIBUTED, Boolean.toString(distributed));
                    if (distributed) {
                        status.putAll(statusService.getDistributedStatus());
                    }
                    return status;
                }))
                .flatMap(status -> ok(resp, status));
    }
}
