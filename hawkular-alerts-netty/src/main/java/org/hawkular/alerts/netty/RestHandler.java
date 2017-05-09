package org.hawkular.alerts.netty;

import java.util.List;
import java.util.Map;

import org.reactivestreams.Publisher;

import reactor.ipc.netty.http.server.HttpServerRequest;
import reactor.ipc.netty.http.server.HttpServerResponse;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public interface RestHandler {

    Publisher<Void> process(HttpServerRequest req,
                            HttpServerResponse resp,
                            String tenantId,
                            String subpath,
                            Map<String, List<String>> params);
}
