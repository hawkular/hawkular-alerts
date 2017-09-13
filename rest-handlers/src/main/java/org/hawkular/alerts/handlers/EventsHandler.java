/*
 * Copyright 2015-2017 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.alerts.handlers;

import static org.hawkular.alerts.api.doc.DocConstants.DELETE;
import static org.hawkular.alerts.api.doc.DocConstants.GET;
import static org.hawkular.alerts.api.doc.DocConstants.POST;
import static org.hawkular.alerts.api.doc.DocConstants.PUT;
import static org.hawkular.alerts.api.json.JsonUtil.collectionFromJson;
import static org.hawkular.alerts.api.json.JsonUtil.fromJson;
import static org.hawkular.alerts.api.json.JsonUtil.toJson;
import static org.hawkular.alerts.api.util.Util.isEmpty;
import static org.hawkular.alerts.handlers.util.ResponseUtil.PARAMS_PAGING;
import static org.hawkular.alerts.handlers.util.ResponseUtil.checkForUnknownQueryParams;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.doc.DocEndpoint;
import org.hawkular.alerts.api.doc.DocParameter;
import org.hawkular.alerts.api.doc.DocParameters;
import org.hawkular.alerts.api.doc.DocPath;
import org.hawkular.alerts.api.doc.DocResponse;
import org.hawkular.alerts.api.doc.DocResponses;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.handlers.util.ResponseUtil;
import org.hawkular.alerts.handlers.util.ResponseUtil.ApiDeleted;
import org.hawkular.alerts.handlers.util.ResponseUtil.ApiError;
import org.hawkular.commons.log.MsgLogger;
import org.hawkular.commons.log.MsgLogging;
import org.hawkular.handlers.RestEndpoint;
import org.hawkular.handlers.RestHandler;

import io.vertx.core.MultiMap;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@RestEndpoint(path = "/events")
@DocEndpoint(value = "/events", description = "Events Handling")
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
        router.delete(path + "/:eventId").handler(this::deleteEvent);
        router.get(path + "/event/:eventId").handler(this::getEvent);
    }

    @DocPath(method = POST,
            path = "/",
            name = "Create a new Event.",
            notes = "Persist the new event and send it to the engine for processing/condition evaluation. + \n" +
                    "Returns created Event.")
    @DocParameters(value = {
            @DocParameter(required = true, body = true, type = Event.class,
                    description = "Event to be created. Category and Text fields required.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Event Created.", response = Event.class),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void createEvent(RoutingContext routing) {
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

    @DocPath(method = POST,
            path = "/data",
            name = "Send events to the engine for processing/condition evaluation. ",
            notes = "Only events generated by the engine are persisted. + \n" +
                    "Input events are treated as external data and those are not persisted into the system.")
    @DocParameters(value = {
            @DocParameter(required = true, body = true, type = Event.class, typeContainer = "List",
                    description = "Events to be processed by alerting.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Events Sent.", response = Event.class),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void sendEvents(RoutingContext routing) {
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

    @DocPath(method = PUT,
            path = "/tags",
            name = "Add tags to existing Events.")
    @DocParameters(value = {
            @DocParameter(name = "eventIds", required = true,
                    description = "List of eventIds to tag.",
                    allowableValues = "Comma separated list of events IDs."),
            @DocParameter(name = "tags", required = true,
                    description = "List of tags to add.",
                    allowableValues = "Comma separated list of tags. + \n" +
                            "Each tag of format 'name\\|description'.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Events tagged successfully.", response = Event.class),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void addTags(RoutingContext routing) {
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

    @DocPath(method = DELETE,
            path = "/tags",
            name = "Remove tags from existing Events.")
    @DocParameters(value = {
            @DocParameter(name = "eventIds", required = true,
                    description = "List of eventIds to untag.",
                    allowableValues = "Comma separated list of events IDs."),
            @DocParameter(name = "tagNames", required = true,
                    description = "List of tag names to remove.",
                    allowableValues = "Comma separated list of tags names.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Events untagged successfully.", response = Event.class),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void removeTags(RoutingContext routing) {
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

    @DocPath(method = GET,
            path = "/",
            name = "Get events with optional filtering.",
            notes = "If not criteria defined, it fetches all events stored in the system. + \n" +
                    "Tags Query language (BNF): + \n" +
                    "[source] \n" +
                    "---- \n" +
                    "<tag_query> ::= ( <expression> | \"(\" <object> \")\" " +
                    "| <object> <logical_operator> <object> ) \n" +
                    "<expression> ::= ( <tag_name> | <not> <tag_name> " +
                    "| <tag_name> <boolean_operator> <tag_value> | " +
                    "<tag_name> <array_operator> <array> ) \n" +
                    "<not> ::= [ \"NOT\" | \"not\" ] \n" +
                    "<logical_operator> ::= [ \"AND\" | \"OR\" | \"and\" | \"or\" ] \n" +
                    "<boolean_operator> ::= [ \"==\" | \"!=\" ] \n" +
                    "<array_operator> ::= [ \"IN\" | \"NOT IN\" | \"in\" | \"not in\" ] \n" +
                    "<array> ::= ( \"[\" \"]\" | \"[\" ( \",\" <tag_value> )* ) \n" +
                    "<tag_name> ::= <identifier> \n" +
                    "<tag_value> ::= ( \"'\" <regexp> \"'\" | <simple_value> ) \n" +
                    "; \n" +
                    "; <identifier> and <simple_value> follow pattern [a-zA-Z_0-9][\\-a-zA-Z_0-9]* \n" +
                    "; <regexp> follows any valid Java Regular Expression format \n" +
                    "---- \n")
    @DocParameters(value = {
            @DocParameter(name = "startTime",
                    description = "Filter out events created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endTime",
                    description = "Filter out events created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "eventIds",
                    description = "Filter out events for unspecified eventIds.",
                    allowableValues = "Comma separated list of event IDs."),
            @DocParameter(name = "triggerIds",
                    description = "Filter out events for unspecified triggers.",
                    allowableValues = "Comma separated list of trigger IDs."),
            @DocParameter(name = "categories",
                    description = "Filter out events for unspecified categories.",
                    allowableValues = "Comma separated list of category values."),
            @DocParameter(name = "tags",
                    description = "[DEPRECATED] Filter out events for unspecified tags.",
                    allowableValues = "Comma separated list of tags, each tag of format 'name\\|description'. + \n" +
                            "Specify '*' for description to match all values."),
            @DocParameter(name = "tagQuery",
                    description = "Filter out events for unspecified tags.",
                    allowableValues = "A tag query expression."),
            @DocParameter(name = "thin",
                    description = "Return only thin events, do not include: evalSets.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Successfully fetched list of events.", response = Event.class, responseContainer = "List"),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void findEvents(RoutingContext routing) {
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

    @DocPath(method = GET,
            path = "/watch",
            name = "Watch events with optional filtering.",
            notes =  "Return a stream of events ordered by ctime. + \n" +
                    " + \n" +
                    "If not criteria defined, it fetches all events stored in the system. + \n" +
                    "Tags Query language (BNF): + \n" +
                    "[source] \n" +
                    "---- \n" +
                    "<tag_query> ::= ( <expression> | \"(\" <object> \")\" " +
                    "| <object> <logical_operator> <object> ) \n" +
                    "<expression> ::= ( <tag_name> | <not> <tag_name> " +
                    "| <tag_name> <boolean_operator> <tag_value> | " +
                    "<tag_name> <array_operator> <array> ) \n" +
                    "<not> ::= [ \"NOT\" | \"not\" ] \n" +
                    "<logical_operator> ::= [ \"AND\" | \"OR\" | \"and\" | \"or\" ] \n" +
                    "<boolean_operator> ::= [ \"==\" | \"!=\" ] \n" +
                    "<array_operator> ::= [ \"IN\" | \"NOT IN\" | \"in\" | \"not in\" ] \n" +
                    "<array> ::= ( \"[\" \"]\" | \"[\" ( \",\" <tag_value> )* ) \n" +
                    "<tag_name> ::= <identifier> \n" +
                    "<tag_value> ::= ( \"'\" <regexp> \"'\" | <simple_value> ) \n" +
                    "; \n" +
                    "; <identifier> and <simple_value> follow pattern [a-zA-Z_0-9][\\-a-zA-Z_0-9]* \n" +
                    "; <regexp> follows any valid Java Regular Expression format \n" +
                    "---- \n")
    @DocParameters(value = {
            @DocParameter(name = "startTime",
                    description = "Filter out events created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endTime",
                    description = "Filter out events created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "eventIds",
                    description = "Filter out events for unspecified eventIds.",
                    allowableValues = "Comma separated list of event IDs."),
            @DocParameter(name = "triggerIds",
                    description = "Filter out events for unspecified triggers.",
                    allowableValues = "Comma separated list of trigger IDs."),
            @DocParameter(name = "categories",
                    description = "Filter out events for unspecified categories.",
                    allowableValues = "Comma separated list of category values."),
            @DocParameter(name = "tags",
                    description = "[DEPRECATED] Filter out events for unspecified tags.",
                    allowableValues = "Comma separated list of tags, each tag of format 'name\\|description'. + \n" +
                            "Specify '*' for description to match all values."),
            @DocParameter(name = "tagQuery",
                    description = "Filter out events for unspecified tags.",
                    allowableValues = "A tag query expression."),
            @DocParameter(name = "watchInterval", type = Long.class,
                    description = "Define interval when watcher notifications will be sent.",
                    allowableValues = "Interval in seconds"),
            @DocParameter(name = "thin", type = Boolean.class,
                    description = "Return only thin events, do not include: evalSets.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Stream of events.", response = Event.class),
            @DocResponse(code = 200, message = "Errors will close the stream. Description is sent before stream is closed.", response = ApiError.class)
    })
    public void watchEvents(RoutingContext routing) {
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

    @DocPath(method = PUT,
            path = "/delete",
            name = "Delete events with optional filtering.",
            notes = "Return number of events deleted. + \n" +
                    "WARNING: If not criteria defined, it deletes all events stored in the system. + \n" +
                    "Tags Query language (BNF): + \n" +
                    "[source] \n" +
                    "---- \n" +
                    "<tag_query> ::= ( <expression> | \"(\" <object> \")\" " +
                    "| <object> <logical_operator> <object> ) \n" +
                    "<expression> ::= ( <tag_name> | <not> <tag_name> " +
                    "| <tag_name> <boolean_operator> <tag_value> | " +
                    "<tag_name> <array_operator> <array> ) \n" +
                    "<not> ::= [ \"NOT\" | \"not\" ] \n" +
                    "<logical_operator> ::= [ \"AND\" | \"OR\" | \"and\" | \"or\" ] \n" +
                    "<boolean_operator> ::= [ \"==\" | \"!=\" ] \n" +
                    "<array_operator> ::= [ \"IN\" | \"NOT IN\" | \"in\" | \"not in\" ] \n" +
                    "<array> ::= ( \"[\" \"]\" | \"[\" ( \",\" <tag_value> )* ) \n" +
                    "<tag_name> ::= <identifier> \n" +
                    "<tag_value> ::= ( \"'\" <regexp> \"'\" | <simple_value> ) \n" +
                    "; \n" +
                    "; <identifier> and <simple_value> follow pattern [a-zA-Z_0-9][\\-a-zA-Z_0-9]* \n" +
                    "; <regexp> follows any valid Java Regular Expression format \n" +
                    "---- \n")
    @DocParameters(value = {
            @DocParameter(name = "startTime",
                    description = "Filter out events created before this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "endTime",
                    description = "Filter out events created after this time.",
                    allowableValues = "Timestamp in millisecond since epoch."),
            @DocParameter(name = "eventIds",
                    description = "Filter out events for unspecified eventIds.",
                    allowableValues = "Comma separated list of event IDs."),
            @DocParameter(name = "triggerIds",
                    description = "Filter out events for unspecified triggers.",
                    allowableValues = "Comma separated list of trigger IDs."),
            @DocParameter(name = "categories",
                    description = "Filter out events for unspecified categories.",
                    allowableValues = "Comma separated list of category values."),
            @DocParameter(name = "tags",
                    description = "[DEPRECATED] Filter out events for unspecified tags.",
                    allowableValues = "Comma separated list of tags, each tag of format 'name\\|description'. + \n" +
                            "Specify '*' for description to match all values."),
            @DocParameter(name = "tagQuery",
                    description = "Filter out events for unspecified tags.",
                    allowableValues = "A tag query expression.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success. Number of events deleted.", response = ApiDeleted.class),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void deleteEvents(RoutingContext routing) {
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

    @DocPath(method = DELETE,
            path = "/{eventId}",
            name = "Delete an existing Event.")
    @DocParameters(value = {
            @DocParameter(name = "eventId", required = true, path = true,
                    description = "Event id to be deleted.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Event deleted."),
            @DocResponse(code = 400, message = "Bad Request/Invalid Parameters.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void deleteEvent(RoutingContext routing) {
        deleteEvents(routing);
    }

    @DocPath(method = GET,
            path = "/event/{eventId}",
            name = "Get an existing Event.")
    @DocParameters(value = {
            @DocParameter(name = "eventId", required = true, path = true,
                    description = "Event id to be deleted."),
            @DocParameter(name = "thin",
                    description = "Return only a thin event, do not include: evalSets, dampening.")
    })
    @DocResponses(value = {
            @DocResponse(code = 200, message = "Success, Event found.", response = Event.class),
            @DocResponse(code = 404, message = "Event not found.", response = ApiError.class),
            @DocResponse(code = 500, message = "Internal server error.", response = ApiError.class)
    })
    public void getEvent(RoutingContext routing) {
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
