package org.hawkular.alerts.handlers;

import static org.hawkular.alerts.api.json.JsonUtil.collectionFromJson;
import static org.hawkular.alerts.api.json.JsonUtil.fromJson;
import static org.hawkular.alerts.api.util.Util.isEmpty;
import static org.hawkular.alerts.handlers.util.ResponseUtil.PARAMS_PAGING;
import static org.hawkular.alerts.handlers.util.ResponseUtil.checkForUnknownQueryParams;
import static org.hawkular.alerts.handlers.util.ResponseUtil.checkTags;
import static org.hawkular.alerts.handlers.util.ResponseUtil.checkTenant;
import static org.hawkular.alerts.handlers.util.ResponseUtil.extractPaging;
import static org.hawkular.alerts.handlers.util.ResponseUtil.getCleanDampening;
import static org.hawkular.alerts.handlers.util.ResponseUtil.result;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
import org.hawkular.alerts.handlers.util.ResponseUtil;
import org.hawkular.alerts.handlers.util.ResponseUtil.BadRequestException;
import org.hawkular.alerts.handlers.util.ResponseUtil.InternalServerException;
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
@RestEndpoint(path = "/triggers")
public class TriggersHandler implements RestHandler {
    private static final MsgLogger log = MsgLogging.getMsgLogger(TriggersHandler.class);
    private static final String PARAM_KEEP_NON_ORPHANS = "keepNonOrphans";
    private static final String PARAM_KEEP_ORPHANS = "keepOrphans";
    private static final String PARAM_INCLUDE_ORPHANS = "includeOrphans";
    private static final String PARAM_TRIGGER_IDS = "triggerIds";
    private static final String PARAM_TAGS = "tags";
    private static final String PARAM_THIN = "thin";
    private static final String PARAM_ENABLED = "enabled";

    private static final String FIND_TRIGGERS = "findTriggers";
    private static final Map<String, Set<String>> queryParamValidationMap = new HashMap<>();
    static {
        Collection<String> TRIGGERS_CRITERIA = Arrays.asList(PARAM_TRIGGER_IDS,
                PARAM_TAGS,
                PARAM_TRIGGER_IDS,
                PARAM_THIN);
        queryParamValidationMap.put(FIND_TRIGGERS, new HashSet<>(TRIGGERS_CRITERIA));
        queryParamValidationMap.get(FIND_TRIGGERS).addAll(PARAMS_PAGING);
    }

    DefinitionsService definitionsService;

    public TriggersHandler() {
        definitionsService = StandaloneAlerts.getDefinitionsService();
    }

    @Override
    public void initRoutes(String baseUrl, Router router) {
        String path = baseUrl + "/triggers";
        router.get(path).handler(this::findTriggers);
        router.post(path).handler(r -> createTrigger(r, false));
        router.get(path + "/:triggerId").handler(r -> getTrigger(r, false));
        router.post(path + "/trigger").handler(this::createFullTrigger);
        router.put(path + "/trigger/:triggerId").handler(this::updateFullTrigger);
        router.post(path + "/groups").handler(r -> createTrigger(r, true));
        router.put(path + "/enabled").handler(r -> setTriggersEnabled(r, false));
        router.put(path + "/:triggerId").handler(r -> updateTrigger(r, false));
        router.delete(path + "/:triggerId").handler(this::deleteTrigger);
        router.get(path + "/trigger/:triggerId").handler(r -> getTrigger(r, true));
        router.get(path + "/:triggerId/dampenings").handler(this::getTriggerDampenings);
        router.get(path + "/:triggerId/conditions").handler(this::getTriggerConditions);
        router.post(path + "/groups/members").handler(this::createGroupMember);
        router.post(path + "/:triggerId/dampenings").handler(r -> createDampening(r, false));
        router.put(path + "/groups/enabled").handler(r -> setTriggersEnabled(r, true));
        router.put(path + "/groups/:groupId").handler(r -> updateTrigger(r, true));
        router.put(path + "/:triggerId/conditions").handler(this::setAllConditions);
        router.delete(path + "/groups/:groupId").handler(this::deleteGroupTrigger);
        router.get(path + "/:triggerId/dampenings/:dampeningId").handler(this::getDampening);
        router.get(path + "/groups/:groupId/members").handler(this::findGroupMembers);
        router.post(path + "/groups/:groupId/dampenings").handler(r -> createDampening(r, true));
        router.put(path + "/:triggerId/dampenings/:dampeningId").handler(r -> updateDampening(r, false));
        router.put(path + "/:triggerId/conditions/:triggerMode").handler(this::setConditions);
        router.put(path + "/groups/:groupId/conditions").handler(this::setGroupConditions);
        router.delete(path + "/:triggerId/dampenings/:dampeningId").handler(r -> deleteDampening(r, false));
        router.get(path + "/:triggerId/dampenings/mode/:triggerMode").handler(this::getTriggerDampenings);
        router.post(path + "/groups/members/:memberId/orphan").handler(this::orphanMemberTrigger);
        router.post(path + "/groups/members/:memberId/unorphan").handler(this::unorphanMemberTrigger);
        router.put(path + "/groups/:groupId/dampenings/:dampeningId").handler(r -> updateDampening(r, true));
        router.put(path + "/groups/:groupId/conditions/:triggerMode").handler(this::setGroupConditions);
        router.delete(path + "/groups/:groupId/dampenings/:dampeningId").handler(r -> deleteDampening(r, true));
    }

    void createDampening(RoutingContext routing, boolean isGroup) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    String triggerId = routing.request().getParam("triggerId");
                    String groupId = routing.request().getParam("groupId");
                    Dampening dampening;
                    try {
                        dampening = fromJson(json, Dampening.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing Dampening json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                    dampening.setTenantId(tenantId);
                    dampening.setTriggerId(isGroup ? groupId : triggerId);
                    Dampening found = null;
                    try {
                        found = definitionsService.getDampening(tenantId, dampening.getDampeningId());
                    } catch(NotFoundException e) {
                        // Expected
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    if (found != null) {
                        throw new BadRequestException("Existing dampening for dampeningId: " + dampening.getDampeningId());
                    }
                    try {
                        Dampening d = getCleanDampening(dampening);
                        if (isGroup) {
                            definitionsService.addGroupDampening(tenantId, d);
                        } else {
                            definitionsService.addDampening(tenantId, d);
                        }
                        log.debugf("Dampening: %s", dampening);
                        future.complete(d);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void createFullTrigger(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    FullTrigger fullTrigger;
                    try {
                        fullTrigger = fromJson(json, FullTrigger.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing FullTrigger json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e);
                    }
                    if (fullTrigger.getTrigger() == null) {
                        throw new BadRequestException("Trigger is empty");
                    }

                    Trigger trigger = fullTrigger.getTrigger();
                    trigger.setTenantId(tenantId);
                    if (isEmpty(trigger.getId())) {
                        trigger.setId(Trigger.generateId());
                    } else {
                        Trigger found = null;
                        try {
                            found = definitionsService.getTrigger(tenantId, trigger.getId());
                        } catch (NotFoundException e) {
                            // Expected
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
                        definitionsService.createFullTrigger(tenantId, fullTrigger);
                        future.complete(fullTrigger);

                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e);
                    }
                }, res -> result(routing, res));
    }

    void updateFullTrigger(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    String triggerId = routing.request().getParam("triggerId");
                    FullTrigger fullTrigger;
                    Trigger trigger;
                    try {
                        fullTrigger = fromJson(json, FullTrigger.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing FullTrigger json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString(), e);
                    }
                    if (null == fullTrigger) {
                        throw new BadRequestException("FullTrigger can not be null.");
                    }
                    trigger = fullTrigger.getTrigger();
                    if (null == trigger) {
                        throw new BadRequestException("FullTrigger.Trigger can not be null.");
                    }
                    trigger.setId(triggerId);
                    if (!checkTags(trigger)) {
                        throw new BadRequestException(
                                "Tags " + trigger.getTags() + " must be non empty.");
                    }
                    try {
                        definitionsService.updateFullTrigger(tenantId, fullTrigger);
                        log.debugf("FullTrigger: %s", fullTrigger);
                        future.complete();
                    } catch (NotFoundException e) {
                        throw new NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void createGroupMember(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    GroupMemberInfo groupMember;
                    try {
                        groupMember = fromJson(json, GroupMemberInfo.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing GroupMemberInfo json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
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
                        future.complete(child);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void createTrigger(RoutingContext routing, boolean isGroup) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    Trigger trigger;
                    try {
                        trigger = fromJson(json, Trigger.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing Trigger json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e);
                    }
                    if (isEmpty(trigger.getId())) {
                        trigger.setId(Trigger.generateId());
                    } else {
                        Trigger found = null;
                        try {
                            found = definitionsService.getTrigger(tenantId, trigger.getId());
                        } catch (NotFoundException e) {
                            // expected
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
                        if (isGroup) {
                            definitionsService.addGroupTrigger(tenantId, trigger);
                        } else {
                            definitionsService.addTrigger(tenantId, trigger);
                        }
                        log.debugf("Trigger: %s", trigger);
                        future.complete(trigger);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void deleteDampening(RoutingContext routing, boolean isGroup) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String dampeningId = routing.request().getParam("dampeningId");
                    Dampening found;
                    try {
                        found = definitionsService.getDampening(tenantId, dampeningId);
                        if (found != null) {
                            if (isGroup) {
                                definitionsService.removeGroupDampening(tenantId, dampeningId);
                            } else {
                                definitionsService.removeDampening(tenantId, dampeningId);
                            }
                            log.debugf("DampeningId: %s", dampeningId);
                            future.complete(found);
                            return;
                        }
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    throw new ResponseUtil.NotFoundException("Dampening " + dampeningId + " not found ");
                }, res -> result(routing, res));
    }

    void deleteGroupTrigger(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String groupId = routing.request().getParam("groupId");
                    try {
                        boolean keepNonOrphans = false;
                        if (routing.request().params().get(PARAM_KEEP_NON_ORPHANS) != null) {
                            keepNonOrphans = Boolean.valueOf(routing.request().params().get(PARAM_KEEP_NON_ORPHANS));
                        }
                        boolean keepOrphans = false;
                        if (routing.request().params().get(PARAM_KEEP_ORPHANS) != null) {
                            keepOrphans = Boolean.valueOf(routing.request().params().get(PARAM_KEEP_ORPHANS));
                        }
                        definitionsService.removeGroupTrigger(tenantId, groupId, keepNonOrphans, keepOrphans);
                        if (log.isDebugEnabled()) {
                            log.debugf("Remove Group Trigger: %s / %s", tenantId, groupId);
                        }
                        future.complete();
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void deleteTrigger(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String triggerId = routing.request().getParam("triggerId");
                    try {
                        definitionsService.removeTrigger(tenantId, triggerId);
                        log.debugf("TriggerId: %s", triggerId);
                        future.complete();
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new ResponseUtil.BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new ResponseUtil.InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void findGroupMembers(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String groupId = routing.request().getParam("groupId");
                    try {
                        boolean includeOrphans = false;
                        if (routing.request().params().get(PARAM_INCLUDE_ORPHANS) != null) {
                            includeOrphans = Boolean.valueOf(routing.request().params().get(PARAM_INCLUDE_ORPHANS));
                        }
                        Collection<Trigger> members = definitionsService.getMemberTriggers(tenantId, groupId, includeOrphans);
                        log.debugf("Member Triggers: %s", members);
                        future.complete(members);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void findTriggers(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    try {
                        checkForUnknownQueryParams(routing.request().params(), queryParamValidationMap.get(FIND_TRIGGERS));
                        Pager pager = extractPaging(routing.request().params());
                        TriggersCriteria criteria = buildCriteria(routing.request().params());
                        Page<Trigger> triggerPage = definitionsService.getTriggers(tenantId, criteria, pager);
                        log.debugf("Triggers: %s", triggerPage);
                        future.complete(triggerPage);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void getDampening(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String dampeningId = routing.request().getParam("dampeningId");
                    Dampening found;
                    try {
                        found = definitionsService.getDampening(tenantId, dampeningId);
                        if (found != null) {
                            future.complete(found);
                            return;
                        }
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                    throw new ResponseUtil.NotFoundException("No dampening found for dampeningId:" + dampeningId);
                }, res -> result(routing, res));
    }

    void getTrigger(RoutingContext routing, boolean isFullTrigger) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String triggerId = routing.request().getParam("triggerId");
                    Object found = null;
                    try {
                        found = isFullTrigger ? definitionsService.getFullTrigger(tenantId, triggerId)
                                : definitionsService.getTrigger(tenantId, triggerId);
                    } catch (NotFoundException e) {
                        // Expected
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
                    future.complete(found);
                }, res -> result(routing, res));
    }

    void getTriggerConditions(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String triggerId = routing.request().getParam("triggerId");
                    try {
                        Collection<Condition> conditions = definitionsService.getTriggerConditions(tenantId, triggerId, null);
                        log.debugf("Conditions: %s", conditions);
                        future.complete(conditions);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void getTriggerDampenings(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String triggerId = routing.request().getParam("triggerId");
                    String triggerMode = routing.request().getParam("triggerMode");
                    Mode mode = null;
                    if (triggerMode != null) {
                        mode = Mode.valueOf(triggerMode);
                    }
                    try {
                        Collection<Dampening> dampenings = definitionsService.getTriggerDampenings(tenantId, triggerId, mode);
                        log.debugf("Dampenings: %s", dampenings);
                        future.complete(dampenings);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void updateTrigger(RoutingContext routing, boolean isGroup) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    String triggerId = routing.request().getParam("triggerId");
                    String groupId = routing.request().getParam("groupId");
                    Trigger trigger;
                    try {
                        trigger = fromJson(json, Trigger.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing Trigger json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString(), e);
                    }
                    if (trigger != null && !isEmpty(triggerId)) {
                        trigger.setId(isGroup ? groupId : triggerId);
                    }
                    if (!checkTags(trigger)) {
                        throw new BadRequestException("Tags " + trigger.getTags() + " must be non empty.");
                    }
                    try {
                        if (isGroup) {
                            definitionsService.updateGroupTrigger(tenantId, trigger);
                        } else {
                            definitionsService.updateTrigger(tenantId, trigger);
                        }
                        log.debugf("Trigger: %s", trigger);
                        future.complete();
                    } catch (NotFoundException e) {
                        throw new NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void orphanMemberTrigger(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String memberId = routing.request().getParam("memberId");
                    try {
                        Trigger child = definitionsService.orphanMemberTrigger(tenantId, memberId);
                        log.debugf("Orphan Member Trigger: %s", child);
                        future.complete();
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void updateDampening(RoutingContext routing, boolean isGroup) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    String triggerId = routing.request().getParam("triggerId");
                    String groupId = routing.request().getParam("groupId");
                    String dampeningId = routing.request().getParam("dampeningId");
                    Dampening dampening;
                    try {
                        dampening = fromJson(json, Dampening.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing Dampening json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
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
                        dampening.setTriggerId(isGroup ? groupId : triggerId);
                        Dampening d = getCleanDampening(dampening);
                        log.debugf("Dampening: %s", d);
                        if (isGroup) {
                            definitionsService.updateGroupDampening(tenantId, d);
                        } else {
                            definitionsService.updateDampening(tenantId, d);
                        }
                        future.complete(d);
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void unorphanMemberTrigger(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    String memberId = routing.request().getParam("memberId");
                    UnorphanMemberInfo unorphanMemberInfo;
                    try {
                        unorphanMemberInfo = fromJson(json, UnorphanMemberInfo.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing UnorphanMemberInfo json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                    if (!checkTags(unorphanMemberInfo)) {
                        throw new BadRequestException("Tags " + unorphanMemberInfo.getMemberTags() + " must be non empty.");
                    }
                    try {
                        Trigger child = definitionsService.unorphanMemberTrigger(tenantId, memberId,
                                unorphanMemberInfo.getMemberContext(),
                                unorphanMemberInfo.getMemberTags(),
                                unorphanMemberInfo.getDataIdMap());
                        log.debugf("Member Trigger: %s",child);
                        future.complete();
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void setAllConditions(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    String triggerId = routing.request().getParam("triggerId");
                    Collection<Condition> conditions;
                    try {
                        conditions = collectionFromJson(json, Condition.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing Condition json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                    try {
                        Collection<Condition> updatedConditions = definitionsService.setAllConditions(tenantId,
                                triggerId, conditions);
                        log.debugf("Conditions: %s", updatedConditions);
                        future.complete(updatedConditions);
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void setConditions(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    String triggerId = routing.request().getParam("triggerId");
                    String triggerMode = routing.request().getParam("triggerMode");
                    Collection<Condition> conditions;
                    try {
                        conditions = collectionFromJson(json, Condition.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing Condition json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
                    Mode mode = Mode.valueOf(triggerMode.toUpperCase());
                    for (Condition condition : conditions) {
                        condition.setTriggerId(triggerId);
                        if (condition.getTriggerMode() == null || !condition.getTriggerMode().equals(mode)) {
                            throw new BadRequestException(
                                    "Condition: " + condition + " has a different triggerMode [" + triggerMode + "]");
                        }
                    }
                    try {
                        Collection<Condition> updatedConditions = definitionsService.setConditions(tenantId, triggerId,
                                mode, conditions);
                        log.debugf("Conditions: %s", updatedConditions);
                        future.complete(updatedConditions);
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    @SuppressWarnings("unchecked")
    void setGroupConditions(RoutingContext routing) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    String json = routing.getBodyAsString();
                    String groupId = routing.request().getParam("groupId");
                    String triggerMode = routing.request().getParam("triggerMode");
                    GroupConditionsInfo groupConditionsInfo;
                    try {
                        groupConditionsInfo = fromJson(json, GroupConditionsInfo.class);
                    } catch (Exception e) {
                        log.errorf("Error parsing GroupConditionsInfo json: %s. Reason: %s", json, e.toString());
                        throw new BadRequestException(e.toString());
                    }
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
                        future.complete(updatedConditions);
                        return;
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
                        future.complete(updatedConditions);
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    void setTriggersEnabled(RoutingContext routing, boolean isGroup) {
        routing.vertx()
                .executeBlocking(future -> {
                    String tenantId = checkTenant(routing);
                    try {
                        String triggerIds = null;
                        Boolean enabled = null;
                        if (routing.request().params().get(PARAM_TRIGGER_IDS) != null) {
                            triggerIds = routing.request().params().get(PARAM_TRIGGER_IDS);
                        }
                        if (routing.request().params().get(PARAM_ENABLED) != null) {
                            enabled = Boolean.valueOf(routing.request().params().get(PARAM_ENABLED));
                        }
                        if (isEmpty(triggerIds)) {
                            throw new BadRequestException("TriggerIds must be non empty.");
                        }
                        if (null == enabled) {
                            throw new BadRequestException("Enabled must be non-empty.");
                        }
                        if (isGroup) {
                            definitionsService.updateGroupTriggerEnablement(tenantId, triggerIds, enabled);
                        } else {
                            definitionsService.updateTriggerEnablement(tenantId, triggerIds, enabled);
                        }
                        future.complete();
                    } catch (NotFoundException e) {
                        throw new ResponseUtil.NotFoundException(e.getMessage());
                    } catch (IllegalArgumentException e) {
                        throw new BadRequestException("Bad arguments: " + e.getMessage());
                    } catch (Exception e) {
                        log.debug(e.getMessage(), e);
                        throw new InternalServerException(e.toString());
                    }
                }, res -> result(routing, res));
    }

    TriggersCriteria buildCriteria(MultiMap params) {
        String triggerIds = null;
        String tags = null;
        boolean thin = false;

        if (params.get(PARAM_TRIGGER_IDS) != null) {
            triggerIds = params.get(PARAM_TRIGGER_IDS);
        }
        if (params.get(PARAM_TAGS) != null) {
            tags = params.get(PARAM_TAGS);
        }
        if (params.get(PARAM_THIN) != null) {
            thin = Boolean.valueOf(params.get(PARAM_THIN));
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
