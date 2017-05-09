package org.hawkular.alerts.netty.handlers;

import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hawkular.alerts.api.json.JsonUtil.collectionFromJson;
import static org.hawkular.alerts.api.json.JsonUtil.fromJson;
import static org.hawkular.alerts.api.json.JsonUtil.toJson;
import static org.hawkular.alerts.netty.HandlersManager.TENANT_HEADER_NAME;
import static org.hawkular.alerts.netty.util.ResponseUtil.badRequest;
import static org.hawkular.alerts.netty.util.ResponseUtil.checkTags;
import static org.hawkular.alerts.netty.util.ResponseUtil.extractPaging;
import static org.hawkular.alerts.netty.util.ResponseUtil.handleExceptions;
import static org.hawkular.alerts.netty.util.ResponseUtil.isEmpty;
import static org.hawkular.alerts.netty.util.ResponseUtil.ok;
import static org.hawkular.alerts.netty.util.ResponseUtil.paginatedOk;
import static org.hawkular.alerts.netty.util.ResponseUtil.parseTagQuery;
import static org.hawkular.alerts.netty.util.ResponseUtil.parseTags;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.log.MsgLogger;
import org.hawkular.alerts.netty.RestEndpoint;
import org.hawkular.alerts.netty.RestHandler;
import org.hawkular.alerts.netty.handlers.EventsWatcher.EventsListener;
import org.hawkular.alerts.netty.util.ResponseUtil;
import org.hawkular.alerts.netty.util.ResponseUtil.BadRequestException;
import org.hawkular.alerts.netty.util.ResponseUtil.InternalServerException;
import org.jboss.logging.Logger;
import org.reactivestreams.Publisher;

import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.ipc.netty.http.server.HttpServerRequest;
import reactor.ipc.netty.http.server.HttpServerResponse;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/events")
public class EventsHandler implements RestHandler {
    private static final MsgLogger log = Logger.getMessageLogger(MsgLogger.class, EventsHandler.class.getName());
    private static final String ROOT = "/";
    private static final String DATA = "/data";
    private static final String TAGS = "/tags";
    private static final String WATCH = "/watch";
    private static final String _DELETE = "/delete";
    private static final String EVENT = "/event";
    private static final String PARAM_START_TIME = "startTime";
    private static final String PARAM_END_TIME = "endTime";
    private static final String PARAM_EVENT_IDS = "eventIds";
    private static final String PARAM_TRIGGER_IDS = "triggerIds";
    private static final String PARAM_CATEGORIES = "categories";
    private static final String PARAM_TAGS = "tags";
    private static final String PARAM_TAG_QUERY = "tagQuery";
    private static final String PARAM_THIN = "thin";
    private static final String PARAM_WATCH_INTERVAL = "watchInterval";
    private static final String PARAM_TAG_NAMES = "tagNames";

    AlertsService alertsService;

    public EventsHandler() {
        alertsService = StandaloneAlerts.getAlertsService();
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

        // POST /
        if (method == POST && subpath.equals(ROOT)) {
            return createEvent(req, resp, tenantId);
        }
        // POST /data
        if (method == POST && subpath.equals(DATA)) {
            return sendEvents(req, resp, tenantId);
        }
        // PUT /tags
        if (method == PUT && subpath.equals(TAGS)) {
            return addTags(req, resp, tenantId, params);
        }
        // DELETE /tags
        if (method == DELETE && subpath.equals(TAGS)) {
            return removeTags(req, resp, tenantId, params);
        }
        // GET /
        if (method == GET && subpath.equals(ROOT)) {
            return findEvents(req, resp, tenantId, params, req.uri());
        }
        // GET /watch
        if (method == GET && subpath.equals(WATCH)) {
            return watchEvents(resp, tenantId, params);
        }
        // PUT /delete
        if (method == PUT && subpath.equals(_DELETE)) {
            return deleteEvents(req, resp, tenantId, null, params);
        }
        String[] tokens = subpath.substring(1).split(ROOT);

        // DELETE /{eventId}
        if (method == DELETE && tokens.length == 1) {
            return deleteEvents(req, resp, tenantId, tokens[0], params);
        }

        // GET /event/{eventId}
        if (method == GET && subpath.startsWith(EVENT) && tokens.length == 2) {
            return getEvent(req, resp, tenantId, tokens[1], params);
        }

        return badRequest(resp, "Wrong path " + method + " " + subpath);
    }

    Publisher<Void> createEvent(HttpServerRequest req, HttpServerResponse resp, String tenantId) {
        return req
                .receive()
                .aggregate()
                .asString()
                .publishOn(Schedulers.elastic())
                .map(json -> {
                    Event parsed;
                    try {
                        parsed = fromJson(json, Event.class);
                        return parsed;
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing Event json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                })
                .flatMap(event -> {
                    if (event == null) {
                        throw new BadRequestException("Event null.");
                    }
                    if (isEmpty(event.getId())) {
                        throw new BadRequestException("Event with id null.");
                    }
                    if (isEmpty(event.getCategory())) {
                        throw new BadRequestException("Event with category null.");
                    }
                    event.setTenantId(tenantId);
                    if (!checkTags(event)) {
                        throw new BadRequestException("Tags " + event.getTags() + " must be non empty.");
                    }
                    Event found;
                    try {
                        found = alertsService.getEvent(tenantId, event.getId(), true);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                    if (found != null) {
                        throw new BadRequestException("Event with ID [" + event.getId() + "] exists.");
                    }
                    try {
                        alertsService.addEvents(Collections.singletonList(event));
                        return ok(resp, event);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> sendEvents(HttpServerRequest req, HttpServerResponse resp, String tenantId) {
        return req
                .receive()
                .aggregate()
                .asString()
                .publishOn(Schedulers.elastic())
                .map(json -> {
                    Collection<Event> parsed;
                    try {
                        parsed = collectionFromJson(json, Event.class);
                        return parsed;
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing Event json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                })
                .flatMap(events -> {
                    if (isEmpty(events)) {
                        throw new BadRequestException("Events is empty");
                    }
                    try {
                        events.stream().forEach(ev -> ev.setTenantId(tenantId));
                        alertsService.sendEvents(events);
                        log.debugf("Events: ", events);
                        return ok(resp);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> addTags(HttpServerRequest req, HttpServerResponse resp, String tenantId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    String eventIds = null;
                    String tags = null;
                    if (params.get(PARAM_EVENT_IDS) != null) {
                        eventIds = params.get(PARAM_EVENT_IDS).get(0);
                    }
                    if (params.get(PARAM_TAGS) != null) {
                        tags = params.get(PARAM_TAGS).get(0);
                    }
                    if (isEmpty(eventIds) || isEmpty(tags)) {
                        throw new BadRequestException("EventIds and Tags required for adding tags");
                    }
                    try {
                        List<String> eventIdList = Arrays.asList(eventIds.split(","));
                        Map<String, String> tagsMap = parseTags(tags);
                        alertsService.addEventTags(tenantId, eventIdList, tagsMap);
                        log.debugf("Tagged eventIds:%s, %s", eventIdList, tagsMap);
                        return tagsMap;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(tagsMap -> ok(resp))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> removeTags(HttpServerRequest req, HttpServerResponse resp, String tenantId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    String eventIds = null;
                    String tagNames = null;
                    if (params.get(PARAM_EVENT_IDS) != null) {
                        eventIds = params.get(PARAM_EVENT_IDS).get(0);
                    }
                    if (params.get(PARAM_TAG_NAMES) != null) {
                        tagNames = params.get(PARAM_TAG_NAMES).get(0);
                    }
                    if (isEmpty(eventIds) || isEmpty(tagNames)) {
                        throw new BadRequestException("EventIds and Tags required for removing tags");
                    }
                    try {
                        Collection<String> ids = Arrays.asList(eventIds.split(","));
                        Collection<String> tags = Arrays.asList(tagNames.split(","));
                        alertsService.removeEventTags(tenantId, ids, tags);
                        log.debugf("Untagged eventsIds:%s, %s", ids, tags);
                        return tags;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(tags -> ok(resp))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> findEvents(HttpServerRequest req, HttpServerResponse resp, String tenantId, Map<String, List<String>> params, String uri) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    try {
                        Pager pager = extractPaging(params);
                        EventsCriteria criteria = buildCriteria(params);
                        Page<Event> eventPage = alertsService.getEvents(tenantId, criteria, pager);
                        log.debugf("Events: %s", eventPage);
                        return eventPage;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(eventPage -> {
                    if (isEmpty(eventPage)) {
                        return ok(resp, eventPage);
                    }
                    return paginatedOk(req, resp, eventPage, uri);
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> watchEvents(HttpServerResponse resp, String tenantId, Map<String, List<String>> params) {
        EventsCriteria criteria = buildCriteria(params);
        Flux<String> watcherFlux = Flux.create(sink -> {
            Long watchInterval = null;
            if (params.get(PARAM_WATCH_INTERVAL) != null) {
                watchInterval = Long.valueOf(params.get(PARAM_WATCH_INTERVAL).get(0));
            }
            EventsListener listener = event -> {
                sink.next(toJson(event) + "\r\n");
            };
            String channelId = resp.context().channel().id().asShortText();
            EventsWatcher watcher = new EventsWatcher(channelId, listener, Collections.singleton(tenantId), criteria, watchInterval);
            sink.onCancel(() -> watcher.dispose());
            watcher.start();
        });
        resp.status(OK);
        // Watcher send events one by one, so flux is splited in windows of one element
        return watcherFlux.window(1).concatMap(w -> resp.sendString(w));
    }

    Publisher<Void> deleteEvents(HttpServerRequest req, HttpServerResponse resp, String tenantId, String eventId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    int numDeleted = -1;
                    try {
                        EventsCriteria criteria = buildCriteria(params);
                        criteria.setEventId(eventId);
                        numDeleted = alertsService.deleteEvents(tenantId, criteria);
                        log.debugf("Events deleted: ", numDeleted);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    if (numDeleted <= 0 && eventId != null) {
                        throw new ResponseUtil.NotFoundException("Event " + eventId + " doesn't exist for delete");
                    }
                    Map<String, Integer> deleted = new HashMap<>();
                    deleted.put("deleted", new Integer(numDeleted));
                    return deleted;
                }))
                .flatMap(deleted -> ok(resp, deleted))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> getEvent(HttpServerRequest req, HttpServerResponse resp, String tenantId, String eventId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    boolean thin = false;
                    if (params.get(PARAM_THIN) != null) {
                        thin = Boolean.valueOf(params.get(PARAM_THIN).get(0));
                    }
                    Event found;
                    try {
                        found = alertsService.getEvent(tenantId, eventId, thin);
                        if (found != null) {
                            return found;
                        }
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    throw new ResponseUtil.NotFoundException("eventId: " + eventId + " not found");
                }))
                .flatMap(found -> ok(resp, found))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    EventsCriteria buildCriteria(Map<String, List<String>> params) {
        Long startTime = null;
        Long endTime = null;
        String eventIds = null;
        String triggerIds = null;
        String categories = null;
        String tags = null;
        String tagQuery = null;
        boolean thin = false;

        if (params.get(PARAM_START_TIME) != null) {
            startTime = Long.valueOf(params.get(PARAM_START_TIME).get(0));
        }
        if (params.get(PARAM_END_TIME) != null) {
            endTime = Long.valueOf(params.get(PARAM_END_TIME).get(0));
        }
        if (params.get(PARAM_EVENT_IDS) != null) {
            eventIds = params.get(PARAM_EVENT_IDS).get(0);
        }
        if (params.get(PARAM_TRIGGER_IDS) != null) {
            triggerIds = params.get(PARAM_TRIGGER_IDS).get(0);
        }
        if (params.get(PARAM_CATEGORIES) != null) {
            categories = params.get(PARAM_CATEGORIES).get(0);
        }
        if (params.get(PARAM_TAGS) != null) {
            tags = params.get(PARAM_TAGS).get(0);
        }
        if (params.get(PARAM_TAG_QUERY) != null) {
            tagQuery = params.get(PARAM_TAG_QUERY).get(0);
        }
        String unifiedTagQuery;
        if (!isEmpty(tags)) {
            unifiedTagQuery = parseTagQuery(parseTags(tags));
        } else {
            unifiedTagQuery = tagQuery;
        }
        if (params.get(PARAM_THIN) != null) {
            thin = Boolean.valueOf(params.get(PARAM_THIN).get(0));
        }
        return new EventsCriteria(startTime, endTime, eventIds, triggerIds, categories, unifiedTagQuery, thin);
    }
}
