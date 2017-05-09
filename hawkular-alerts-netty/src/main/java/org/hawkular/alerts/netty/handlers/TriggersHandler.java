package org.hawkular.alerts.netty.handlers;

import static io.netty.handler.codec.http.HttpMethod.DELETE;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpMethod.PUT;
import static org.hawkular.alerts.api.json.JsonUtil.collectionFromJson;
import static org.hawkular.alerts.api.json.JsonUtil.fromJson;
import static org.hawkular.alerts.netty.HandlersManager.TENANT_HEADER_NAME;
import static org.hawkular.alerts.netty.util.ResponseUtil.badRequest;
import static org.hawkular.alerts.netty.util.ResponseUtil.checkTags;
import static org.hawkular.alerts.netty.util.ResponseUtil.extractPaging;
import static org.hawkular.alerts.netty.util.ResponseUtil.getCleanDampening;
import static org.hawkular.alerts.netty.util.ResponseUtil.handleExceptions;
import static org.hawkular.alerts.netty.util.ResponseUtil.isEmpty;
import static org.hawkular.alerts.netty.util.ResponseUtil.ok;
import static org.hawkular.alerts.netty.util.ResponseUtil.paginatedOk;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.hawkular.alerts.api.exception.NotFoundException;
import org.hawkular.alerts.api.json.GroupConditionsInfo;
import org.hawkular.alerts.api.json.GroupMemberInfo;
import org.hawkular.alerts.api.json.UnorphanMemberInfo;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.model.trigger.FullTrigger;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.TriggersCriteria;
import org.hawkular.alerts.engine.StandaloneAlerts;
import org.hawkular.alerts.log.MsgLogger;
import org.hawkular.alerts.netty.RestEndpoint;
import org.hawkular.alerts.netty.RestHandler;
import org.hawkular.alerts.netty.util.ResponseUtil;
import org.hawkular.alerts.netty.util.ResponseUtil.BadRequestException;
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
@RestEndpoint(path = "/triggers")
public class TriggersHandler implements RestHandler {
    private static final MsgLogger log = Logger.getMessageLogger(MsgLogger.class, TriggersHandler.class.getName());
    private static final String ROOT = "/";
    private static final String GROUPS = "groups";
    private static final String MEMBERS = "members";
    private static final String TRIGGER = "trigger";
    private static final String ORPHAN = "orphan";
    private static final String UNORPHAN = "unorphan";
    private static final String DAMPENINGS = "dampenings";
    private static final String MODE = "mode";
    private static final String CONDITIONS = "conditions";
    private static final String ENABLED = "enabled";
    private static final String PARAM_KEEP_NON_ORPHANS = "keepNonOrphans";
    private static final String PARAM_KEEP_ORPHANS = "keepOrphans";
    private static final String PARAM_INCLUDE_ORPHANS = "includeOrphans";
    private static final String PARAM_TRIGGER_IDS = "triggerIds";
    private static final String PARAM_TAGS = "tags";
    private static final String PARAM_THIN = "thin";
    private static final String PARAM_ENABLED = "enabled";

    DefinitionsService definitionsService;

    public TriggersHandler() {
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

        String[] tokens = subpath.substring(1).split(ROOT);

        // GET /
        if (method == GET && subpath.equals(ROOT)) {
            return findTriggers(req, resp, tenantId, params, req.uri());
        }
        // POST /
        if (method == POST && subpath.equals(ROOT)) {
            return createTrigger(req, resp, tenantId, false);
        }

        // GET /{triggerId}
        if (method == GET && tokens.length == 1) {
            return getTrigger(req, resp, tenantId, tokens[0], false);
        }
        // POST /trigger
        if (method == POST && tokens.length == 1 && TRIGGER.equals(tokens[0])) {
            return createFullTrigger(req, resp, tenantId);
        }
        // POST /groups
        if (method == POST && tokens.length == 1 && GROUPS.equals(tokens[0])) {
            return createTrigger(req, resp, tenantId, true);
        }
        // PUT /enabled
        if (method == PUT && tokens.length == 1 && ENABLED.equals(tokens[0])) {
            return setTriggersEnabled(req, resp, tenantId, params, false);
        }
        // PUT /{triggerId}
        if (method == PUT && tokens.length == 1) {
            return updateTrigger(req, resp, tenantId, tokens[0], false);
        }
        // DELETE /{triggerId}
        if (method == DELETE && tokens.length == 1) {
            return deleteTrigger(req, resp, tenantId, tokens[0]);
        }

        // GET /trigger/{triggerId}
        if (method == GET && tokens.length == 2 && TRIGGER.equals(tokens[0])) {
            return getTrigger(req, resp, tenantId, tokens[1], true);
        }
        // GET /{triggerId}/dampenings
        if (method == GET && tokens.length ==2 && DAMPENINGS.equals(tokens[1])) {
            return getTriggerDampenings(req, resp, tenantId, tokens[0], null);
        }
        // GET /{triggerId}/conditions
        if (method == GET && tokens.length == 2 && CONDITIONS.equals(tokens[1])) {
            return getTriggerConditions(req, resp, tenantId, tokens[0]);
        }
        // POST /groups/members
        if (method == POST && tokens.length == 2 && GROUPS.equals(tokens[0]) && MEMBERS.equals(tokens[1])) {
            return createGroupMember(req, resp, tenantId);
        }
        // POST /{triggerId}/dampenings
        if (method == POST && tokens.length == 2 && DAMPENINGS.equals(tokens[1])) {
            return createDampening(req, resp, tenantId, tokens[0], false);
        }
        // PUT /groups/enabled
        if (method == PUT && tokens.length == 2 && GROUPS.equals(tokens[0]) && ENABLED.equals(tokens[1])) {
            return setTriggersEnabled(req, resp, tenantId, params, true);
        }
        // PUT /groups/{groupId}
        if (method == PUT && tokens.length == 2 && GROUPS.equals(tokens[0])) {
            return updateTrigger(req, resp, tenantId, tokens[1], true);
        }
        // PUT /{triggerId}/conditions
        if (method == PUT && tokens.length == 2 && CONDITIONS.equals(tokens[1])) {
            return setConditions(req, resp, tenantId, tokens[0], null);
        }
        // DELETE /groups/{groupId}
        if (method == DELETE && tokens.length == 2 && GROUPS.equals(tokens[0])) {
            return deleteGroupTrigger(req, resp, tenantId, tokens[1], params);
        }

        // GET /{triggerId}/dampenings/{dampeningId}
        if (method == GET && tokens.length == 3 && DAMPENINGS.equals(tokens[1])) {
            return getDampening(req, resp, tenantId, tokens[0], tokens[2]);
        }
        // GET /groups/{groupId}/members
        if (method == GET && tokens.length == 3 && GROUPS.equals(tokens[0]) && MEMBERS.equals(tokens[2])) {
            return findGroupMembers(req, resp, tenantId, tokens[1], params);
        }
        // POST /groups/{groupId}/dampenings
        if (method == POST && tokens.length == 3 && GROUPS.equals(tokens[0]) && DAMPENINGS.equals(tokens[2])) {
            return createDampening(req, resp, tenantId, tokens[1], true);
        }
        // PUT /{triggerId}/dampenings/{dampeningId}
        if (method == PUT && tokens.length == 3 && DAMPENINGS.equals(tokens[1])) {
            return updateDampening(req, resp, tenantId, tokens[0], tokens[2], false);
        }
        // PUT /{triggerId}/conditions/{triggerMode}
        if (method == PUT && tokens.length == 3 && CONDITIONS.equals(tokens[1])) {
            return setConditions(req, resp, tenantId, tokens[0], tokens[2]);
        }
        // PUT /groups/{groupId}/conditions
        if (method == PUT && tokens.length == 3 && GROUPS.equals(tokens[0]) && CONDITIONS.equals(tokens[2])) {
            return setGroupConditions(req, resp, tenantId, tokens[1], null);
        }
        // DELETE /{triggerId}/dampenings/{dampeningId}
        if (method == DELETE && tokens.length == 3 && DAMPENINGS.equals(tokens[1])) {
            return deleteDampening(req, resp, tenantId, tokens[0], tokens[2], false);
        }

        // GET /{triggerId}/dampenings/mode/{triggerMode}
        if (method == GET && tokens.length == 4 && DAMPENINGS.equals(tokens[1]) && MODE.equals(tokens[2])) {
            return getTriggerDampenings(req, resp, tenantId, tokens[0], Mode.valueOf(tokens[3]));
        }
        // POST /groups/members/{memberId}/orphan
        if (method == POST && tokens.length == 4 && GROUPS.equals(tokens[0]) && MEMBERS.equals(tokens[1]) && ORPHAN.equals(tokens[3])) {
            return orphanMemberTrigger(req, resp, tenantId, tokens[2]);
        }
        // POST /groups/members/{memberId}/unorphan
        if (method == POST && tokens.length == 4 && GROUPS.equals(tokens[0]) && MEMBERS.equals(tokens[1]) && UNORPHAN.equals(tokens[3])) {
            return unorphanMemberTrigger(req, resp, tenantId, tokens[2]);
        }
        // PUT /groups/{groupId}/dampenings/{dampeningId}
        if (method == PUT && tokens.length == 4 && GROUPS.equals(tokens[0]) && DAMPENINGS.equals(tokens[2])) {
            return updateDampening(req, resp, tenantId, tokens[1], tokens[3], true);
        }
        // PUT /groups/{groupId}/conditions/{triggerMode}
        if (method == PUT && tokens.length == 4 && GROUPS.equals(tokens[0]) && CONDITIONS.equals(tokens[2])) {
            return setGroupConditions(req, resp, tenantId, tokens[1], tokens[3]);
        }
        // DELETE /groups/{groupId}/dampenings/{dampeningId}
        if (method == DELETE && tokens.length == 4 && GROUPS.equals(tokens[0]) && DAMPENINGS.equals(tokens[2])) {
            return deleteDampening(req, resp, tenantId, tokens[1], tokens[3], true);
        }

        return badRequest(resp, "Wrong path " + method + " " + subpath);
    }

    Publisher<Void> createDampening(HttpServerRequest req, HttpServerResponse resp, String tenantId, String triggerId, boolean isGroupTrigger) {
        return req
                .receive()
                .aggregate()
                .asString()
                .publishOn(Schedulers.elastic())
                .map(json -> {
                    Dampening parsed;
                    try {
                        parsed = fromJson(json, Dampening.class);
                        return parsed;
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing Dampening json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                })
                .flatMap(dampening -> {
                    dampening.setTenantId(tenantId);
                    dampening.setTriggerId(triggerId);
                    Dampening found;
                    try {
                        found = definitionsService.getDampening(tenantId, dampening.getDampeningId());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    if (found != null) {
                        throw new BadRequestException("Existing dampening for dampeningId: " + dampening.getDampeningId());
                    }
                    try {
                        Dampening d = getCleanDampening(dampening);
                        if (!isGroupTrigger) {
                            definitionsService.addDampening(tenantId, d);
                        } else {
                            definitionsService.addGroupDampening(tenantId, d);
                        }
                        log.debugf("Dampening: %s", dampening);
                        return ok(resp, d);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> createFullTrigger(HttpServerRequest req, HttpServerResponse resp, String tenantId) {
        return req
                .receive()
                .aggregate()
                .asString()
                .publishOn(Schedulers.elastic())
                .map(json -> {
                    FullTrigger parsed;
                    try {
                        parsed = fromJson(json, FullTrigger.class);
                        return parsed;
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing FullTrigger json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e);
                    }
                })
                .flatMap(fullTrigger -> {
                    if (fullTrigger.getTrigger() == null) {
                        throw new BadRequestException("Trigger is empty");
                    }
                    Trigger trigger = fullTrigger.getTrigger();
                    trigger.setTenantId(tenantId);
                    if (isEmpty(trigger.getId())) {
                        trigger.setId(Trigger.generateId());
                    } else {
                        Trigger found;
                        try {
                            found = definitionsService.getTrigger(tenantId, trigger.getId());
                        } catch (Exception e) {
                            log.debug(e.getMessage(), e);
                            throw new InternalServerException(e.toString());
                        }
                        if (found != null) {
                            throw new BadRequestException("Trigger with ID [" + trigger.getId() + "] exists.");
                        }
                    }
                    if (!checkTags(trigger)) {
                        throw new BadRequestException("Tags " + trigger.getTags() + " must be non empty.");
                    }
                    try {
                        definitionsService.addTrigger(tenantId, trigger);
                        log.debugf("Trigger: %s", trigger);
                        for (Dampening dampening : fullTrigger.getDampenings()) {
                            dampening.setTenantId(tenantId);
                            dampening.setTriggerId(trigger.getId());
                            boolean exist = (definitionsService.getDampening(tenantId, dampening.getDampeningId()) != null);
                            if (exist) {
                                definitionsService.removeDampening(tenantId, dampening.getDampeningId());
                            }
                            definitionsService.addDampening(tenantId, dampening);
                            log.debugf("Dampening: %s", dampening);
                        }
                        fullTrigger.getConditions().stream().forEach(c -> {
                            c.setTenantId(tenantId);
                            c.setTriggerId(trigger.getId());
                        });
                        List<Condition> firingConditions = fullTrigger.getConditions().stream()
                                .filter(c -> c.getTriggerMode() == Mode.FIRING)
                                .collect(Collectors.toList());
                        if (firingConditions != null && !firingConditions.isEmpty()) {
                            definitionsService.setConditions(tenantId, trigger.getId(), Mode.FIRING, firingConditions);
                            log.debugf("Conditions: %s", firingConditions);
                        }
                        List<Condition> autoResolveConditions = fullTrigger.getConditions().stream()
                                .filter(c -> c.getTriggerMode() == Mode.AUTORESOLVE)
                                .collect(Collectors.toList());
                        if (autoResolveConditions != null && !autoResolveConditions.isEmpty()) {
                            definitionsService.setConditions(tenantId, trigger.getId(), Mode.AUTORESOLVE, autoResolveConditions);
                            log.debugf("Conditions: %s", autoResolveConditions);
                        }
                        return ok(resp, fullTrigger);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e);
                    }
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> createGroupMember(HttpServerRequest req, HttpServerResponse resp, String tenantId) {
        return req
                .receive()
                .aggregate()
                .asString()
                .publishOn(Schedulers.elastic())
                .map(json -> {
                    GroupMemberInfo parsed;
                    try {
                        parsed = fromJson(json, GroupMemberInfo.class);
                        return parsed;
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing GroupMemberInfo json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                })
                .flatMap(groupMember -> {
                    String groupId = groupMember.getGroupId();
                    if (isEmpty(groupId)) {
                        throw new BadRequestException("MemberTrigger groupId is null");
                    }
                    if (!checkTags(groupMember)) {
                        throw new BadRequestException("Tags " + groupMember.getMemberTags() + " must be non empty.");
                    }
                    try {
                        Trigger child = definitionsService.addMemberTrigger(tenantId, groupId, groupMember.getMemberId(),
                                groupMember.getMemberName(),
                                groupMember.getMemberDescription(),
                                groupMember.getMemberContext(),
                                groupMember.getMemberTags(),
                                groupMember.getDataIdMap());
                        log.debugf("Child Trigger: %s", child);
                        return ok(resp, child);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> createTrigger(HttpServerRequest req, HttpServerResponse resp, String tenantId, boolean isGroupTrigger) {
        return req
                .receive()
                .aggregate()
                .asString()
                .publishOn(Schedulers.elastic())
                .map(json -> {
                    Trigger parsed;
                    try {
                        parsed = fromJson(json, Trigger.class);
                        return parsed;
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing Trigger json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e);
                    }
                })
                .flatMap(trigger -> {
                    if (isEmpty(trigger.getId())) {
                        trigger.setId(Trigger.generateId());
                    } else {
                        Trigger found;
                        try {
                            found = definitionsService.getTrigger(tenantId, trigger.getId());
                        } catch (Exception e) {
                            log.debug(e.getMessage(), e);
                            throw new InternalServerException(e.toString());
                        }
                        if (found != null) {
                            throw new BadRequestException("Trigger with ID [" + trigger.getId() + "] exists.");
                        }
                    }
                    if (!checkTags(trigger)) {
                        throw new BadRequestException("Tags " + trigger.getTags() + " must be non empty.");
                    }
                    try {
                        if (isGroupTrigger) {
                            definitionsService.addGroupTrigger(tenantId, trigger);
                        } else {
                            definitionsService.addTrigger(tenantId, trigger);
                        }
                        log.debugf("Trigger: %s", trigger);
                        return ok(resp, trigger);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> deleteDampening(HttpServerRequest req, HttpServerResponse resp, String tenantId, String triggerId, String dampeningId, boolean isGroupTrigger) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    Dampening found;
                    try {
                        found = definitionsService.getDampening(tenantId, dampeningId);
                        if (found != null) {
                            if (!isGroupTrigger) {
                                definitionsService.removeDampening(tenantId, dampeningId);
                            } else {
                                definitionsService.removeGroupDampening(tenantId, dampeningId);
                            }
                            log.debugf("DampeningId: %s", dampeningId);
                            return found;
                        }
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    throw new ResponseUtil.NotFoundException("Dampening " + dampeningId + " not found for triggerId: " + triggerId);
                }))
                .flatMap(dampening -> ok(resp))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> deleteGroupTrigger(HttpServerRequest req, HttpServerResponse resp, String tenantId, String groupId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    try {
                        boolean keepNonOrphans = false;
                        if (params.get(PARAM_KEEP_NON_ORPHANS) != null) {
                            keepNonOrphans = Boolean.valueOf(params.get(PARAM_KEEP_NON_ORPHANS).get(0));
                        }
                        boolean keepOrphans = false;
                        if (params.get(PARAM_KEEP_ORPHANS) != null) {
                            keepOrphans = Boolean.valueOf(params.get(PARAM_KEEP_ORPHANS).get(0));
                        }
                        definitionsService.removeGroupTrigger(tenantId, groupId, keepNonOrphans, keepOrphans);
                        if (log.isDebugEnabled()) {
                            log.debugf("Remove Group Trigger: %s / %s", tenantId, groupId);
                        }
                        return groupId;
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(removed -> ok(resp))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> deleteTrigger(HttpServerRequest req, HttpServerResponse resp, String tenantId, String triggerId) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    try {
                        definitionsService.removeTrigger(tenantId, triggerId);
                        log.debugf("TriggerId: %s", triggerId);
                        return triggerId;
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }))
                .flatMap(removed -> ok(resp))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> findGroupMembers(HttpServerRequest req, HttpServerResponse resp, String tenantId, String groupId, Map<String, List<String>> params) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    try {
                        boolean includeOrphans = false;
                        if (params.get(PARAM_INCLUDE_ORPHANS) != null) {
                            includeOrphans = Boolean.valueOf(params.get(PARAM_INCLUDE_ORPHANS).get(0));
                        }
                        Collection<Trigger> members = definitionsService.getMemberTriggers(tenantId, groupId, includeOrphans);
                        log.debugf("Member Triggers: %s", members);
                        return members;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(members -> ok(resp, members))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> findTriggers(HttpServerRequest req, HttpServerResponse resp, String tenantId, Map<String, List<String>> params, String uri) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    try {
                        Pager pager = extractPaging(params);
                        TriggersCriteria criteria = buildCriteria(params);
                        Page<Trigger> triggerPage = definitionsService.getTriggers(tenantId, criteria, pager);
                        log.debugf("Triggers: %s", triggerPage);
                        return triggerPage;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(triggerPage -> {
                    if (isEmpty(triggerPage)) {
                        return ok(resp, triggerPage);
                    }
                    return paginatedOk(req, resp, triggerPage, uri);
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> getDampening(HttpServerRequest req, HttpServerResponse resp, String tenantId, String triggerId, String dampeningId) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    Dampening found;
                    try {
                        found = definitionsService.getDampening(tenantId, dampeningId);
                        if (found != null) {
                            return found;
                        }
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    throw new ResponseUtil.NotFoundException("No dampening found for triggerId: " + triggerId + " and dampeningId:" + dampeningId);
                }))
                .flatMap(found -> ok(resp, found))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> getTrigger(HttpServerRequest req, HttpServerResponse resp, String tenantId, String triggerId, boolean isFullTrigger) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    Trigger found;
                    try {
                        found = definitionsService.getTrigger(tenantId, triggerId);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    if (found == null) {
                        throw new ResponseUtil.NotFoundException("triggerId: " + triggerId + " not found");
                    }
                    log.debugf("Trigger: %s", found);
                    return found;
                }))
                .flatMap(found -> {
                    if (isFullTrigger) {
                        try {
                            List<Dampening> dampenings = new ArrayList<>(definitionsService.getTriggerDampenings(tenantId, found.getId(), null));
                            List<Condition> conditions = new ArrayList<>(definitionsService.getTriggerConditions(tenantId, found.getId(), null));
                            FullTrigger fullTrigger = new FullTrigger(found, dampenings, conditions);
                            return ok(resp, fullTrigger);
                        } catch (IllegalArgumentException e) {
                            throw new BadRequestException("Bad arguments: " + e.getMessage());
                        } catch (Exception e) {
                            log.debug(e.getMessage(), e);
                            throw new InternalServerException(e.toString());
                        }
                    }
                    return ok(resp, found);
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> getTriggerConditions(HttpServerRequest req, HttpServerResponse resp, String tenantId, String triggerId) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    try {
                        Collection<Condition> conditions = definitionsService.getTriggerConditions(tenantId, triggerId, null);
                        log.debugf("Conditions: %s", conditions);
                        return conditions;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(conditions -> ok(resp, conditions))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> getTriggerDampenings(HttpServerRequest req, HttpServerResponse resp, String tenantId, String triggerId, Mode triggerMode) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    try {
                        Collection<Dampening> dampenings = definitionsService.getTriggerDampenings(tenantId, triggerId, triggerMode);
                        log.debug("Dampenings: " + dampenings);
                        return dampenings;
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(dampenings -> ok(resp, dampenings))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> updateTrigger(HttpServerRequest req, HttpServerResponse resp, String tenantId, String triggerId, boolean isGroupTrigger) {
        return req
                .receive()
                .aggregate()
                .asString()
                .publishOn(Schedulers.elastic())
                .map(json -> {
                    Trigger parsed;
                    try {
                        parsed = fromJson(json, Trigger.class);
                        return parsed;
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing Trigger json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString(), e);
                    }
                })
                .flatMap(trigger -> {
                    if (trigger != null && !isEmpty(triggerId)) {
                        trigger.setId(triggerId);
                    }
                    if (!checkTags(trigger)) {
                        throw new BadRequestException("Tags " + trigger.getTags() + " must be non empty.");
                    }
                    try {
                        if (isGroupTrigger) {
                            definitionsService.updateGroupTrigger(tenantId, trigger);
                        } else {
                            definitionsService.updateTrigger(tenantId, trigger);
                        }
                        log.debugf("Trigger: %s", trigger);
                        return ok(resp);
                    } catch (NotFoundException e) {
                        throw new NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> orphanMemberTrigger(HttpServerRequest req, HttpServerResponse resp, String tenantId, String memberId) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    try {
                        Trigger child = definitionsService.orphanMemberTrigger(tenantId, memberId);
                        log.debugf("Orphan Member Trigger: %s", child);
                        return memberId;
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(orphan -> ok(resp))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> updateDampening(HttpServerRequest req, HttpServerResponse resp, String tenantId, String triggerId, String dampeningId, boolean isGroupTrigger) {
        return req
                .receive()
                .aggregate()
                .asString()
                .publishOn(Schedulers.elastic())
                .map(json -> {
                    Dampening parsed;
                    try {
                        parsed = fromJson(json, Dampening.class);
                        return parsed;
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing Dampening json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                })
                .flatMap(dampening -> {
                    Dampening found;
                    try {
                        found = definitionsService.getDampening(tenantId, dampeningId);
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    if (found == null) {
                        throw new ResponseUtil.NotFoundException("No dampening found for dampeningId: " + dampeningId);
                    }
                    try {
                        dampening.setTriggerId(triggerId);
                        Dampening d = getCleanDampening(dampening);
                        log.debugf("Dampening: %s", d);
                        if (isGroupTrigger) {
                            definitionsService.updateGroupDampening(tenantId, d);
                        } else {
                            definitionsService.updateDampening(tenantId, d);
                        }
                        return ok(resp, d);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> unorphanMemberTrigger(HttpServerRequest req, HttpServerResponse resp, String tenantId, String memberId) {
        return req
                .receive()
                .aggregate()
                .asString()
                .publishOn(Schedulers.elastic())
                .map(json -> {
                    UnorphanMemberInfo parsed;
                    try {
                        parsed = fromJson(json, UnorphanMemberInfo.class);
                        return parsed;
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing UnorphanMemberInfo json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                })
                .flatMap(unorphanMemberInfo -> {
                    if (!checkTags(unorphanMemberInfo)) {
                        throw new BadRequestException("Tags " + unorphanMemberInfo.getMemberTags() + " must be non empty.");
                    }
                    try {
                        Trigger child = definitionsService.unorphanMemberTrigger(tenantId, memberId,
                                unorphanMemberInfo.getMemberContext(),
                                unorphanMemberInfo.getMemberTags(),
                                unorphanMemberInfo.getDataIdMap());
                        log.debugf("Member Trigger: %s",child);
                        return ok(resp);
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> setConditions(HttpServerRequest req, HttpServerResponse resp, String tenantId, String triggerId, String triggerMode) {
        return req
                .receive()
                .aggregate()
                .asString()
                .publishOn(Schedulers.elastic())
                .map(json -> {
                    Collection<Condition> parsed;
                    try {
                        parsed = collectionFromJson(json, Condition.class);
                        return parsed;
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing Condition json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                })
                .flatMap(conditions -> {
                    Collection<Condition> updatedConditions;
                    if (triggerMode == null) {
                        updatedConditions = new HashSet<>();
                        conditions.stream().forEach(c -> c.setTriggerId(triggerId));
                        Collection<Condition> firingConditions = conditions.stream()
                                .filter(c -> c.getTriggerMode() == null || c.getTriggerMode().equals(Mode.FIRING))
                                .collect(Collectors.toList());
                        Collection<Condition> autoResolveConditions = conditions.stream()
                                .filter(c -> c.getTriggerMode().equals(Mode.AUTORESOLVE))
                                .collect(Collectors.toList());
                        try {
                            updatedConditions.addAll(definitionsService.setConditions(tenantId, triggerId, Mode.FIRING, firingConditions));
                            updatedConditions.addAll(definitionsService.setConditions(tenantId, triggerId, Mode.AUTORESOLVE,
                                    autoResolveConditions));
                        } catch (NotFoundException e) {
                            throw new ResponseUtil.NotFoundException(e.getMessage());
                        } catch (IllegalArgumentException e) {
                            throw new BadRequestException("Bad arguments: " + e.getMessage());
                        } catch (Exception e) {
                            log.debug(e.getMessage(), e);
                            throw new InternalServerException(e.toString());
                        }
                        log.debugf("Conditions: %s", updatedConditions);
                        return ok(resp, updatedConditions);
                    }

                    Mode mode = Mode.valueOf(triggerMode.toUpperCase());
                    if (!isEmpty(conditions)) {
                        for (Condition condition : conditions) {
                            condition.setTriggerId(triggerId);
                            if (condition.getTriggerMode() == null || !condition.getTriggerMode().equals(mode)) {
                                throw new BadRequestException("Condition: " + condition + " has a different triggerMode [" + triggerMode + "]");
                            }
                        }
                    }
                    try {
                        updatedConditions = definitionsService.setConditions(tenantId, triggerId, mode, conditions);
                        return ok(resp, updatedConditions);
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> setGroupConditions(HttpServerRequest req, HttpServerResponse resp, String tenantId, String groupId, String triggerMode) {
        return req
                .receive()
                .aggregate()
                .asString()
                .publishOn(Schedulers.elastic())
                .map(json -> {
                    GroupConditionsInfo parsed;
                    try {
                        parsed = fromJson(json, GroupConditionsInfo.class);
                        return parsed;
                    } catch (Exception e) {
                        log.errorf(e, "Error parsing GroupConditionsInfo json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                })
                .flatMap(groupConditionsInfo -> {
                    Collection<Condition> updatedConditions = new HashSet<>();
                    if (groupConditionsInfo == null) {
                        throw new BadRequestException("GroupConditionsInfo must be non null.");
                    }
                    if (groupConditionsInfo.getConditions() == null) {
                        groupConditionsInfo.setConditions(Collections.EMPTY_LIST);
                    }
                    for (Condition condition : groupConditionsInfo.getConditions()) {
                        if (condition == null) {
                            throw new BadRequestException("GroupConditionsInfo must have non null conditions: " + groupConditionsInfo);
                        }
                        condition.setTriggerId(groupId);
                    }
                    if (triggerMode == null) {
                        Collection<Condition> firingConditions = groupConditionsInfo.getConditions().stream()
                                .filter(c -> c.getTriggerMode() == null || c.getTriggerMode().equals(Mode.FIRING))
                                .collect(Collectors.toList());
                        Collection<Condition> autoResolveConditions = groupConditionsInfo.getConditions().stream()
                                .filter(c -> c.getTriggerMode().equals(Mode.AUTORESOLVE))
                                .collect(Collectors.toList());
                        try {
                            updatedConditions.addAll(definitionsService.setGroupConditions(tenantId, groupId, Mode.FIRING, firingConditions,
                                    groupConditionsInfo.getDataIdMemberMap()));
                            updatedConditions.addAll(definitionsService.setGroupConditions(tenantId, groupId, Mode.AUTORESOLVE,
                                    autoResolveConditions,
                                    groupConditionsInfo.getDataIdMemberMap()));
                        } catch (NotFoundException e) {
                            throw new ResponseUtil.NotFoundException(e.getMessage());
                        } catch (IllegalArgumentException e) {
                            throw new BadRequestException("Bad arguments: " + e.getMessage());
                        } catch (Exception e) {
                            log.debug(e.getMessage(), e);
                            throw new InternalServerException(e.toString());
                        }
                        log.debugf("Conditions: %s", updatedConditions);
                        return ok(resp, updatedConditions);
                    }
                    Mode mode = Mode.valueOf(triggerMode.toUpperCase());
                    for (Condition condition : groupConditionsInfo.getConditions()) {
                        if (condition == null) {
                            throw new BadRequestException("GroupConditionsInfo must have non null conditions: " + groupConditionsInfo);
                        }
                        condition.setTriggerId(groupId);
                        if (condition.getTriggerMode() == null || !condition.getTriggerMode().equals(mode)) {
                            throw new BadRequestException("Condition: " + condition + " has a different triggerMode [" + triggerMode + "]");
                        }
                    }
                    try {
                        updatedConditions = definitionsService.setGroupConditions(tenantId, groupId, mode,
                                groupConditionsInfo.getConditions(),
                                groupConditionsInfo.getDataIdMemberMap());
                        log.debugf("Conditions: %s", updatedConditions);
                        return ok(resp, updatedConditions);
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                })
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    Publisher<Void> setTriggersEnabled(HttpServerRequest req, HttpServerResponse resp, String tenantId, Map<String, List<String>> params, boolean isGroupTrigger) {
        return req
                .receive()
                .publishOn(Schedulers.elastic())
                .thenMany(Mono.fromSupplier(() -> {
                    try {
                        String triggerIds = null;
                        Boolean enabled = null;
                        if (params.get(PARAM_TRIGGER_IDS) != null) {
                            triggerIds = params.get(PARAM_TRIGGER_IDS).get(0);
                        }
                        if (params.get(PARAM_ENABLED) != null) {
                            enabled = Boolean.valueOf(params.get(PARAM_ENABLED).get(0));
                        }
                        if (isEmpty(triggerIds)) {
                            return badRequest(resp, "TriggerIds must be non empty.");
                        }
                        if (null == enabled) {
                            return badRequest(resp, "Enabled must be non-empty.");
                        }
                        if (isGroupTrigger) {
                            definitionsService.updateGroupTriggerEnablement(tenantId, triggerIds, enabled);
                        } else {
                            definitionsService.updateTriggerEnablement(tenantId, triggerIds, enabled);
                        }
                        return triggerIds;
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }))
                .flatMap(triggerIds -> ok(resp))
                .onErrorResumeWith(e -> handleExceptions(resp, e));
    }

    TriggersCriteria buildCriteria(Map<String, List<String>> params) {
        String triggerIds = null;
        String tags = null;
        boolean thin = false;

        if (params.get(PARAM_TRIGGER_IDS) != null) {
            triggerIds = params.get(PARAM_TRIGGER_IDS).get(0);
        }
        if (params.get(PARAM_TAGS) != null) {
            tags = params.get(PARAM_TAGS).get(0);
        }
        if (params.get(PARAM_THIN) != null) {
            thin = Boolean.valueOf(params.get(PARAM_THIN).get(0));
        }

        TriggersCriteria criteria = new TriggersCriteria();
        if (!isEmpty(triggerIds)) {
            criteria.setTriggerIds(Arrays.asList(triggerIds.split(",")));
        }
        if (!isEmpty(tags)) {
            String[] tagTokens = tags.split(",");
            Map<String, String> tagsMap = new HashMap<>(tagTokens.length);
            for (String tagToken : tagTokens) {
                String[] fields = tagToken.split("\\|");
                if (fields.length == 2) {
                    tagsMap.put(fields[0], fields[1]);
                } else {
                    if (log.isDebugEnabled()) {
                        log.debugf("Invalid Tag Criteria %s", Arrays.toString(fields));
                    }
                    throw new IllegalArgumentException( "Invalid Tag Criteria " + Arrays.toString(fields) );
                }
            }
            criteria.setTags(tagsMap);
        }
        criteria.setThin(thin);

        return criteria;
    }
}
