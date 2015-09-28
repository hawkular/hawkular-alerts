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

import java.util.ArrayList;
import java.util.Collection;
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
import javax.ws.rs.core.Response;

import org.hawkular.alerts.api.exception.NotFoundException;
import org.hawkular.alerts.api.json.GroupMemberInfo;
import org.hawkular.alerts.api.json.JacksonDeserializer;
import org.hawkular.alerts.api.json.UnorphanMemberInfo;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * REST endpoint for triggers
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Path("/triggers")
@Api(value = "/triggers",
        description = "Trigger Handling")
public class TriggersHandler {
    private static final Logger log = Logger.getLogger(TriggersHandler.class);

    @HeaderParam(TENANT_HEADER_NAME)
    String tenantId;

    @EJB
    DefinitionsService definitions;

    ObjectMapper objectMapper;

    public TriggersHandler() {
        log.debugf("Creating instance.");
        objectMapper = new ObjectMapper();
    }

    @GET
    @Path("/")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Find all Trigger definitions",
            notes = "Pagination is not yet implemented")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response findTriggers() {
        try {
            Collection<Trigger> triggers = definitions.getTriggers(tenantId);
            log.debugf("Triggers: %s ", triggers);
            return ResponseUtil.ok(triggers);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/tag")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Find all Trigger definitions for the specified tag. At least one of category and name is required",
            notes = "Pagination is not yet implemented")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Triggers list found. Can be empty."),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response findTriggersByTag(
            @ApiParam(required = true, value = "The tag name.")
            @QueryParam("name")
            final String name,
            @ApiParam(required = true, value = "The tag value. Set to '*' to match all values for the tag name.")
            @QueryParam("value")
            final String value) {
        try {
            Collection<Trigger> triggers = definitions.getTriggersByTag(tenantId, name, value);
            log.debugf("Triggers: %s ", triggers);
            return ResponseUtil.ok(triggers);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/groups/{groupId}/members")
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Find all Group Member Trigger Definitions",
            notes = "Pagination is not yet implemented")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response findGroupMembers(
            @ApiParam(value = "Group TriggerId", required = true)//
            @PathParam("groupId")//
            final String groupId,
            @ApiParam(value = "include Orphan members? No if omitted.", required = false)//
            @QueryParam("includeOrphans")//
            final boolean includeOrphans) {
        try {
            Collection<Trigger> members = definitions.getMemberTriggers(tenantId, groupId, includeOrphans);
            log.debugf("Member Triggers: %s ", members);
            return ResponseUtil.ok(members);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @POST
    @Path("/")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Create a new trigger",
            response = Trigger.class,
            notes = "Returns created Trigger")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Trigger Created"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public Response createTrigger(
            @ApiParam(value = "Trigger definition to be created", name = "trigger", required = true)
            final Trigger trigger) {
        try {
            if (null != trigger) {
                if (isEmpty(trigger.getId())) {
                    trigger.setId(Trigger.generateId());
                } else if (definitions.getTrigger(tenantId, trigger.getId()) != null) {
                    return ResponseUtil.badRequest("Trigger with ID [" + trigger.getId() + "] exists.");
                }
                definitions.addTrigger(tenantId, trigger);
                log.debugf("Trigger: %s ", trigger.toString());
                return ResponseUtil.ok(trigger);
            } else {
                return ResponseUtil.badRequest("Trigger is null");
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @POST
    @Path("/groups")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Create a new group trigger",
            response = Trigger.class,
            notes = "Returns created GroupTrigger")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Group Trigger Created"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public Response createGroupTrigger(
            @ApiParam(value = "Trigger definition to be created", name = "groupTrigger", required = true)
            final Trigger groupTrigger) {
        try {
            if (null != groupTrigger) {
                if (isEmpty(groupTrigger.getId())) {
                    groupTrigger.setId(Trigger.generateId());
                } else if (definitions.getTrigger(tenantId, groupTrigger.getId()) != null) {
                    return ResponseUtil.badRequest("Trigger with ID [" + groupTrigger.getId() + "] exists.");
                }
                definitions.addGroupTrigger(tenantId, groupTrigger);
                log.debugf("Group Trigger: %s ", groupTrigger.toString());
                return ResponseUtil.ok(groupTrigger);
            } else {
                return ResponseUtil.badRequest("Trigger is null");
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @POST
    @Path("/groups/members")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Create a new member trigger for a parent trigger.",
            response = Trigger.class,
            notes = "Returns Member Trigger created if operation finished correctly")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Member Trigger Created"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 404, message = "Group trigger not found."),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public Response createGroupMember(
            @ApiParam(value = "Group member trigger to be created", name = "groupMember", required = true)//
            final GroupMemberInfo groupMember) {
        try {
            if (null == groupMember) {
                return ResponseUtil.badRequest("MemberTrigger is null");
            }
            String groupId = groupMember.getGroupId();
            String memberName = groupMember.getMemberName();
            if (isEmpty(groupId)) {
                return ResponseUtil.badRequest("MemberTrigger groupId is null");
            }
            if (isEmpty(memberName)) {
                return ResponseUtil.badRequest("MemberTrigger memberName is null");
            }
            Trigger child = definitions.addMemberTrigger(tenantId, groupId, groupMember.getMemberId(), memberName,
                    groupMember.getMemberContext(),
                    groupMember.getDataIdMap());
            log.debugf("Child Trigger: %s ", child.toString());
            return ResponseUtil.ok(child);

        } catch (NotFoundException e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.notFound(e.getMessage());
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/{triggerId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get an existing trigger definition",
            response = Trigger.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Trigger found"),
            @ApiResponse(code = 404, message = "Trigger not found"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response getTrigger(
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)
            @PathParam("triggerId")
            final String triggerId) {
        try {
            Trigger found = definitions.getTrigger(tenantId, triggerId);
            if (found != null) {
                log.debugf("Trigger: %s ", found);
                return ResponseUtil.ok(found);
            } else {
                return ResponseUtil.notFound("triggerId: " + triggerId + " not found");
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @PUT
    @Path("/{triggerId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Update an existing trigger definition")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Trigger updated"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 404, message = "Trigger doesn't exist/Invalid Parameters") })
    public Response updateTrigger(
            @ApiParam(value = "Trigger definition id to be updated", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Updated trigger definition", name = "trigger", required = true)
            final Trigger trigger) {
        try {
            if (trigger != null && !isEmpty(triggerId)) {
                trigger.setId(triggerId);
            }
            definitions.updateTrigger(tenantId, trigger);
            log.debugf("Trigger: %s ", trigger);
            return ResponseUtil.ok();

        } catch (NotFoundException e) {
            return ResponseUtil.notFound("Trigger " + triggerId + " doesn't exist for update");

        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @PUT
    @Path("/groups/{groupId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Update an existing group trigger definition and its member definitions")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Group Trigger updated"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 404, message = "Trigger doesn't exist/Invalid Parameters") })
    public Response updateGroupTrigger(
            @ApiParam(value = "Group Trigger id to be updated", required = true)
            @PathParam("groupId")
            final String groupId,
            @ApiParam(value = "Updated group trigger definition", name = "trigger", required = true)
            final Trigger groupTrigger) {
        try {
            if (groupTrigger != null && !isEmpty(groupId)) {
                groupTrigger.setId(groupId);
            }
            definitions.updateGroupTrigger(tenantId, groupTrigger);
            log.debugf("Trigger: %s ", groupTrigger);
            return ResponseUtil.ok();

        } catch (NotFoundException e) {
            return ResponseUtil.notFound("Trigger " + groupId + " doesn't exist for update");

        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }


    @POST
    @Path("/groups/members/{memberId}/orphan")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Make a non-orphan member trigger into an orphan.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Trigger updated"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 404, message = "Trigger doesn't exist/Invalid Parameters") })
    public Response orphanMemberTrigger(
            @ApiParam(value = "Member Trigger id to be made an orphan.", required = true)//
            @PathParam("memberId")//
            final String memberId) {
        try {
            Trigger child = definitions.orphanMemberTrigger(tenantId, memberId);
            log.debugf("Orphan Member Trigger: %s ", child);
            return ResponseUtil.ok();

        } catch (NotFoundException e) {
            return ResponseUtil.notFound("Member Trigger " + memberId + " doesn't exist for update");

        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @POST
    @Path("/groups/members/{memberId}/unorphan")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Make a non-orphan member trigger into an orphan.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Trigger updated"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 404, message = "Trigger doesn't exist/Invalid Parameters") })
    public Response unorphanMemberTrigger(
            @ApiParam(value = "Member Trigger id to be made an orphan.", required = true)//
            @PathParam("memberId")//
            final String memberId,
            @ApiParam(required = true, name = "memberTrigger",
                    value = "Only context and dataIdMap are used when changing back to a non-orphan.")//
            final UnorphanMemberInfo unorphanMemberInfo) {
        try {
            if (null == unorphanMemberInfo) {
                return ResponseUtil.badRequest("MemberTrigger is null");
            }
            Trigger child = definitions.unorphanMemberTrigger(tenantId, memberId, unorphanMemberInfo.getMemberContext(),
                    unorphanMemberInfo.getDataIdMap());
            log.debugf("Member Trigger: %s ", child);
            return ResponseUtil.ok();

        } catch (NotFoundException e) {
            return ResponseUtil.notFound("Member Trigger " + memberId + " doesn't exist for update");

        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @DELETE
    @Path("/{triggerId}")
    @ApiOperation(value = "Delete an existing trigger definition")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Trigger deleted"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 404, message = "Trigger not found") })
    public Response deleteTrigger(
            @ApiParam(value = "Trigger definition id to be deleted", required = true) @PathParam("triggerId")//
            final String triggerId) {
        try {
            definitions.removeTrigger(tenantId, triggerId);
            log.debugf("TriggerId: %s ", triggerId);
            return ResponseUtil.ok();

        } catch (NotFoundException e) {
            return ResponseUtil.notFound("Trigger " + triggerId + " doesn't exist for delete");

        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @DELETE
    @Path("/groups/{groupId}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(
            value = "Delete a group trigger.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Group Trigger Removed"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 404, message = "Group Trigger not found"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public Response deleteGroupTrigger(
            @ApiParam(required = true, value = "Group Trigger id")//
            @PathParam("groupId")//
            final String groupId,
            @ApiParam(required = true, value = "Convert the non-orphan member triggers to standard triggers.")//
            @QueryParam("keepNonOrphans")//
            final boolean keepNonOrphans,
            @ApiParam(required = true, value = "Convert the orphan member triggers to standard triggers.")//
            @QueryParam("keepOrphans")//
            final boolean keepOrphans) {
        try {
            definitions.removeGroupTrigger(tenantId, groupId, keepNonOrphans, keepOrphans);
            log.debugf("Remove Group Trigger: %s/%s ", tenantId, groupId);
            return ResponseUtil.ok();

        } catch (NotFoundException e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.notFound(e.getMessage());
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }


    @GET
    @Path("/{triggerId}/dampenings")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get all Dampenings for a Trigger (1 Dampening per mode).")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error")})
    public Response getTriggerDampenings(
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)
            @PathParam("triggerId")
            final String triggerId) {
        try {
            Collection<Dampening> dampenings = definitions.getTriggerDampenings(tenantId, triggerId, null);
            log.debugf("Dampenings: %s ", dampenings);
            return ResponseUtil.ok(dampenings);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/{triggerId}/dampenings/mode/{triggerMode}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get a dampening using triggerId and triggerMode")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response getTriggerModeDampenings(
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)//
            @PathParam("triggerId")//
            final String triggerId,//
            @ApiParam(value = "Trigger mode", required = true)//
            @PathParam("triggerMode")//
            final Mode triggerMode) {
        try {
            Collection<Dampening> dampenings = definitions.getTriggerDampenings(tenantId, triggerId,
                    triggerMode);
            log.debugf("Dampenings: %s ", dampenings);
            return ResponseUtil.ok(dampenings);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/{triggerId}/dampenings/{dampeningId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get an existing dampening")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Dampening Found"),
            @ApiResponse(code = 404, message = "No Dampening Found"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response getDampening(
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Dampening id", required = true)
            @PathParam("dampeningId")
            final String dampeningId) {
        try {
            Dampening found = definitions.getDampening(tenantId, dampeningId);
            log.debugf("Dampening: %s ", found);
            if (found == null) {
                return ResponseUtil.notFound("No dampening found for triggerId: " + triggerId + " and dampeningId:" +
                        dampeningId);
            }
            return ResponseUtil.ok(found);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @POST
    @Path("/{triggerId}/dampenings")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a new dampening", notes = "Returns Dampening created if operation finishes correctly")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Dampening created"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public Response createDampening(
            @ApiParam(value = "Trigger definition id attached to dampening", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Dampening definition to be created", required = true)
            final Dampening dampening) {
        try {
            dampening.setTriggerId(triggerId);
            boolean exists = (definitions.getDampening(tenantId, dampening.getDampeningId()) != null);
            if (!exists) {
                // make sure we have the best chance of clean data..
                Dampening d = getCleanDampening(dampening);
                definitions.addDampening(tenantId, d);
                log.debugf("Dampening: %s ", d);
                return ResponseUtil.ok(d);
            } else {
                return ResponseUtil.badRequest("Existing dampening for dampeningId: " + dampening.getDampeningId());
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @POST
    @Path("/groups/{groupId}/dampenings")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Create a new group dampening",
            notes = "Returns Dampening created if operation finishes correctly")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Dampening created"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public Response createGroupDampening(
            @ApiParam(value = "Group Trigger definition id attached to dampening", required = true)//
            @PathParam("groupId")//
            final String groupId,
            @ApiParam(value = "Dampening definition to be created", required = true)//
            final Dampening dampening) {
        try {
            dampening.setTriggerId(groupId);
            boolean exists = (definitions.getDampening(tenantId, dampening.getDampeningId()) != null);
            if (!exists) {
                // make sure we have the best chance of clean data..
                Dampening d = getCleanDampening(dampening);
                definitions.addGroupDampening(tenantId, d);
                log.debugf("Dampening: %s ", d);
                return ResponseUtil.ok(d);
            } else {
                return ResponseUtil.badRequest("Existing dampening for dampeningId: " + dampening.getDampeningId());
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    private Dampening getCleanDampening(Dampening dampening) throws Exception {
        switch (dampening.getType()) {
            case STRICT:
                return Dampening.forStrict(dampening.getTriggerId(), dampening.getTriggerMode(),
                        dampening.getEvalTrueSetting());

            case STRICT_TIME:
                return Dampening.forStrictTime(dampening.getTriggerId(), dampening.getTriggerMode(),
                        dampening.getEvalTimeSetting());

            case STRICT_TIMEOUT:
                return Dampening.forStrictTimeout(dampening.getTriggerId(), dampening.getTriggerMode(),
                        dampening.getEvalTimeSetting());
            case RELAXED_COUNT:
                return Dampening.forRelaxedCount(dampening.getTriggerId(), dampening.getTriggerMode(),
                        dampening.getEvalTrueSetting(),
                        dampening.getEvalTotalSetting());
            case RELAXED_TIME:
                return Dampening.forRelaxedTime(dampening.getTriggerId(), dampening.getTriggerMode(),
                        dampening.getEvalTrueSetting(), dampening.getEvalTimeSetting());

            default:
                throw new Exception("Unhandled Dampening Type: " + dampening.toString());
        }
    }

    @PUT
    @Path("/{triggerId}/dampenings/{dampeningId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Update an existing dampening definition. Note that the trigger mode can not be changed.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Dampening Updated"),
            @ApiResponse(code = 404, message = "No Dampening Found"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response updateDampening(
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Dampening id", required = true)
            @PathParam("dampeningId")
            final String dampeningId,
            @ApiParam(value = "Updated dampening definition", required = true)
            final Dampening dampening) {
        try {
            boolean exists = (definitions.getDampening(tenantId, dampeningId) != null);
            if (exists) {
                // make sure we have the best chance of clean data..
                Dampening d = getCleanDampening(dampening);
                definitions.updateDampening(tenantId, d);
                log.debugf("Dampening: %s ", d);
                return ResponseUtil.ok(d);
            } else {
                return ResponseUtil.notFound("No dampening found for dampeningId: " + dampeningId);
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @PUT
    @Path("/groups/{groupId}/dampenings/{dampeningId}")
    @Consumes(APPLICATION_JSON)
    @ApiOperation(value = "Update an existing group dampening definition. Note that trigger mode can not be changed.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Dampening Updated"),
            @ApiResponse(code = 404, message = "No Dampening Found"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response updateGroupDampening(
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)
            @PathParam("groupId")
            final String groupId,
            @ApiParam(value = "Dampening id", required = true)
            @PathParam("dampeningId")
            final String dampeningId,
            @ApiParam(value = "Updated dampening definition", required = true)
            final Dampening dampening) {
        try {
            boolean exists = (definitions.getDampening(tenantId, dampeningId) != null);
            if (exists) {
                // make sure we have the best chance of clean data..
                Dampening d = getCleanDampening(dampening);
                definitions.updateGroupDampening(tenantId, d);
                log.debugf("Group Dampening: %s ", d);
                return ResponseUtil.ok(d);
            } else {
                return ResponseUtil.notFound("No dampening found for dampeningId: " + dampeningId);
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @DELETE
    @Path("/{triggerId}/dampenings/{dampeningId}")
    @ApiOperation(value = "Delete an existing dampening definition")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Dampening deleted"),
            @ApiResponse(code = 404, message = "No Dampening found"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response deleteDampening(
            @ApiParam(value = "Trigger definition id to be deleted", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Dampening id for dampening definition to be deleted", required = true)
            @PathParam("dampeningId")
            final String dampeningId) {
        try {
            boolean exists = (definitions.getDampening(tenantId, dampeningId) != null);
            if (exists) {
                definitions.removeDampening(tenantId, dampeningId);
                log.debugf("DampeningId: %s ", dampeningId);
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.notFound("Dampening not found for dampeningId: " + dampeningId);
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @DELETE
    @Path("/groups/{groupId}/dampenings/{dampeningId}")
    @ApiOperation(value = "Delete an existing group dampening definition")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Dampening deleted"),
            @ApiResponse(code = 404, message = "No Dampening found"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response deleteGroupDampening(
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)
            @PathParam("groupId")
            final String groupId,
            @ApiParam(value = "Dampening id for dampening definition to be deleted", required = true)
            @PathParam("dampeningId")
            final String dampeningId) {
        try {
            boolean exists = (definitions.getDampening(tenantId, dampeningId) != null);
            if (exists) {
                definitions.removeGroupDampening(tenantId, dampeningId);
                log.debugf("Group DampeningId: %s ", dampeningId);
                return ResponseUtil.ok();
            } else {
                return ResponseUtil.notFound("Dampening not found for dampeningId: " + dampeningId);
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/{triggerId}/conditions")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Get all conditions for a specific trigger.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success"),
            @ApiResponse(code = 500, message = "Internal server error") })
    public Response getTriggerConditions(
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)
            @PathParam("triggerId")
            final String triggerId) {
        try {
            Collection<Condition> conditions = definitions.getTriggerConditions(tenantId, triggerId, null);
            log.debugf("Conditions: %s ", conditions);
            return ResponseUtil.ok(conditions);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @GET
    @Path("/{triggerId}/conditions/{conditionId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "@Deprecated : Use GET /alerts/triggers/{triggerId}/conditions")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Condition found"),
            @ApiResponse(code = 404, message = "No Condition found"),
            @ApiResponse(code = 500, message = "Internal server error") })
    @Deprecated
    public Response getTriggerCondition(
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @PathParam("conditionId")
            final String conditionId) {
        try {
            Trigger trigger = definitions.getTrigger(tenantId, triggerId);
            if (trigger == null) {
                return ResponseUtil.notFound("No trigger found for triggerId: " + triggerId);
            }
            Condition found = definitions.getCondition(tenantId, conditionId);
            if (found == null) {
                return ResponseUtil.notFound("No condition found for conditionId: " + conditionId);
            }
            if (!found.getTriggerId().equals(triggerId)) {
                return ResponseUtil.notFound("ConditionId: " + conditionId + " does not belong to triggerId: " +
                        triggerId);
            }
            return ResponseUtil.ok(found);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @PUT
    @Path("/{triggerId}/conditions/{triggerMode}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Set the conditions for the trigger. This replaces any existing conditions. Returns "
            + "the new conditions.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Condition Set created"),
            @ApiResponse(code = 404, message = "No trigger found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public Response setConditions(
            @ApiParam(value = "The relevant Trigger.", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "FIRING or AUTORESOLVE (not case sensitive).", required = true)
            @PathParam("triggerMode")
            final String triggerMode,
            @ApiParam(value = "Json representation of a condition list. For examples of Condition types, See "
                    + "https://github.com/hawkular/hawkular-alerts/blob/master/hawkular-alerts-rest-tests/"
                    + "src/test/groovy/org/hawkular/alerts/rest/ConditionsITest.groovy")//
            String jsonConditions) {
        try {
            Mode mode = Mode.valueOf(triggerMode.toUpperCase());
            Collection<Condition> conditions = new ArrayList<>();
            if (!isEmpty(jsonConditions)) {

                ObjectMapper om = new ObjectMapper();
                JsonNode rootNode = om.readTree(jsonConditions);
                for (JsonNode conditionNode : rootNode) {
                    Condition condition = JacksonDeserializer.deserializeCondition(conditionNode);
                    if (condition == null) {
                        return ResponseUtil.badRequest("Bad json conditions: " + jsonConditions);
                    }
                    condition.setTriggerId(triggerId);
                    condition.setTriggerMode(mode);
                    conditions.add(condition);
                }
            }

            conditions = definitions.setConditions(tenantId, triggerId, mode, conditions);
            log.debugf("Conditions: %s ", conditions);
            return ResponseUtil.ok(conditions);

        } catch (IllegalArgumentException e) {
            return ResponseUtil.badRequest("Bad argument: " + e.getMessage());
        } catch (NotFoundException e) {
            return ResponseUtil.notFound(e.getMessage());
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @PUT
    @Path("/groups/{groupId}/conditions/{triggerMode}")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Set the conditions for the group trigger. This replaces any existing conditions on "
            + "the group and member conditions.  Returns the new group conditions.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Group Condition Set created"),
            @ApiResponse(code = 404, message = "No trigger found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    public Response setGroupConditions(
            @ApiParam(value = "The relevant Group Trigger.", required = true)
            @PathParam("groupId")
            final String groupId,
            @ApiParam(value = "FIRING or AUTORESOLVE (not case sensitive).", required = true)
            @PathParam("triggerMode")
            final String triggerMode,
            @ApiParam(value = "Json representation of GroupConditionsInfo. For examples of Condition types, See "
                    + "https://github.com/hawkular/hawkular-alerts/blob/master/hawkular-alerts-rest-tests/"
                    + "src/test/groovy/org/hawkular/alerts/rest/ConditionsITest.groovy")//
            String jsonGroupConditionsInfo) {
        try {
            if (isEmpty(jsonGroupConditionsInfo)) {
                return ResponseUtil.badRequest("GroupConditionsInfo can not be null");
            }

            Mode mode = Mode.valueOf(triggerMode.toUpperCase());
            Collection<Condition> conditions = new ArrayList<>();

            ObjectMapper om = new ObjectMapper();
            JsonNode rootNode = om.readTree(jsonGroupConditionsInfo);
            JsonNode conditionsNode = rootNode.get("conditions");
            for (JsonNode conditionNode : conditionsNode) {
                Condition condition = JacksonDeserializer.deserializeCondition(conditionNode);
                if (condition == null) {
                    return ResponseUtil.badRequest("Bad json conditions: " + conditionsNode.toString());
                }
                condition.setTriggerId(groupId);
                condition.setTriggerMode(mode);
                conditions.add(condition);
            }

            JsonNode dataIdMemberMapNode = rootNode.get("dataIdMemberMap");
            Map<String, Map<String, String>> dataIdMemberMap = null;
            if (null != dataIdMemberMapNode) {
                dataIdMemberMap = om.treeToValue(dataIdMemberMapNode, Map.class);
            }

            conditions = definitions.setGroupConditions(tenantId, groupId, mode, conditions, dataIdMemberMap);

            log.debugf("Conditions: %s ", conditions);
            return ResponseUtil.ok(conditions);

        } catch (IllegalArgumentException e) {
            return ResponseUtil.badRequest("Bad trigger mode: " + triggerMode);
        } catch (NotFoundException e) {
            return ResponseUtil.notFound(e.getMessage());
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @POST
    @Path("/{triggerId}/conditions")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Deprecated : Use PUT /alerts/triggers/{triggerId}/conditions to set the entire "
            + "condition set in one service.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Condition created"),
            @ApiResponse(code = 404, message = "No trigger found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    @Deprecated
    public Response createCondition(
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @ApiParam(value = "Json representation of a condition. For examples of Condition types, See "
                    + "https://github.com/hawkular/hawkular-alerts/blob/master/hawkular-alerts-rest-tests/"
                    + "src/test/groovy/org/hawkular/alerts/rest/ConditionsITest.groovy")//
            String jsonCondition) {
        try {
            if (isEmpty(jsonCondition) || !jsonCondition.contains("type")) {
                return ResponseUtil.badRequest("json condition empty or without type");
            }

            ObjectMapper om = new ObjectMapper();
            JsonNode rootNode = om.readTree(jsonCondition);
            Condition condition = JacksonDeserializer.deserializeCondition(rootNode);

            if (condition == null) {
                return ResponseUtil.badRequest("Bad json condition");
            }
            condition.setTriggerId(triggerId);

            Collection<Condition> conditions;
            conditions = definitions.addCondition(tenantId,
                        condition.getTriggerId(),
                        condition.getTriggerMode(),
                        condition);
            log.debugf("Conditions: %s ", conditions);
            return ResponseUtil.ok(conditions);

        } catch (NotFoundException e) {
            return ResponseUtil.notFound(e.getMessage());
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @PUT
    @Path("/{triggerId}/conditions/{conditionId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Deprecated : Use PUT /alerts/triggers/{triggerId}/conditions to set the entire "
            + "condition set in one service.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Condition updated"),
            @ApiResponse(code = 404, message = "No Condition found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    @Deprecated
    public Response updateCondition(
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @PathParam("conditionId")
            final String conditionId,
            @ApiParam(value = "Json representation of a condition")
            String jsonCondition) {
        try {
            Trigger trigger = definitions.getTrigger(tenantId, triggerId);
            if (trigger == null) {
                return ResponseUtil.notFound("No trigger found for triggerId: " + triggerId);
            }
            if (isEmpty(jsonCondition) || !jsonCondition.contains("type")) {
                return ResponseUtil.badRequest("json condition empty or without type");
            }

            ObjectMapper om = new ObjectMapper();
            JsonNode rootNode = om.readTree(jsonCondition);
            Condition condition = JacksonDeserializer.deserializeCondition(rootNode);
            if (condition == null) {
                return ResponseUtil.badRequest("Bad json condition");
            }
            condition.setTriggerId(triggerId);
            boolean exists = false;
            if (conditionId.equals(condition.getConditionId())) {
                exists = (definitions.getCondition(tenantId, condition.getConditionId()) != null);
            }
            if (!exists) {
                return ResponseUtil.notFound("Condition not found for conditionId: " + conditionId);
            } else {
                Collection<Condition> conditions = definitions.updateCondition(tenantId, condition);
                log.debugf("Conditions: %s ", conditions);
                return ResponseUtil.ok(conditions);
            }
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    @DELETE
    @Path("/{triggerId}/conditions/{conditionId}")
    @Produces(APPLICATION_JSON)
    @ApiOperation(value = "Deprecated : Use PUT /alerts/triggers/{triggerId}/conditions to set the entire "
            + "condition set in one service.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Success, Condition deleted"),
            @ApiResponse(code = 404, message = "No Condition found"),
            @ApiResponse(code = 500, message = "Internal server error"),
            @ApiResponse(code = 400, message = "Bad Request/Invalid Parameters") })
    @Deprecated
    public Response deleteCondition(
            @ApiParam(value = "Trigger definition id to be retrieved", required = true)
            @PathParam("triggerId")
            final String triggerId,
            @PathParam("conditionId")
            final String conditionId) {
        try {
            Trigger trigger = definitions.getTrigger(tenantId, triggerId);
            if (trigger == null) {
                return ResponseUtil.notFound("No trigger found for triggerId: " + triggerId);
            }
            Condition condition = definitions.getCondition(tenantId, conditionId);
            if (condition == null) {
                return ResponseUtil.notFound("No condition found for conditionId: " + conditionId);
            }
            if (!condition.getTriggerId().equals(triggerId)) {
                return ResponseUtil.badRequest("ConditionId: " + conditionId + " does not belong to triggerId: " +
                        triggerId);
            }
            Collection<Condition> conditions = definitions.removeCondition(tenantId, conditionId);
            log.debugf("Conditions: %s ", conditions);
            return ResponseUtil.ok(conditions);
        } catch (Exception e) {
            log.debugf(e.getMessage(), e);
            return ResponseUtil.internalError(e.getMessage());
        }
    }

    private boolean isEmpty(String s) {
        return null == s || s.trim().isEmpty();
    }

}
