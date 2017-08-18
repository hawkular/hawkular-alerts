package org.hawkular.alerts.handlers;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hawkular.alerts.api.json.JsonUtil.collectionFromJson;
import static org.hawkular.alerts.api.json.JsonUtil.fromJson;
import static org.hawkular.alerts.api.json.JsonUtil.toJson;
import static org.hawkular.alerts.api.util.Util.isEmpty;
import static org.hawkular.alerts.handlers.util.ResponseUtil.PARAMS_PAGING;
import static org.hawkular.alerts.handlers.util.ResponseUtil.checkForUnknownQueryParams;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.handlers.util.ResponseUtil;
import org.hawkular.handlers.RestEndpoint;
import org.hawkular.handlers.RestHandler;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/events")
public class EventsHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(EventsHandler.class);
    private static final String PARAM_START_TIME = "startTime";
    private static final String PARAM_END_TIME = "endTime";
    private static final String PARAM_EVENT_ID = "eventId";
    private static final String PARAM_EVENT_IDS = "eventIds";
    private static final String PARAM_TRIGGER_IDS = "triggerIds";
    private static final String PARAM_CATEGORIES = "categories";
    private static final String PARAM_TAGS = "tags";
    private static final String PARAM_TAG_QUERY = "tagQuery";
    private static final String PARAM_THIN = "thin";
    private static final String PARAM_WATCH_INTERVAL = "watchInterval";
    private static final String PARAM_TAG_NAMES = "tagNames";
    private static final String PARAM_EVENT_TYPE = "eventType";

    protected static final String FIND_EVENTS = "findEvents";
    protected static final String WATCH_EVENTS = "watchEvents";
    private static final String DELETE_EVENTS = "deleteEvents";
    protected static final Map<String, Set<String>> queryParamValidationMap = new HashMap<>();
    static {
        Collection<String> EVENTS_CRITERIA = Arrays.asList(PARAM_START_TIME,
                PARAM_END_TIME,
                PARAM_EVENT_IDS,
                PARAM_TRIGGER_IDS,
                PARAM_CATEGORIES,
                PARAM_TAGS,
                PARAM_TAG_QUERY,
                PARAM_EVENT_TYPE,
                PARAM_THIN);
        queryParamValidationMap.put(FIND_EVENTS, new HashSet<>(EVENTS_CRITERIA));
        queryParamValidationMap.get(FIND_EVENTS).addAll(PARAMS_PAGING);
        queryParamValidationMap.put(WATCH_EVENTS, new HashSet<>(EVENTS_CRITERIA));
        queryParamValidationMap.get(WATCH_EVENTS).add(PARAM_WATCH_INTERVAL);
        queryParamValidationMap.put(DELETE_EVENTS, new HashSet<>(EVENTS_CRITERIA));
        queryParamValidationMap.get(DELETE_EVENTS).add(PARAM_EVENT_ID);
    }

    AlertsService alertsService;

    public EventsHandler() {
        alertsService = StandaloneAlerts.getAlertsService();
    }

    @Override
    public void initRoutes(String baseUrl, Router router) {
        String path = baseUrl + "/events";
        router.post(path).handler(this::createEvent);
        router.post(path + "/data").handler(this::sendEvents);
        router.put(path + "/tags").handler(this::addTags);
        router.delete(path + "/tags").handler(this::removeTags);
        router.get(path).handler(this::findEvents);
        router.get(path + "/watch").blockingHandler(this::watchEvents);
        router.put(path + "/delete").handler(this::deleteEvents);
        router.delete(path + "/:eventId").handler(this::deleteEvents);
        router.get(path + "/event/:eventId").handler(this::getEvent);
    }

    void createEvent(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String json = routing.getBodyAsString();
                    Event event;
                    try {
                        event = fromJson(json, Event.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing Event json: %s. Reason: %s", json, e.toString());
                        throw new ResponseUtil.BadRequestException(e.toString());
                    }
                    if (event == null) {
                        throw new ResponseUtil.BadRequestException("Event null.");
                    }
                    if (isEmpty(event.getId())) {
                        throw new ResponseUtil.BadRequestException("Event with id null.");
                    }
                    if (isEmpty(event.getCategory())) {
                        throw new ResponseUtil.BadRequestException("Event with category null.");
                    }
                    event.setTenantId(tenantId);
                    if (!ResponseUtil.checkTags(event)) {
                        throw new ResponseUtil.BadRequestException("Tags " + event.getTags() + " must be non empty.");
                    }
                    Event found;
                    try {
                        found = alertsService.getEvent(tenantId, event.getId(), true);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                    if (found != null) {
                        throw new ResponseUtil.BadRequestException("Event with ID [" + event.getId() + "] exists.");
                    }
                    try {
                        alertsService.addEvents(Collections.singletonList(event));
                        future.complete(event);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void sendEvents(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String json = routing.getBodyAsString();
                    Collection<Event> events;
                    try {
                        events = collectionFromJson(json, Event.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing Event json: %s. Reason: %s", json, e.toString());
                        throw new ResponseUtil.BadRequestException(e.toString());
                    }
                    if (isEmpty(events)) {
                        throw new ResponseUtil.BadRequestException("Events is empty");
                    }
                    try {
                        events.stream().forEach(ev -> ev.setTenantId(tenantId));
                        alertsService.sendEvents(events);
                        log.debugf("Events: ", events);
                        future.complete(events);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void addTags(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String eventIds = null;
                    String tags = null;
                    if (routing.request().params().get(PARAM_EVENT_IDS) != null) {
                        eventIds = routing.request().params().get(PARAM_EVENT_IDS);
                    }
                    if (routing.request().params().get(PARAM_TAGS) != null) {
                        tags = routing.request().params().get(PARAM_TAGS);
                    }
                    if (isEmpty(eventIds) || isEmpty(tags)) {
                        throw new ResponseUtil.BadRequestException("EventIds and Tags required for adding tags");
                    }
                    try {
                        List<String> eventIdList = Arrays.asList(eventIds.split(","));
                        Map<String, String> tagsMap = ResponseUtil.parseTags(tags);
                        alertsService.addEventTags(tenantId, eventIdList, tagsMap);
                        log.debugf("Tagged eventIds:%s, %s", eventIdList, tagsMap);
                        future.complete(tagsMap);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void removeTags(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String eventIds = null;
                    String tagNames = null;
                    if (routing.request().params().get(PARAM_EVENT_IDS) != null) {
                        eventIds = routing.request().params().get(PARAM_EVENT_IDS);
                    }
                    if (routing.request().params().get(PARAM_TAG_NAMES) != null) {
                        tagNames = routing.request().params().get(PARAM_TAG_NAMES);
                    }
                    if (isEmpty(eventIds) || isEmpty(tagNames)) {
                        throw new ResponseUtil.BadRequestException("EventIds and Tags required for removing tags");
                    }
                    try {
                        Collection<String> ids = Arrays.asList(eventIds.split(","));
                        Collection<String> tags = Arrays.asList(tagNames.split(","));
                        alertsService.removeEventTags(tenantId, ids, tags);
                        log.debugf("Untagged eventsIds:%s, %s", ids, tags);
                        future.complete(tags);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void findEvents(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    try {
                        checkForUnknownQueryParams(routing.request().params(), queryParamValidationMap.get(FIND_EVENTS));
                        Pager pager = ResponseUtil.extractPaging(routing.request().params());
                        EventsCriteria criteria = buildCriteria(routing.request().params());
                        Page<Event> eventPage = alertsService.getEvents(tenantId, criteria, pager);
                        log.debugf("Events: %s", eventPage);
                        future.complete(eventPage);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> ResponseUtil.result(routing, res));
    }

    void watchEvents(RoutingContext routing) {
        String tenantId = ResponseUtil.checkTenant(routing);
        try {
            checkForUnknownQueryParams(routing.request().params(), queryParamValidationMap.get(WATCH_EVENTS));
        } catch (IllegalArgumentException e) {
            ResponseUtil.badRequest(routing, e.getMessage());
            return;
        }
        EventsCriteria criteria = buildCriteria(routing.request().params());
        Long watchInterval = null;
        if (routing.request().params().get(PARAM_WATCH_INTERVAL) != null) {
            watchInterval = Long.valueOf(routing.request().params().get(PARAM_WATCH_INTERVAL));
        }
        routing.response()
                .putHeader(ResponseUtil.ACCEPT, ResponseUtil.APPLICATION_JSON)
                .putHeader(ResponseUtil.CONTENT_TYPE, ResponseUtil.APPLICATION_JSON)
                .setChunked(true)
                .setStatusCode(OK.code());

        EventsWatcher.EventsListener listener = event -> {
            routing.response().write(toJson(event) + "\r\n");
        };
        String channelId = routing.request().connection().remoteAddress().toString();
        EventsWatcher watcher = new EventsWatcher(channelId, listener, Collections.singleton(tenantId), criteria, watchInterval);
        watcher.start();
        log.infof("EventsWatcher [%s] created", channelId);
        routing.response().closeHandler(e -> {
            watcher.dispose();
            log.infof("EventsWatcher [%s] finished", channelId);
        });
    }

    void deleteEvents(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String eventId = routing.request().getParam(PARAM_EVENT_ID);
                    int numDeleted;
                    try {
                        checkForUnknownQueryParams(routing.request().params(), queryParamValidationMap.get(DELETE_EVENTS));
                        EventsCriteria criteria = buildCriteria(routing.request().params());
                        criteria.setEventId(eventId);
                        numDeleted = alertsService.deleteEvents(tenantId, criteria);
                        log.debugf("Events deleted: %s", numDeleted);
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                    if (numDeleted <= 0 && eventId != null) {
                        throw new ResponseUtil.NotFoundException("Event " + eventId + " doesn't exist for delete");
                    }
                    Map<String, Integer> deleted = new HashMap<>();
                    deleted.put("deleted", new Integer(numDeleted));
                    future.complete(deleted);
                }, res -> ResponseUtil.result(routing, res));
    }

    void getEvent(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = ResponseUtil.checkTenant(routing);
                    String eventId = routing.request().getParam(PARAM_EVENT_ID);
                    boolean thin = false;
                    if (routing.request().params().get(PARAM_THIN) != null) {
                        thin = Boolean.valueOf(routing.request().params().get(PARAM_THIN));
                    }
                    Event found;
                    try {
                        found = alertsService.getEvent(tenantId, eventId, thin);
                        if (found != null) {
                            future.complete(found);
                            return;
                        }
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                    throw new ResponseUtil.NotFoundException("eventId: " + eventId + " not found");
                }, res -> ResponseUtil.result(routing, res));
    }

    public static EventsCriteria buildCriteria(MultiMap params) {
        Long startTime = null;
        Long endTime = null;
        String eventIds = null;
        String triggerIds = null;
        String categories = null;
        String tags = null;
        String tagQuery = null;
        boolean thin = false;
        String eventType = null;

        if (params.get(PARAM_START_TIME) != null) {
            startTime = Long.valueOf(params.get(PARAM_START_TIME));
        }
        if (params.get(PARAM_END_TIME) != null) {
            endTime = Long.valueOf(params.get(PARAM_END_TIME));
        }
        if (params.get(PARAM_EVENT_IDS) != null) {
            eventIds = params.get(PARAM_EVENT_IDS);
        }
        if (params.get(PARAM_TRIGGER_IDS) != null) {
            triggerIds = params.get(PARAM_TRIGGER_IDS);
        }
        if (params.get(PARAM_CATEGORIES) != null) {
            categories = params.get(PARAM_CATEGORIES);
        }
        if (params.get(PARAM_TAGS) != null) {
            tags = params.get(PARAM_TAGS);
        }
        if (params.get(PARAM_TAG_QUERY) != null) {
            tagQuery = params.get(PARAM_TAG_QUERY);
        }
        String unifiedTagQuery;
        if (!isEmpty(tags)) {
            unifiedTagQuery = ResponseUtil.parseTagQuery(ResponseUtil.parseTags(tags));
        } else {
            unifiedTagQuery = tagQuery;
        }
        if (params.get(PARAM_THIN) != null) {
            thin = Boolean.valueOf(params.get(PARAM_THIN));
        }
        if (params.get(PARAM_EVENT_TYPE) != null) {
            eventType = params.get(PARAM_EVENT_TYPE);
        }
        return new EventsCriteria(startTime, endTime, eventIds, triggerIds, categories, unifiedTagQuery, eventType, thin);
    }
}
