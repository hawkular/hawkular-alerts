/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
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
package org.hawkular.alerts.rest;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

import static org.hawkular.alerts.rest.HawkularAlertsApp.TENANT_HEADER_NAME;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.ejb.EJB;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.jboss.logging.Logger;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * REST endpoint for events
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/events")
@Api(value = "/events", description = "Event Handling")
public class EventsHandler {
    private final Logger log = Logger.getLogger(EventsHandler.class);

    @HeaderParam(TENANT_HEADER_NAME)
    String tenantId;

    @EJB
    AlertsService alertsService;

    public EventsHandler() {
        log.debugf("Creating instance.");
    }

    @POST
    @Path("/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Create a new Event.",
            response = Event.class,
            notes = "Returns created Event")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Event Created"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public Response createEvent(
            @ApiParam(value = "Event to be created. Category and Text fields required",
                    name = "event", required = true)
            final Event event) {
        try {
            if (null != event) {
                event.setTenantId(tenantId);
                if (null != alertsService.getEvent(tenantId, event.getId(), true)) {
                    return ResponseUtil.badRequest("Event with ID [" + event.getId() + "] exists.");
                }
                alertsService.addEvents(Collections.singletonList(event));
                log.debugf("Event: %s ", event.toString());
                return ResponseUtil.ok(event);
            } else {
                return ResponseUtil.badRequest("Event is null");
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Get events with optional filtering")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response findEvents(
            @ApiParam(required = false, value = "filter out events created before this time, millisecond since epoch")
            @QueryParam("startTime")
            final Long startTime,
            @ApiParam(required = false, value = "filter out events created after this time, millisecond since epoch")
            @QueryParam("endTime")
            final Long endTime,
            @ApiParam(required = false, value = "filter out events for unspecified eventIds, " +
                    "comma separated list of event IDs")
            @QueryParam("eventIds") final String eventIds,
            @ApiParam(required = false, value = "filter out events for unspecified triggers, " +
                    "comma separated list of trigger IDs")
            @QueryParam("triggerIds")
            final String triggerIds,
            @ApiParam(required = false, value = "filter out events for unspecified categories, " +
                    "comma separated list of category values")
            @QueryParam("categories")
            final String categories,
            @ApiParam(required = false, value = "filter out events for unspecified tags, comma separated list of tags, "
                    + "each tag of format 'name|value'. Specify '*' for value to match all values.")
            @QueryParam("tags")
            final String tags,
            @ApiParam(required = false, value = "return only thin events, do not include: evalSets")
            @QueryParam("thin")
            final Boolean thin,
            @Context
            final UriInfo uri) {
        Pager pager = RequestUtil.extractPaging(uri);
        try {
            EventsCriteria criteria = buildCriteria(startTime, endTime, eventIds, triggerIds, categories, tags, thin);
            Page<Event> eventPage = alertsService.getEvents(tenantId, criteria, pager);
            log.debugf("Events: %s ", eventPage);
            if (isEmpty(eventPage)) {
                return ResponseUtil.ok(eventPage);
            }
            return ResponseUtil.paginatedOk(eventPage, uri);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }


    @DELETE
    @Path("/{eventId}")
    @ApiOperation(value = "Delete an existing Event")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Event deleted"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 404, message = "Event not found") })
    public Response deleteEvent(
            @ApiParam(required = true, value = "Event id to be deleted")
            @PathParam("eventId")
            final String eventId) {
        try {
            EventsCriteria criteria = new EventsCriteria();
            criteria.setEventId(eventId);
            int numDeleted = alertsService.deleteEvents(tenantId, criteria);
            if (1 == numDeleted) {
                log.debugf("EventId: %s ", eventId);
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.notFound("Event " + eventId + " doesn't exist for delete");
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @PUT
    @Path("/delete")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Delete events with optional filtering")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response deleteEvents(
            @ApiParam(required = false, value = "filter out events created before this time, millisecond since epoch")
            @QueryParam("startTime")
            final Long startTime,
            @ApiParam(required = false, value = "filter out events created after this time, millisecond since epoch")
            @QueryParam("endTime")
            final Long endTime,
            @ApiParam(required = false, value = "filter out events for unspecified eventIds, " +
                    "comma separated list of event IDs") @QueryParam("eventIds") final String eventIds,
            @ApiParam(required = false, value = "filter out events for unspecified triggers, " +
                    "comma separated list of trigger IDs")
            @QueryParam("triggerIds")
            final String triggerIds,
            @ApiParam(required = false, value = "filter out events for unspecified categories, " +
                    "comma separated list of category values") @QueryParam("categories") final String categories,
            @ApiParam(required = false, value = "filter out events for unspecified tags, comma separated list of tags, "
                    + "each tag of format 'name|value'. Specify '*' for value to match all values.")
            @QueryParam("tags")
            final String tags
            ) {
        try {
            EventsCriteria criteria = buildCriteria(startTime, endTime, eventIds, triggerIds, categories, tags, null);
            int numDeleted = alertsService.deleteEvents(tenantId, criteria);
            log.debugf("Events deleted: %s ", numDeleted);
            return ResponseUtil.ok(numDeleted);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    private EventsCriteria buildCriteria(Long startTime, Long endTime, String eventIds, String triggerIds,
            String categories, String tags, Boolean thin) {
        EventsCriteria criteria = new EventsCriteria();
        criteria.setStartTime(startTime);
        criteria.setEndTime(endTime);
        if (!isEmpty(eventIds)) {
            criteria.setEventIds(Arrays.asList(eventIds.split(",")));
        }
        if (!isEmpty(triggerIds)) {
            criteria.setTriggerIds(Arrays.asList(triggerIds.split(",")));
        }
        if (!isEmpty(categories)) {
            criteria.setCategories(Arrays.asList(categories.split(",")));
        }
        if (!isEmpty(tags)) {
            String[] tagTokens = tags.split(",");
            Map<String, String> tagsMap = new HashMap<>(tagTokens.length);
            for (String tagToken : tagTokens) {
                String[] fields = tagToken.split("\\|");
                if (fields.length == 2) {
                    tagsMap.put(fields[0], fields[1]);
                } else {
                    log.debugf("Invalid Tag Criteria %s", Arrays.toString(fields));
                }
            }
            criteria.setTags(tagsMap);
        }
        if (null != thin) {
            criteria.setThin(thin.booleanValue());
        }

        return criteria;
    }

    @GET
    @Path("/event/{eventId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get an existing Event",
            response = Event.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Event found"),
            @ApiResponse(code = 404, message = "Event not found"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response getEvent(
            @ApiParam(value = "Id of Event to be retrieved", required = true)
            @PathParam("eventId")
            final String eventId,
            @ApiParam(required = false, value = "return only a thin event, do not include: evalSets, dampening")
            @QueryParam("thin")
            final Boolean thin) {
        try {
            Event found = alertsService.getEvent(tenantId, eventId, ((null == thin) ? false : thin.booleanValue()));
            if (found != null) {
                log.debugf("Event: %s ", found);
                return ResponseUtil.ok(found);
            } else {
                return ResponseUtil.notFound("eventId: " + eventId + " not found");
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean isEmpty(Collection collection) {
        return collection == null || collection.isEmpty();
    }
}
