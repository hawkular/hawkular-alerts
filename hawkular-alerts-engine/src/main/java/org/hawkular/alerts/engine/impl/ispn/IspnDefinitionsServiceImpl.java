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
package org.hawkular.alerts.engine.impl.ispn;

import static org.hawkular.alerts.api.services.DefinitionsEvent.Type.ACTION_DEFINITION_CREATE;
import static org.hawkular.alerts.api.services.DefinitionsEvent.Type.ACTION_DEFINITION_REMOVE;
import static org.hawkular.alerts.api.services.DefinitionsEvent.Type.ACTION_DEFINITION_UPDATE;
import static org.hawkular.alerts.api.util.Util.isEmpty;
import static org.hawkular.alerts.engine.impl.ispn.IspnPk.pk;
import static org.hawkular.alerts.engine.impl.ispn.IspnPk.pkFromDampeningId;
import static org.hawkular.alerts.engine.impl.ispn.IspnPk.pkFromTriggerId;
import static org.hawkular.alerts.engine.util.Utils.checkTenantId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;
import javax.ejb.Local;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import org.hawkular.alerts.api.json.GroupMemberInfo;
import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.EventCondition;
import org.hawkular.alerts.api.model.condition.ExternalCondition;
import org.hawkular.alerts.api.model.condition.MissingCondition;
import org.hawkular.alerts.api.model.condition.NelsonCondition;
import org.hawkular.alerts.api.model.condition.RateCondition;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.export.Definitions;
import org.hawkular.alerts.api.model.export.ImportType;
import org.hawkular.alerts.api.model.paging.Order;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.model.paging.TriggerComparator;
import org.hawkular.alerts.api.model.trigger.FullTrigger;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.TriggerType;
import org.hawkular.alerts.api.services.DefinitionsEvent;
import org.hawkular.alerts.api.services.DefinitionsEvent.Type;
import org.hawkular.alerts.api.services.DefinitionsListener;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.DistributedListener;
import org.hawkular.alerts.api.services.PropertiesService;
import org.hawkular.alerts.api.services.TriggersCriteria;
import org.hawkular.alerts.cache.IspnCacheManager;
import org.hawkular.alerts.engine.exception.FoundApplicationException;
import org.hawkular.alerts.engine.exception.NotFoundApplicationException;
import org.hawkular.alerts.engine.impl.AlertsContext;
import org.hawkular.alerts.engine.impl.ispn.model.IspnActionDefinition;
import org.hawkular.alerts.engine.impl.ispn.model.IspnActionPlugin;
import org.hawkular.alerts.engine.impl.ispn.model.IspnCondition;
import org.hawkular.alerts.engine.impl.ispn.model.IspnDampening;
import org.hawkular.alerts.engine.impl.ispn.model.IspnTrigger;
import org.hawkular.alerts.engine.impl.ispn.model.TagsBridge;
import org.hawkular.alerts.engine.log.MsgLogger;
import org.hawkular.alerts.engine.service.AlertsEngine;
import org.infinispan.Cache;
import org.infinispan.query.Search;
import org.infinispan.query.dsl.FilterConditionContext;
import org.infinispan.query.dsl.QueryBuilder;
import org.infinispan.query.dsl.QueryFactory;
import org.jboss.logging.Logger;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@Local(DefinitionsService.class)
@Stateless
@TransactionAttribute(value = TransactionAttributeType.NOT_SUPPORTED)
public class IspnDefinitionsServiceImpl implements DefinitionsService {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(IspnDefinitionsServiceImpl.class);

    @EJB
    AlertsEngine alertsEngine;

    @EJB
    AlertsContext alertsContext;

    @EJB
    PropertiesService properties;

    Cache<String, Object> backend;

    QueryFactory queryFactory;

    private List<DefinitionsEvent> deferredNotifications = new ArrayList<>();
    private int deferNotificationsCount = 0;

    @PostConstruct
    public void init() {
        backend = IspnCacheManager.getCacheManager().getCache("backend");
        if (backend == null) {
            log.error("Ispn backend cache not found. Check configuration.");
            throw new RuntimeException("backend cache not found");
        }
        queryFactory = Search.getQueryFactory(backend);
    }

    public void setAlertsEngine(AlertsEngine alertsEngine) {
        this.alertsEngine = alertsEngine;
    }

    public void setAlertsContext(AlertsContext alertsContext) {
        this.alertsContext = alertsContext;
    }

    public void setProperties(PropertiesService properties) {
        this.properties = properties;
    }

    @Override
    public void addActionDefinition(String tenantId, ActionDefinition actionDefinition) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (actionDefinition == null) {
            throw new IllegalArgumentException("ActionDefinition must be not null");
        }
        actionDefinition.setTenantId(tenantId);
        if (isEmpty(actionDefinition.getActionPlugin())) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
        }
        if (isEmpty(actionDefinition.getActionId())) {
            throw new IllegalArgumentException("ActionId must be not null");
        }
        if (isEmpty(actionDefinition.getProperties())) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        String plugin = actionDefinition.getActionPlugin();
        if (!getActionPlugins().contains(plugin)) {
            throw new IllegalArgumentException("Plugin: " + plugin + " is not deployed");
        }
        Set<String> pluginProperties = getActionPlugin(plugin);
        for (String property : actionDefinition.getProperties().keySet()) {
            boolean isPluginProperty = false;
            for (String pluginProperty : pluginProperties) {
                if (property.startsWith(pluginProperty)) {
                    isPluginProperty = true;
                    break;
                }
            }
            if (!isPluginProperty) {
                throw new IllegalArgumentException("Property: " + property + " is not valid on plugin: " +
                        actionDefinition.getActionPlugin());
            }
        }
        String pk = pk(actionDefinition);
        IspnActionDefinition found = (IspnActionDefinition) backend.get(pk);
        if (found != null) {
            throw new FoundApplicationException(pk);
        }
        backend.put(pk(actionDefinition), new IspnActionDefinition(actionDefinition));

        notifyListeners(new DefinitionsEvent(ACTION_DEFINITION_CREATE, actionDefinition));
    }

    @Override
    public void addTrigger(String tenantId, Trigger trigger) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(trigger)) {
            throw new IllegalArgumentException("Trigger must be not null");
        }

        checkTenantId(tenantId, trigger);
        trigger.setType(TriggerType.STANDARD);

        addTrigger(trigger);
    }

    @Override
    public void addGroupTrigger(String tenantId, Trigger groupTrigger) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(groupTrigger)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        checkTenantId(tenantId, groupTrigger);
        if (!groupTrigger.isGroup()) {
            groupTrigger.setType(TriggerType.GROUP);
        }

        addTrigger(groupTrigger);
    }

    @Override
    public Trigger addMemberTrigger(String tenantId, String groupId, String memberId, String memberName,
            String memberDescription, Map<String, String> memberContext, Map<String, String> memberTags,
            Map<String, String> dataIdMap) throws Exception {

        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(groupId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (isEmpty(dataIdMap)) {
            throw new IllegalArgumentException("DataIdMap must be not null");
        }

        try {
            deferNotifications();

            // fetch the group trigger (or throw NotFoundApplicationException)
            Trigger group = getTrigger(tenantId, groupId);

            // fetch the group conditions
            // ensure we have a 1-1 mapping for the dataId substitution
            Set<String> dataIdTokens = new HashSet<>();
            Collection<Condition> conditions = getTriggerConditions(tenantId, groupId, null);
            for (Condition c : conditions) {
                if (Condition.Type.COMPARE == c.getType()) {
                    dataIdTokens.add(c.getDataId());
                    dataIdTokens.add(((CompareCondition) c).getData2Id());
                } else {
                    dataIdTokens.add(c.getDataId());
                }
            }
            if (!dataIdTokens.equals(dataIdMap.keySet())) {
                throw new IllegalArgumentException(
                        "DataIdMap must contain the exact dataIds (keyset) expected by the condition set. Expected: "
                                + dataIdTokens + ", dataIdMap: " + dataIdMap.keySet());
            }

            // create a member trigger like the group trigger
            memberId = isEmpty(memberId) ? Trigger.generateId() : memberId;
            memberName = isEmpty(memberName) ? group.getName() : memberName;
            Trigger member = new Trigger(tenantId, memberId, memberName);

            copyGroupTrigger(group, member, true);

            if (!isEmpty(memberDescription)) {
                member.setDescription(memberDescription);
            }
            if (null != memberContext) {
                // add additional or override existing context
                Map<String, String> combinedContext = new HashMap<>();
                combinedContext.putAll(member.getContext());
                combinedContext.putAll(memberContext);
                member.setContext(combinedContext);
            }
            if (null != memberTags) {
                // add additional or override existing tags
                Map<String, String> combinedTags = new HashMap<>();
                combinedTags.putAll(member.getTags());
                combinedTags.putAll(memberTags);
                member.setTags(combinedTags);
            }

            // store the dataIdMap so that it can be used for future condition updates (where the mappings are unchanged)
            member.setDataIdMap(dataIdMap);

            addTrigger(member);

            List<Condition> memberConditions = conditions.stream()
                    .map(c -> getMemberCondition(member, c, dataIdMap))
                    .collect(Collectors.toList());
            setAllConditions(tenantId, memberId, memberConditions);

            // add any dampening
            Collection<Dampening> dampenings = getTriggerDampenings(tenantId, groupId, null);

            for (Dampening d : dampenings) {
                Dampening newDampening = new Dampening(member.getTenantId(), member.getId(), d.getTriggerMode(),
                        d.getType(), d.getEvalTrueSetting(), d.getEvalTotalSetting(), d.getEvalTimeSetting());
                addDampening(newDampening);
            }

            return member;

        } finally {
            releaseNotifications();
        }
    }

    @Override
    public Trigger addDataDrivenMemberTrigger(String tenantId, String groupId, String source) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(groupId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (isEmpty(source)) {
            throw new IllegalArgumentException("source must be not null");
        }
        if (Data.SOURCE_NONE.equals(source)) {
            throw new IllegalArgumentException("source is required (can not be none)");
        }

        try {
            deferNotifications();

            // fetch the group trigger (or throw NotFoundApplicationException)
            Trigger group = getTrigger(tenantId, groupId);

            // fetch the group conditions and generate a dataIdMap that just uses the same tokens as found in the
            // group conditions. That is what we want in this use case, the source provides the differentiator
            Map<String, String> dataIdMap = new HashMap<>();
            Collection<Condition> conditions = getTriggerConditions(tenantId, groupId, null);
            for (Condition c : conditions) {
                dataIdMap.put(c.getDataId(), c.getDataId());
                if (Condition.Type.COMPARE == c.getType()) {
                    dataIdMap.put(((CompareCondition) c).getData2Id(), ((CompareCondition) c).getData2Id());
                }
            }

            // create a member trigger like the group trigger
            String memberId = group.getId() + "_" + source;
            Trigger member = new Trigger(tenantId, memberId, group.getName());

            copyGroupTrigger(group, member, true);
            member.setSource(source);
            // add source tag (not sure if we really need this)
            member.getTags().put("source", source);

            addTrigger(member);

            // add any conditions
            List<Condition> memberConditions = conditions.stream()
                    .map(c -> getMemberCondition(member, c, dataIdMap))
                    .collect(Collectors.toList());
            setAllConditions(tenantId, memberId, memberConditions);

            // add any dampening
            Collection<Dampening> dampenings = getTriggerDampenings(tenantId, groupId, null);

            for (Dampening d : dampenings) {
                Dampening newDampening = new Dampening(member.getTenantId(), member.getId(), d.getTriggerMode(),
                        d.getType(), d.getEvalTrueSetting(), d.getEvalTotalSetting(), d.getEvalTimeSetting());
                addDampening(newDampening);
            }

            return member;

        } finally {
            releaseNotifications();
        }
    }

    @Override
    public void removeTrigger(String tenantId, String triggerId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        // fetch the trigger (or throw NotFoundApplicationException)
        Trigger doomedTrigger = getTrigger(tenantId, triggerId);
        if (doomedTrigger.isGroup()) {
            throw new IllegalArgumentException("Trigger [" + tenantId + "/" + triggerId + "] is a group trigger.");
        }

        removeTrigger(doomedTrigger);
    }

    @Override
    public void removeGroupTrigger(String tenantId, String groupId, boolean keepNonOrphans, boolean keepOrphans)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(groupId)) {
            throw new IllegalArgumentException("GroupId must be not null");
        }

        try {
            deferNotifications();

            // fetch the trigger (or throw NotFoundApplicationException)
            Trigger doomedTrigger = getTrigger(tenantId, groupId);
            if (!doomedTrigger.isGroup()) {
                throw new IllegalArgumentException(
                        "Trigger [" + tenantId + "/" + groupId + "] is not a group trigger");
            }

            Collection<Trigger> memberTriggers = getMemberTriggers(tenantId, groupId, true);

            for (Trigger member : memberTriggers) {
                if ((keepNonOrphans && !member.isOrphan()) || (keepOrphans && member.isOrphan())) {
                    member.setMemberOf(null);
                    member.setType(TriggerType.STANDARD);
                    updateTrigger(member);
                    continue;
                }

                removeTrigger(member);
            }

            removeTrigger(doomedTrigger);

        } finally {
            releaseNotifications();
        }
    }

    @Override
    public void updateFullTrigger(String tenantId, FullTrigger fullTrigger) throws Exception {
        if (null == fullTrigger) {
            throw new IllegalArgumentException("FullTrigger must be not null");
        }
        Trigger trigger = fullTrigger.getTrigger();
        if (null == trigger) {
            throw new IllegalArgumentException("FullTrigger.Trigger must be not null");
        }
        TriggerType type = trigger.getType();
        if (TriggerType.MEMBER == type) {
            throw new IllegalArgumentException("FullTrigger.Trigger is type MEMBER and must be updated via the group");
        }

        checkTenantId(tenantId, trigger);
        String triggerId = trigger.getId();

        // fetch the trigger (or throw NotFoundApplicationException)
        FullTrigger existingFullTrigger = getFullTrigger(tenantId, triggerId);
        Trigger existingTrigger = existingFullTrigger.getTrigger();

        if (existingTrigger.getType() != type) {
            throw new IllegalArgumentException(
                    "It is not allowed to update trigger type. Current type: [" + existingTrigger.getType() + "]");
        }
        if (existingTrigger.isMember()) {
            if (!existingTrigger.getMemberOf().equals(trigger.getMemberOf())) {
                throw new IllegalArgumentException("A member trigger can not change groups.");
            }
            if (existingTrigger.isOrphan() != trigger.isOrphan()) {
                throw new IllegalArgumentException("Orphan status can not be changed by this method.");
            }
        }

        try {
            deferNotifications();

            // if changed then update the trigger definition
            if (!trigger.isSame(existingTrigger)) {
                log.debugf("Updating trigger definition from %s to %s", existingTrigger, trigger);
                if (trigger.isGroup()) {
                    updateGroupTrigger(tenantId, trigger);
                } else {
                    updateTrigger(trigger);
                }
            } else {
                log.debugf("Skipping trigger update, no difference between old %s and new %s", existingTrigger,
                        trigger);
            }

            // if changed then update the dampening definitions
            List<Dampening> dampenings = fullTrigger.getDampenings();
            List<Dampening> existingDampenings = existingFullTrigger.getDampenings();
            if (!isSameDampenings(dampenings, existingDampenings)) {
                log.debugf("Updating dampenings from %s to %s", existingDampenings, dampenings);
                if (trigger.isGroup()) {
                    for (Dampening d : existingDampenings) {
                        removeGroupDampening(tenantId, d.getDampeningId());
                    }
                    for (Dampening d : dampenings) {
                        addGroupDampening(tenantId, d);
                    }
                } else {
                    for (Dampening d : existingDampenings) {
                        removeDampening(tenantId, d.getDampeningId());
                    }
                    for (Dampening d : dampenings) {
                        addDampening(tenantId, d);
                    }
                }
            } else {
                log.debugf("Skipping dampening update, no difference between old %s and new %s", existingDampenings,
                        dampenings);
            }

            // if changed then update the condition set
            List<Condition> conditions = fullTrigger.getConditions();
            List<Condition> existingConditions = existingFullTrigger.getConditions();
            if (!isSameConditions(conditions, existingConditions)) {
                log.debugf("Updating conditions from %s to %s", existingConditions, conditions);
                List<Condition> firingConditions = conditions.stream()
                        .filter(c -> Mode.FIRING == c.getTriggerMode())
                        .collect(Collectors.toList());
                List<Condition> resolveConditions = conditions.stream()
                        .filter(c -> Mode.AUTORESOLVE == c.getTriggerMode())
                        .collect(Collectors.toList());
                if (trigger.isGroup()) {
                    setGroupConditions(tenantId, triggerId, Mode.FIRING, firingConditions, null);
                    setGroupConditions(tenantId, triggerId, Mode.AUTORESOLVE, resolveConditions, null);
                } else {
                    setConditions(tenantId, triggerId, Mode.FIRING, firingConditions);
                    setConditions(tenantId, triggerId, Mode.AUTORESOLVE, resolveConditions);
                }
            } else {
                log.debugf("Skipping condition update, no difference between old %s and new %s", existingConditions,
                        conditions);
            }
        } finally {
            releaseNotifications();
        }
    }

    private boolean isSameDampenings(List<Dampening> dampenings, List<Dampening> existingDampenings) {
        if (dampenings.size() != existingDampenings.size()) {
            return false;
        }
        Collections.sort(dampenings, (d1, d2) -> d1.getDampeningId().compareTo(d2.getDampeningId()));
        Collections.sort(existingDampenings, (d1, d2) -> d1.getDampeningId().compareTo(d2.getDampeningId()));
        for (int i = 0; i < dampenings.size(); ++i) {
            if (!dampenings.get(i).isSame(existingDampenings.get(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isSameConditions(List<Condition> conditions, List<Condition> existingConditions) {
        if (conditions.size() != existingConditions.size()) {
            return false;
        }
        Collections.sort(conditions, (d1, d2) -> d1.getConditionId().compareTo(d2.getConditionId()));
        Collections.sort(existingConditions, (d1, d2) -> d1.getConditionId().compareTo(d2.getConditionId()));
        for (int i = 0; i < conditions.size(); ++i) {
            if (!conditions.get(i).isSame(existingConditions.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Trigger updateTrigger(String tenantId, Trigger trigger) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(trigger)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        checkTenantId(tenantId, trigger);
        String triggerId = trigger.getId();

        // fetch the trigger (or throw NotFoundApplicationException)
        Trigger existingTrigger = getTrigger(tenantId, trigger.getId());
        if (existingTrigger.isGroup()) {
            throw new IllegalArgumentException("Trigger [" + tenantId + "/" + triggerId + "] is a group trigger.");
        }
        if (existingTrigger.isMember()) {
            if (!existingTrigger.isOrphan()) {
                throw new IllegalArgumentException("Trigger [" + tenantId + "/" + triggerId
                        + "] is a member trigger and must be updated via the group.");
            }
            if (!existingTrigger.getMemberOf().equals(trigger.getMemberOf())) {
                throw new IllegalArgumentException("A member trigger can not change groups.");
            }
            if (existingTrigger.isOrphan() != trigger.isOrphan()) {
                throw new IllegalArgumentException("Orphan status can not be changed by this method.");
            }
        }
        return updateTrigger(trigger);
    }

    @Override
    public Trigger updateGroupTrigger(String tenantId, Trigger groupTrigger) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(groupTrigger)) {
            throw new IllegalArgumentException("Trigger must be not null");
        }

        try {
            deferNotifications();

            checkTenantId(tenantId, groupTrigger);
            String groupId = groupTrigger.getId();

            // fetch the group trigger (or throw NotFoundApplicationException)
            Trigger existingGroupTrigger = getTrigger(tenantId, groupId);
            if (!existingGroupTrigger.isGroup()) {
                throw new IllegalArgumentException(
                        "Trigger [" + tenantId + "/" + groupId + "] is not a group trigger");
            }

            // trigger type can not be updated
            groupTrigger.setType(existingGroupTrigger.getType());

            Collection<Trigger> memberTriggers = getMemberTriggers(tenantId, groupId, false);

            for (Trigger member : memberTriggers) {
                copyGroupTrigger(groupTrigger, member, false);
                updateTrigger(member);
            }

            return updateTrigger(groupTrigger);

        } finally {
            releaseNotifications();
        }
    }

    @Override
    public void updateGroupTriggerEnablement(String tenantId, String groupTriggerIds, boolean enabled)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(groupTriggerIds)) {
            throw new IllegalArgumentException("GroupTriggerIds must be not null");
        }

        Set<Trigger> filteredGroupTriggers = new HashSet<>();

        for (String groupTriggerId : groupTriggerIds.split(",")) {
            // fetch the group trigger (or throw NotFoundApplicationException)
            Trigger existingGroupTrigger = getTrigger(tenantId, groupTriggerId.trim());
            if (!existingGroupTrigger.isGroup()) {
                throw new IllegalArgumentException(
                        "Trigger [" + tenantId + "/" + groupTriggerId + "] is not a group trigger.");
            }

            if (enabled == existingGroupTrigger.isEnabled()) {
                log.debugf("Ignoring enable/disable request. Group Trigger %s is already set enabled=%s",
                        groupTriggerId, enabled);
                continue;
            }

            filteredGroupTriggers.add(existingGroupTrigger);
        }

        try {
            deferNotifications();

            for (Trigger groupTrigger : filteredGroupTriggers) {
                Collection<Trigger> memberTriggers = getMemberTriggers(tenantId, groupTrigger.getId(), false);

                updateTriggerEnablement(tenantId, memberTriggers, enabled);
                updateTriggerEnablement(tenantId, Collections.singleton(groupTrigger), enabled);
            }
        } finally {
            releaseNotifications();
        }
    }

    @Override
    public void updateTriggerEnablement(String tenantId, String triggerIds, boolean enabled) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerIds)) {
            throw new IllegalArgumentException("TriggerIds must be not null");
        }

        Set<Trigger> filteredTriggers = new HashSet<>();

        for (String triggerId : triggerIds.split(",")) {
            // fetch the trigger (or throw NotFoundApplicationException)
            Trigger existingTrigger = getTrigger(tenantId, triggerId.trim());
            if (existingTrigger.isGroup()) {
                throw new IllegalArgumentException("Trigger [" + tenantId + "/" + triggerId + "] is a group trigger.");
            }

            if (enabled == existingTrigger.isEnabled()) {
                log.debugf("Ignoring enable/disable request. Trigger %s is already set enabled=%s", triggerId,
                        enabled);
                continue;
            }

            filteredTriggers.add(existingTrigger);
        }

        updateTriggerEnablement(tenantId, filteredTriggers, enabled);
    }

    @Override
    public Trigger getTrigger(String tenantId, String triggerId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        String pk = pkFromTriggerId(tenantId, triggerId);
        IspnTrigger found = (IspnTrigger) backend.get(pk);
        if (found == null) {
            throw new NotFoundApplicationException(pk);
        }
        return found.getTrigger();
    }

    @Override
    public Page<Trigger> getTriggers(String tenantId, TriggersCriteria criteria, Pager pager) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        boolean filter = (null != criteria && criteria.hasCriteria());
        if (filter) {
            log.debugf("getTriggers criteria: %s", criteria);
        }

        List<IspnTrigger> triggers;
        if (filter) {
            StringBuilder query = new StringBuilder(
                    "from org.hawkular.alerts.engine.impl.ispn.model.IspnTrigger where ");
            query.append("tenantId = '").append(tenantId).append("' and ");
            if (criteria.hasTriggerIdCriteria()) {
                Set<String> triggerIds = filterByTriggers(criteria);
                query.append("(");
                Iterator<String> iter = triggerIds.iterator();
                while (iter.hasNext()) {
                    String triggerId = iter.next();
                    query.append("triggerId = '").append(triggerId).append("' ");
                    if (iter.hasNext()) {
                        query.append("or ");
                    }
                }
                query.append(") ");
                if (criteria.hasTagCriteria()) {
                    query.append("and ");
                }
            }
            if (criteria.hasTagCriteria()) {
                Map<String, String> tags = criteria.getTags();
                query.append("(");
                Iterator<Map.Entry<String, String>> iter = tags.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<String, String> tag = iter.next();
                    query.append("tags like '")
                            .append(tag.getKey())
                            .append(TagsBridge.VALUE)
                            .append(tag.getValue().equals("*") ? "%" : tag.getValue())
                            .append("' ");
                    if (iter.hasNext()) {
                        query.append("or ");
                    }
                }
                query.append(") ");
            }
            triggers = queryFactory.create(query.toString()).list();
        } else {
            triggers = queryFactory.from(IspnTrigger.class)
                    .having("tenantId")
                    .eq(tenantId)
                    .build()
                    .list();
        }
        return prepareTriggersPage(triggers.stream().map(t -> t.getTrigger()).collect(Collectors.toList()), pager);
    }

    @Override
    public Collection<Trigger> getMemberTriggers(String tenantId, String groupId, boolean includeOrphans)
            throws Exception {
        Collection<IspnTrigger> ispnTriggers = queryFactory.from(IspnTrigger.class)
                .having("tenantId").eq(tenantId).and()
                .having("memberOf").eq(groupId)
                .build()
                .list();
        return ispnTriggers.stream()
                .map(t -> t.getTrigger())
                .filter(t -> includeOrphans || TriggerType.MEMBER == t.getType())
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Trigger> getAllTriggers() throws Exception {
        List<IspnTrigger> triggers = queryFactory.from(IspnTrigger.class)
                .build()
                .list();
        return triggers.stream().map(t -> t.getTrigger()).collect(Collectors.toList());
    }

    @Override
    public Collection<Trigger> getAllTriggersByTag(String name, String value) throws Exception {
        if (isEmpty(name)) {
            throw new IllegalArgumentException("name must be not null");
        }
        if (isEmpty(value)) {
            throw new IllegalArgumentException("value must be not null (use '*' for all");
        }
        StringBuilder query = new StringBuilder(
                "from org.hawkular.alerts.engine.impl.ispn.model.IspnTrigger where tags like '")
                        .append(name)
                        .append(TagsBridge.VALUE)
                        .append(value.equals("*") ? "%" : value)
                        .append("'");
        List<IspnTrigger> triggers = queryFactory.create(query.toString()).list();
        return triggers.stream().map(t -> t.getTrigger()).collect(Collectors.toList());
    }

    @Override
    public Trigger orphanMemberTrigger(String tenantId, String memberId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(memberId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        // fetch the trigger (or throw NotFoundApplicationException)
        Trigger member = getTrigger(tenantId, memberId);
        if (!member.isMember()) {
            throw new IllegalArgumentException("Trigger is not a member trigger: [" + tenantId + "/" + memberId + "]");
        }
        if (member.isOrphan()) {
            throw new IllegalArgumentException("Trigger is already an orphan: [" + tenantId + "/" + memberId + "]");
        }

        member.setType(TriggerType.ORPHAN);

        return updateTrigger(member);
    }

    @Override
    public Trigger unorphanMemberTrigger(String tenantId, String memberId, Map<String, String> memberContext,
            Map<String, String> memberTags, Map<String, String> dataIdMap) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(memberId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        // fetch the trigger (or throw NotFoundApplicationException)
        Trigger orphanMember = getTrigger(tenantId, memberId);
        if (!orphanMember.isMember()) {
            throw new IllegalArgumentException("Trigger is not a member trigger: [" + tenantId + "/" + memberId + "]");
        }
        if (!orphanMember.isOrphan()) {
            throw new IllegalArgumentException("Trigger is not an orphan: [" + tenantId + "/" + memberId + "]");
        }

        String groupId = orphanMember.getMemberOf();
        String memberName = orphanMember.getName();
        String memberDescription = orphanMember.getDescription();

        removeTrigger(orphanMember);
        Trigger member = addMemberTrigger(tenantId, groupId, memberId, memberName, memberDescription, memberContext,
                memberTags, dataIdMap);

        return member;
    }

    @Override
    public Dampening addDampening(String tenantId, Dampening dampening) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(dampening)) {
            throw new IllegalArgumentException("Dampening, DampeningId and TriggerId must be not null");
        }

        checkTenantId(tenantId, dampening);

        String triggerId = dampening.getTriggerId();
        // fetch the trigger (or throw NotFoundApplicationException)
        Trigger trigger = getTrigger(tenantId, triggerId);
        if (trigger.isGroup()) {
            throw new IllegalArgumentException("Trigger [" + tenantId + "/" + triggerId + "] is a group trigger.");
        }
        if (trigger.isMember() && !trigger.isOrphan()) {
            throw new IllegalArgumentException("Trigger [" + tenantId + "/" + triggerId
                    + "] is a member trigger and must be managed via the group.");
        }

        return addDampening(dampening);
    }

    @Override
    public Dampening addGroupDampening(String tenantId, Dampening groupDampening) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(groupDampening)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        try {
            deferNotifications();

            checkTenantId(tenantId, groupDampening);

            String groupId = groupDampening.getTriggerId();
            // fetch the group trigger (or throw NotFoundApplicationException)
            Trigger groupTrigger = getTrigger(tenantId, groupId);
            if (!groupTrigger.isGroup()) {
                throw new IllegalArgumentException(
                        "Trigger [" + tenantId + "/" + groupId + "] is not a group trigger.");
            }

            Collection<Trigger> memberTriggers = getMemberTriggers(tenantId, groupId, false);

            for (Trigger member : memberTriggers) {
                groupDampening.setTriggerId(member.getId());
                addDampening(groupDampening);
            }

            groupDampening.setTriggerId(groupTrigger.getId());
            return addDampening(groupDampening);

        } finally {
            releaseNotifications();
        }
    }

    @Override
    public void removeDampening(String tenantId, String dampeningId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(dampeningId)) {
            throw new IllegalArgumentException("dampeningId must be not null");
        }

        Dampening dampening = null;
        try {
            dampening = getDampening(tenantId, dampeningId);
        } catch (NotFoundApplicationException e) {
            log.debugf("Ignoring removeDampening(%s), the Dampening does not exist.", dampeningId);
            return;
        }

        String triggerId = dampening.getTriggerId();
        // fetch the trigger (or throw NotFoundApplicationException)
        Trigger trigger = getTrigger(tenantId, triggerId);
        if (trigger.isGroup()) {
            throw new IllegalArgumentException("Trigger [" + tenantId + "/" + triggerId + "] is a group trigger.");
        }
        if (trigger.isMember() && !trigger.isOrphan()) {
            throw new IllegalArgumentException("Trigger [" + tenantId + "/" + triggerId
                    + "] is a member trigger and must be managed via the group.");
        }

        removeDampening(dampening);
    }

    @Override
    public void removeGroupDampening(String tenantId, String groupDampeningId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(groupDampeningId)) {
            throw new IllegalArgumentException("dampeningId must be not null");
        }

        try {
            deferNotifications();

            Dampening groupDampening = null;
            try {
                groupDampening = getDampening(tenantId, groupDampeningId);
            } catch (NotFoundApplicationException e) {
                log.debugf("Ignoring removeDampening(%s), the Dampening does not exist.", groupDampeningId);
                return;
            }

            String groupId = groupDampening.getTriggerId();
            // fetch the trigger (or throw NotFoundApplicationException)
            Trigger groupTrigger = getTrigger(tenantId, groupId);
            if (!groupTrigger.isGroup()) {
                throw new IllegalArgumentException(
                        "Trigger [" + tenantId + "/" + groupId + "] is not a group trigger.");
            }

            Collection<Trigger> memberTriggers = getMemberTriggers(tenantId, groupId, false);

            for (Trigger member : memberTriggers) {
                Collection<Dampening> dampenings = getTriggerDampenings(tenantId, member.getId(),
                        groupDampening.getTriggerMode());
                if (dampenings.isEmpty()) {
                    continue;
                }
                removeDampening(dampenings.iterator().next());
            }

            removeDampening(groupDampening);

        } finally {
            releaseNotifications();
        }
    }

    @Override
    public Dampening updateDampening(String tenantId, Dampening dampening) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(dampening)) {
            throw new IllegalArgumentException("Dampening, DampeningId and TriggerId must be not null");
        }

        checkTenantId(tenantId, dampening);

        String triggerId = dampening.getTriggerId();
        // fetch the trigger (or throw NotFoundApplicationException)
        Trigger trigger = getTrigger(tenantId, triggerId);
        if (trigger.isGroup()) {
            throw new IllegalArgumentException("Trigger [" + tenantId + "/" + triggerId + "] is a group trigger.");
        }
        if (trigger.isMember() && !trigger.isOrphan()) {
            throw new IllegalArgumentException("Trigger [" + tenantId + "/" + triggerId
                    + "] is a member trigger and must be managed via the group.");
        }

        return updateDampening(dampening);
    }

    @Override
    public Dampening updateGroupDampening(String tenantId, Dampening groupDampening) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(groupDampening)) {
            throw new IllegalArgumentException("DampeningId and TriggerId must be not null");
        }

        try {
            deferNotifications();

            checkTenantId(tenantId, groupDampening);

            String groupId = groupDampening.getTriggerId();
            // fetch the group trigger (or throw NotFoundApplicationException)
            Trigger groupTrigger = getTrigger(tenantId, groupId);
            if (!groupTrigger.isGroup()) {
                throw new IllegalArgumentException(
                        "Trigger [" + tenantId + "/" + groupId + "] is not a group trigger.");
            }

            Collection<Trigger> memberTriggers = getMemberTriggers(tenantId, groupId, false);

            for (Trigger member : memberTriggers) {
                groupDampening.setTriggerId(member.getId());
                updateDampening(groupDampening);
            }

            groupDampening.setTriggerId(groupTrigger.getId());
            return updateDampening(groupDampening);

        } finally {
            releaseNotifications();
        }
    }

    @Override
    public Dampening getDampening(String tenantId, String dampeningId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(dampeningId)) {
            throw new IllegalArgumentException("DampeningId must be not null");
        }

        String pk = pkFromDampeningId(dampeningId);
        IspnDampening found = (IspnDampening) backend.get(pk);
        if (found == null) {
            throw new NotFoundApplicationException(pk);
        }
        return found.getDampening();
    }

    @Override
    public Collection<Dampening> getAllDampenings() throws Exception {
        return mapDampenings(queryFactory.from(IspnDampening.class).build().list());
    }

    @Override
    public Collection<Dampening> getDampenings(String tenantId) throws Exception {
        return mapDampenings(queryFactory.from(IspnDampening.class)
                .having("tenantId").eq(tenantId)
                .build()
                .list());
    }

    @Override
    public Collection<Dampening> getTriggerDampenings(String tenantId, String triggerId, Mode triggerMode)
            throws Exception {
        FilterConditionContext qb = queryFactory.from(IspnDampening.class)
                .having("tenantId").eq(tenantId).and()
                .having("triggerId").eq(triggerId);
        if (null != triggerMode) {
            qb = qb.and().having("triggerMode").eq(triggerMode.name());
        }
        return mapDampenings(((QueryBuilder) qb).build().list());
    }

    @Override
    public Collection<Condition> setConditions(String tenantId, String triggerId, Mode triggerMode,
            Collection<Condition> conditions) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (triggerMode == null) {
            throw new IllegalArgumentException("TriggerMode must be not null");
        }
        if (conditions == null) {
            throw new IllegalArgumentException("Conditions must be not null");
        }

        conditions.stream().forEach(c -> {
            c.setTenantId(tenantId);
            c.setTriggerId(triggerId);
            c.setTriggerMode(triggerMode);
        });

        // We keep a cache of the dataIds used in Trigger conditions. They are used to filter incoming data, keeping
        // only the data for relevant dataIds.  This method supplies/updates only the conditions of one trigger mode.
        // To ensure we maintain all of the trigger's dataIds, we must update all conditions, so fetch any conditions
        // for the other trigger mode and then update all of the conditions together. This does not increase overhead
        // too much as the entire trigger must get reloaded regardless.
        Mode otherTriggerMode = triggerMode == Mode.FIRING ? Mode.AUTORESOLVE : Mode.FIRING;
        Collection<Condition> otherConditions = getTriggerConditions(tenantId, triggerId, otherTriggerMode);

        Collection<Condition> allConditions = new ArrayList<>(conditions);
        allConditions.addAll(otherConditions);

        allConditions = setAllConditions(tenantId, triggerId, allConditions);

        return allConditions.stream()
                .filter(c -> c.getTriggerMode().equals(triggerMode))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Condition> setAllConditions(String tenantId, String triggerId,
            Collection<Condition> conditions) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (conditions == null) {
            throw new IllegalArgumentException("Conditions must be not null");
        }
        conditions.stream().forEach(c -> {
            if (null == c.getTriggerMode()) {
                throw new IllegalArgumentException("Condition.triggerMode must not be null");
            }
        });

        conditions.stream().forEach(c -> {
            c.setTenantId(tenantId);
            c.setTriggerId(triggerId);
        });

        Collection<Condition> updatedConditions = new HashSet<>();
        Set<String> dataIds = new HashSet<>();

        Collection<Condition> firingConditions = conditions.stream()
                .filter(c -> c.getTriggerMode() == null || c.getTriggerMode().equals(Mode.FIRING))
                .collect(Collectors.toList());
        updatedConditions.addAll(setConditions(tenantId, triggerId, Mode.FIRING, firingConditions, dataIds));

        Collection<Condition> autoResolveConditions = conditions.stream()
                .filter(c -> c.getTriggerMode().equals(Mode.AUTORESOLVE))
                .collect(Collectors.toList());
        updatedConditions.addAll(setConditions(tenantId, triggerId, Mode.AUTORESOLVE, autoResolveConditions, dataIds));

        if (alertsEngine != null) {
            alertsEngine.reloadTrigger(tenantId, triggerId);
        }

        notifyListeners(new DefinitionsEvent(Type.TRIGGER_CONDITION_CHANGE, tenantId, triggerId, dataIds));

        return updatedConditions;
    }

    @Override
    public Collection<Condition> setGroupConditions(String tenantId, String groupId, Mode triggerMode,
            Collection<Condition> groupConditions, Map<String, Map<String, String>> dataIdMemberMap) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(groupId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (triggerMode == null) {
            throw new IllegalArgumentException("TriggerMode must be not null");
        }
        if (groupConditions == null) {
            throw new IllegalArgumentException("GroupConditions must be not null");
        }

        try {
            deferNotifications();

            // fetch the group trigger (or throw NotFoundApplicationException)
            Trigger group = getTrigger(tenantId, groupId);
            if (!group.isGroup()) {
                throw new IllegalArgumentException(
                        "Trigger [" + tenantId + "/" + groupId + "] is not a group trigger.");
            }

            Collection<Trigger> memberTriggers = getMemberTriggers(tenantId, groupId, false);

            // for data-driven groups a change to group conditions invalidates the previously generated members
            // Note: if the new set of conditions uses the same set of dataIds we probably don't need to invalidate
            // the current members but the work of maintaining them may not add much, if any, benefit.
            if (TriggerType.DATA_DRIVEN_GROUP == group.getType()) {
                for (Trigger member : memberTriggers) {
                    removeTrigger(member);
                }
                memberTriggers.clear();
            }

            if (!memberTriggers.isEmpty()) {
                // fill in dataIdMap entries not supplied using the most recently supplied mapping
                if (null == dataIdMemberMap) {
                    dataIdMemberMap = new HashMap<>();
                }
                for (Trigger member : memberTriggers) {
                    String memberId = member.getId();

                    for (Entry<String, String> entry : member.getDataIdMap().entrySet()) {
                        String groupDataId = entry.getKey();
                        String memberDataId = entry.getValue();

                        Map<String, String> memberIdMap = dataIdMemberMap.get(groupDataId);
                        if (null == memberIdMap) {
                            memberIdMap = new HashMap<>(memberTriggers.size());
                            dataIdMemberMap.put(groupDataId, memberIdMap);
                        }
                        if (memberIdMap.containsKey(memberId)) {
                            // supplied mapping has a mapping for this groupDataId and member. If it is
                            // a new mapping for this member then update the member's dataIdMap
                            if (!memberIdMap.get(memberId).equals(member.getDataIdMap().get(groupDataId))) {
                                Map<String, String> updatedDataIdMap = new HashMap<>(member.getDataIdMap());
                                updatedDataIdMap.put(groupDataId, memberIdMap.get(memberId));
                                updateMemberTriggerDataIdMap(tenantId, memberId, updatedDataIdMap);
                            }
                        } else {
                            // supplied map did not have the previously stored mapping, use the existing mapping
                            memberIdMap.put(memberId, memberDataId);
                        }
                    }
                }

                // validate the dataIdMemberMap
                for (Condition groupCondition : groupConditions) {
                    if (!dataIdMemberMap.containsKey(groupCondition.getDataId())) {
                        throw new IllegalArgumentException("Missing dataIdMap entry for dataId token ["
                                + groupCondition.getDataId() + "]");
                    }
                    if (Condition.Type.COMPARE == groupCondition.getType()) {
                        CompareCondition cc = (CompareCondition) groupCondition;
                        if (!dataIdMemberMap.containsKey(cc.getData2Id())) {
                            throw new IllegalArgumentException(
                                    "Missing dataIdMap entry for CompareCondition data2Id token ["
                                            + cc.getData2Id() + "]");
                        }
                    }
                    for (Entry<String, Map<String, String>> entry : dataIdMemberMap.entrySet()) {
                        String dataId = entry.getKey();
                        Map<String, String> memberMap = entry.getValue();
                        if (memberMap.size() != memberTriggers.size()) {
                            throw new IllegalArgumentException("memberMap size [" + memberMap.size() + "] for dataId ["
                                    + dataId + "] must equal number of member triggers [" + memberTriggers.size()
                                    + "]");
                        }
                        for (Trigger member : memberTriggers) {
                            String value = memberMap.get(member.getId());
                            if (isEmpty(value)) {
                                throw new IllegalArgumentException(
                                        "Invalid mapping. DataId=[" + dataId + "], Member=[" + member.getId()
                                                + "], value=[" + value + "]");
                            }
                        }
                    }
                }
            }

            // ensure conditions are set properly
            for (Condition groupCondition : groupConditions) {
                groupCondition.setTenantId(group.getTenantId());
                groupCondition.setTriggerId(group.getId());
                groupCondition.setTriggerMode(triggerMode);
            }

            // set conditions on the members
            Map<String, String> dataIdMap = new HashMap<>();
            Collection<Condition> memberConditions = new ArrayList<>(groupConditions.size());
            for (Trigger member : memberTriggers) {
                dataIdMap.clear();
                memberConditions.clear();
                for (Entry<String, Map<String, String>> entry : dataIdMemberMap.entrySet()) {
                    dataIdMap.put(entry.getKey(), entry.getValue().get(member.getId()));
                }

                for (Condition groupCondition : groupConditions) {
                    Condition memberCondition = getMemberCondition(member, groupCondition, dataIdMap);
                    memberConditions.add(memberCondition);
                }
                Collection<Condition> memberConditionSet = setConditions(tenantId, member.getId(), triggerMode,
                        memberConditions);
                if (log.isDebugEnabled()) {
                    log.debug("Member condition set: " + memberConditionSet);
                }
            }

            // set conditions on the group trigger
            return setConditions(tenantId, groupId, triggerMode, groupConditions);

        } finally {
            releaseNotifications();
        }

    }

    @Override
    public Collection<Condition> getAllConditions() throws Exception {
        return mapConditions(queryFactory.from(IspnCondition.class).build().list());
    }

    @Override
    public Collection<Condition> getConditions(String tenantId) throws Exception {
        return mapConditions(queryFactory.from(IspnCondition.class)
                .having("tenantId").eq(tenantId)
                .build()
                .list());
    }

    @Override
    public Collection<Condition> getTriggerConditions(String tenantId, String triggerId, Mode triggerMode)
            throws Exception {
        FilterConditionContext qb = queryFactory.from(IspnCondition.class)
                .having("tenantId").eq(tenantId).and()
                .having("triggerId").eq(triggerId);
        if (null != triggerMode) {
            qb = qb.and().having("triggerMode").eq(triggerMode.name());
        }
        return mapConditions(((QueryBuilder) qb).build().list());
    }

    @Override
    public void addActionPlugin(String actionPlugin, Set<String> properties) throws Exception {
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("properties must be not null");
        }
        Map<String, String> defaultProperties = new HashMap<>();
        properties.stream().forEach(prop -> defaultProperties.put(prop, ""));
        addActionPlugin(actionPlugin, defaultProperties);
    }

    @Override
    public void addActionPlugin(String actionPlugin, Map<String, String> defaultProperties) throws Exception {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (defaultProperties == null || defaultProperties.isEmpty()) {
            throw new IllegalArgumentException("defaultProperties must be not null");
        }
        String pk = pk(actionPlugin);
        if (backend.get(pk) != null) {
            throw new FoundApplicationException(pk);
        }
        backend.put(pk, new IspnActionPlugin(actionPlugin, defaultProperties));
    }

    @Override
    public void removeActionPlugin(String actionPlugin) throws Exception {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        backend.remove(pk(actionPlugin));
    }

    @Override
    public void updateActionPlugin(String actionPlugin, Set<String> properties) throws Exception {
        if (properties == null || properties.isEmpty()) {
            throw new IllegalArgumentException("properties must be not null");
        }
        Map<String, String> updated = new HashMap<>();
        properties.stream().forEach(prop -> updated.put(prop, ""));
        updateActionPlugin(actionPlugin, updated);
    }

    @Override
    public void updateActionPlugin(String actionPlugin, Map<String, String> defaultProperties) throws Exception {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        if (defaultProperties == null || defaultProperties.isEmpty()) {
            throw new IllegalArgumentException("defaultProperties must be not null");
        }
        String pk = pk(actionPlugin);
        IspnActionPlugin found = (IspnActionPlugin) backend.get(pk);
        if (found == null) {
            throw new NotFoundApplicationException(pk);
        }
        found.setDefaultProperties(defaultProperties);
        backend.put(pk, found);
    }

    @Override
    public Collection<String> getActionPlugins() throws Exception {
        Set<String> pluginNames = new HashSet<>();
        List<IspnActionPlugin> plugins = queryFactory.from(IspnActionPlugin.class).build().list();
        for (IspnActionPlugin plugin : plugins) {
            pluginNames.add(plugin.getActionPlugin());
        }
        return pluginNames;
    }

    @Override
    public Set<String> getActionPlugin(String actionPlugin) throws Exception {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        IspnActionPlugin found = (IspnActionPlugin) backend.get(pk(actionPlugin));
        return found == null ? null : found.getDefaultProperties().keySet();
    }

    @Override
    public Map<String, String> getDefaultActionPlugin(String actionPlugin) throws Exception {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        IspnActionPlugin found = (IspnActionPlugin) backend.get(pk(actionPlugin));
        return found == null ? null : found.getDefaultProperties();
    }

    @Override
    public void removeActionDefinition(String tenantId, String actionPlugin, String actionId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
        }
        if (isEmpty(actionId)) {
            throw new IllegalArgumentException("ActionId must be not null");
        }
        backend.remove(pk(tenantId, actionPlugin, actionId));

        notifyListeners(new DefinitionsEvent(ACTION_DEFINITION_REMOVE, tenantId, actionPlugin, actionId));
    }

    @Override
    public void updateActionDefinition(String tenantId, ActionDefinition actionDefinition) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (actionDefinition == null) {
            throw new IllegalArgumentException("actionDefinition must be not null");
        }
        if (isEmpty(actionDefinition.getActionPlugin())) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
        }
        if (isEmpty(actionDefinition.getActionId())) {
            throw new IllegalArgumentException("ActionId must be not null");
        }
        if (isEmpty(actionDefinition.getProperties())) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        Set<String> pluginProperties = getActionPlugin(actionDefinition.getActionPlugin());
        for (String property : actionDefinition.getProperties().keySet()) {
            boolean isPluginProperty = false;
            for (String pluginProperty : pluginProperties) {
                if (property.startsWith(pluginProperty)) {
                    isPluginProperty = true;
                    break;
                }
            }
            if (!isPluginProperty) {
                throw new IllegalArgumentException("Property: " + property + " is not valid on plugin: " +
                        actionDefinition.getActionPlugin());
            }
        }
        String pk = pk(actionDefinition);
        IspnActionDefinition found = (IspnActionDefinition) backend.get(pk);
        if (found == null) {
            throw new NotFoundApplicationException(pk);
        }
        backend.put(pk, new IspnActionDefinition(actionDefinition));

        notifyListeners(new DefinitionsEvent(ACTION_DEFINITION_UPDATE, actionDefinition));
    }

    @Override
    public Map<String, Map<String, Set<String>>> getAllActionDefinitionIds() throws Exception {
        Map<String, Map<String, Set<String>>> actions = new HashMap<>();
        List<IspnActionDefinition> actionDefinitions = queryFactory.from(IspnActionDefinition.class).build().list();
        for (IspnActionDefinition action : actionDefinitions) {
            String tenantId = action.getTenantId();
            String actionPlugin = action.getActionPlugin();
            String actionId = action.getActionId();
            if (actions.get(tenantId) == null) {
                actions.put(tenantId, new HashMap<>());
            }
            if (actions.get(tenantId).get(actionPlugin) == null) {
                actions.get(tenantId).put(actionPlugin, new HashSet<>());
            }
            actions.get(tenantId).get(actionPlugin).add(actionId);
        }
        return actions;
    }

    @Override
    public Collection<ActionDefinition> getAllActionDefinitions() throws Exception {
        List<IspnActionDefinition> actionDefinitions = queryFactory.from(IspnActionDefinition.class).build().list();
        return actionDefinitions.stream().map(a -> a.getActionDefinition()).collect(Collectors.toList());
    }

    @Override
    public Map<String, Set<String>> getActionDefinitionIds(String tenantId) throws Exception {
        Map<String, Set<String>> actionIds = new HashMap<>();
        List<IspnActionDefinition> actionDefinitions = queryFactory.from(IspnActionDefinition.class)
                .having("tenantId")
                .eq(tenantId)
                .build()
                .list();
        for (IspnActionDefinition action : actionDefinitions) {
            String actionPlugin = action.getActionPlugin();
            String actionId = action.getActionId();
            if (actionIds.get(actionPlugin) == null) {
                actionIds.put(actionPlugin, new HashSet<>());
            }
            actionIds.get(actionPlugin).add(actionId);
        }
        return actionIds;
    }

    @Override
    public Collection<String> getActionDefinitionIds(String tenantId, String actionPlugin) throws Exception {
        Set<String> actionIds = new HashSet<>();
        List<IspnActionDefinition> actionDefinitions = queryFactory.from(IspnActionDefinition.class)
                .having("tenantId")
                .eq(tenantId)
                .and()
                .having("actionPlugin")
                .eq(actionPlugin)
                .build()
                .list();
        for (IspnActionDefinition action : actionDefinitions) {
            actionIds.add(action.getActionId());
        }
        return actionIds;
    }

    @Override
    public ActionDefinition getActionDefinition(String tenantId, String actionPlugin, String actionId)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
        }
        if (isEmpty(actionId)) {
            throw new IllegalArgumentException("actionId must be not null");
        }
        IspnActionDefinition actionDefinition = (IspnActionDefinition) backend
                .get(pk(tenantId, actionPlugin, actionId));
        return actionDefinition != null ? actionDefinition.getActionDefinition() : null;
    }

    @Override
    public void registerListener(DefinitionsListener listener, DefinitionsEvent.Type eventType,
            DefinitionsEvent.Type... eventTypes) {
        alertsContext.registerDefinitionListener(listener, eventType, eventTypes);
    }

    @Override
    public void registerDistributedListener(DistributedListener listener) {
        alertsContext.registerDistributedListener(listener);
    }

    @Override
    public Definitions exportDefinitions(String tenantId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        Definitions definitions = new Definitions();
        try {
            definitions.setTriggers(getFullTriggers(tenantId));
            definitions.setActions(getActionDefinitions(tenantId));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return definitions;
    }

    @Override
    public Definitions importDefinitions(String tenantId, Definitions definitions, ImportType strategy)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (null == definitions) {
            throw new IllegalArgumentException("Definitions must be not null");
        }
        if (null == strategy) {
            throw new IllegalArgumentException("ImportType startegy must be not null");
        }

        definitions.updateTenant(tenantId);
        Definitions imported = new Definitions();

        try {
            deferNotifications();

            Collection<Trigger> existingTriggers = getTriggers(tenantId, null, null);
            Map<String, Set<String>> existingActionDefinitions = getActionDefinitionIds(tenantId);
            if (strategy.equals(ImportType.DELETE)) {
                msgLog.warningDeleteDefinitionsTenant(tenantId);
                for (Trigger t : existingTriggers) {
                    removeTrigger(t);
                }
                for (Entry<String, Set<String>> entry : existingActionDefinitions.entrySet()) {
                    String actionPlugin = entry.getKey();
                    for (String actionId : entry.getValue()) {
                        removeActionDefinition(tenantId, actionPlugin, actionId);
                    }
                }
            }
            List<ActionDefinition> importedActionDefinitions = new ArrayList<>();
            if (!isEmpty(definitions.getActions())) {
                for (ActionDefinition a : definitions.getActions()) {
                    a.setTenantId(tenantId);
                    if (!isEmpty(a)) {
                        boolean existing = existingActionDefinitions.containsKey(a.getActionPlugin()) &&
                                existingActionDefinitions.get(a.getActionPlugin()).contains(a.getActionId());
                        switch (strategy) {
                            case DELETE:
                                addActionDefinition(tenantId, a);
                                importedActionDefinitions.add(a);
                                break;
                            case ALL:
                                if (existing) {
                                    removeActionDefinition(tenantId, a.getActionPlugin(), a.getActionId());
                                }
                                addActionDefinition(tenantId, a);
                                importedActionDefinitions.add(a);
                                break;
                            case NEW:
                                if (!existing) {
                                    addActionDefinition(tenantId, a);
                                    importedActionDefinitions.add(a);
                                }
                                break;
                            case OLD:
                                if (existing) {
                                    removeActionDefinition(tenantId, a.getActionPlugin(), a.getActionId());
                                    addActionDefinition(tenantId, a);
                                    importedActionDefinitions.add(a);
                                }
                                break;
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("ActionDefinition " + a + " is empty. Ignored on the import process");
                        }
                    }
                }
            }
            List<FullTrigger> importedTriggers = new ArrayList<>();
            if (!isEmpty(definitions.getTriggers())) {
                for (FullTrigger t : definitions.getTriggers()) {
                    if (!isEmpty(t.getTrigger())) {
                        boolean existing = existingTriggers.contains(t.getTrigger());
                        switch (strategy) {
                            case DELETE:
                                addFullTrigger(tenantId, t);
                                importedTriggers.add(t);
                                break;
                            case ALL:
                                if (existing) {
                                    removeTrigger(t.getTrigger());
                                }
                                addFullTrigger(tenantId, t);
                                importedTriggers.add(t);
                                break;
                            case NEW:
                                if (!existing) {
                                    addFullTrigger(tenantId, t);
                                    importedTriggers.add(t);
                                }
                                break;
                            case OLD:
                                if (existing) {
                                    removeTrigger(t.getTrigger());
                                    addFullTrigger(tenantId, t);
                                    importedTriggers.add(t);
                                }
                                break;
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Trigger " + t + " is empty. Ignored on the import process");
                        }
                    }
                }
            }
            List<GroupMemberInfo> importedMembersInfo = new ArrayList<>();
            if (!isEmpty(definitions.getGroupMembersInfo())) {
                for (GroupMemberInfo memberInfo : definitions.getGroupMembersInfo()) {
                    if (!isEmpty(memberInfo.getGroupId()) && !isEmpty(memberInfo.getMemberId())) {
                        boolean existing = false;
                        for (Trigger t : existingTriggers) {
                            if (t.getId().equals(memberInfo.getMemberId())) {
                                existing = true;
                                break;
                            }
                        }
                        switch (strategy) {
                            case DELETE:
                                addMemberTrigger(tenantId, memberInfo.getGroupId(), memberInfo.getMemberId(),
                                        memberInfo.getMemberName(), memberInfo.getMemberDescription(),
                                        memberInfo.getMemberContext(), memberInfo.getMemberTags(),
                                        memberInfo.getDataIdMap());
                                importedMembersInfo.add(memberInfo);
                                break;
                            case ALL:
                                if (existing) {
                                    removeTrigger(tenantId, memberInfo.getMemberId());
                                }
                                addMemberTrigger(tenantId, memberInfo.getGroupId(), memberInfo.getMemberId(),
                                        memberInfo.getMemberName(), memberInfo.getMemberDescription(),
                                        memberInfo.getMemberContext(), memberInfo.getMemberTags(),
                                        memberInfo.getDataIdMap());
                                importedMembersInfo.add(memberInfo);
                                break;
                            case NEW:
                                if (!existing) {
                                    addMemberTrigger(tenantId, memberInfo.getGroupId(), memberInfo.getMemberId(),
                                            memberInfo.getMemberName(), memberInfo.getMemberDescription(),
                                            memberInfo.getMemberContext(), memberInfo.getMemberTags(),
                                            memberInfo.getDataIdMap());
                                    importedMembersInfo.add(memberInfo);
                                }
                                break;
                            case OLD:
                                if (existing) {
                                    removeTrigger(tenantId, memberInfo.getMemberId());
                                    addMemberTrigger(tenantId, memberInfo.getGroupId(), memberInfo.getMemberId(),
                                            memberInfo.getMemberName(), memberInfo.getMemberDescription(),
                                            memberInfo.getMemberContext(), memberInfo.getMemberTags(),
                                            memberInfo.getDataIdMap());
                                    importedMembersInfo.add(memberInfo);
                                }
                                break;
                        }
                    }
                }
            }
            imported.setTriggers(importedTriggers);
            imported.setGroupMembersInfo(importedMembersInfo);
            imported.setActions(importedActionDefinitions);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        } finally {
            releaseNotifications();
        }

        return imported;
    }

    // Private methods

    private void deferNotifications() {
        ++deferNotificationsCount;
    }

    private void releaseNotifications() {
        if (deferNotificationsCount > 0) {
            if (--deferNotificationsCount == 0) {
                notifyListenersDeferred();
            }
        }
    }

    private boolean isDeferredNotifications() {
        return deferNotificationsCount > 0;
    }

    private void notifyListeners(final DefinitionsEvent de) {
        if (alertsContext == null) {
            log.debugf("AlertContext is not set. This scenario is only for testing.");
            return;
        }
        if (isDeferredNotifications()) {
            deferredNotifications.add(de);
            return;
        }
        alertsContext.notifyListeners(Arrays.asList(de));
    }

    private void notifyListenersDeferred() {
        if (deferredNotifications.isEmpty()) {
            return;
        }

        List<DefinitionsEvent> notifications = deferredNotifications;
        deferredNotifications = new ArrayList<>();
        alertsContext.notifyListeners(notifications);
    }

    private void addTrigger(Trigger trigger) throws Exception {
        if (trigger.getActions() != null) {
            Collection<ActionDefinition> actionDefinitions = getActionDefinitions(trigger.getTenantId());
            trigger.getActions().stream().forEach(actionDefinition -> {
                actionDefinition.setTenantId(trigger.getTenantId());
                boolean found = actionDefinitions.stream()
                        .filter(a -> a.getActionPlugin().equals(actionDefinition.getActionPlugin())
                                && a.getActionId().equals(actionDefinition.getActionId()))
                        .findFirst().isPresent();
                if (!found) {
                    throw new IllegalArgumentException("Action " + actionDefinition.getActionId() + " on plugin: "
                            + actionDefinition.getActionPlugin() + " is not found");
                }
            });
        }

        String pk = pk(trigger);
        IspnTrigger found = (IspnTrigger) backend.get(pk);
        if (found != null) {
            throw new FoundApplicationException(pk);
        }
        backend.put(pk, new IspnTrigger(trigger));

        if (null != alertsEngine) {
            alertsEngine.addTrigger(trigger.getTenantId(), trigger.getId());
        }

        notifyListeners(new DefinitionsEvent(DefinitionsEvent.Type.TRIGGER_CREATE, trigger));
    }

    private void removeTrigger(Trigger trigger) throws Exception {
        String tenantId = trigger.getTenantId();
        String triggerId = trigger.getId();
        try {
            backend.startBatch();

            backend.remove(pkFromTriggerId(tenantId, triggerId));
            removeConditions(tenantId, triggerId, null);
            getTriggerDampenings(tenantId, triggerId, null).stream()
                    .forEach(d -> backend.remove(pk(d)));

            backend.endBatch(true);
        } catch (Exception e) {
            try {
                backend.endBatch(false);
            } catch (Exception e2) {
                msgLog.errorDatabaseException(e2.getMessage());
            }
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        /*
            Trigger should be removed from the alerts engine.
         */
        if (null != alertsEngine) {
            alertsEngine.removeTrigger(tenantId, triggerId);
        }

        notifyListeners(
                new DefinitionsEvent(DefinitionsEvent.Type.TRIGGER_REMOVE, tenantId, triggerId, trigger.getTags()));
    }

    private Trigger updateTrigger(Trigger trigger) throws Exception {
        String pk = pk(trigger);
        backend.put(pk, new IspnTrigger(trigger));

        if (null != alertsEngine) {
            alertsEngine.reloadTrigger(trigger.getTenantId(), trigger.getId());
        }

        notifyListeners(new DefinitionsEvent(DefinitionsEvent.Type.TRIGGER_UPDATE, trigger));

        return trigger;
    }

    private Set<String> filterByTriggers(TriggersCriteria criteria) {
        Set<String> result = Collections.emptySet();
        if (isEmpty(criteria.getTriggerIds())) {
            if (!isEmpty(criteria.getTriggerId())) {
                result = new HashSet<>(1);
                result.add(criteria.getTriggerId());
            }
        } else {
            result = new HashSet<>();
            result.addAll(criteria.getTriggerIds());
        }
        return result;
    }

    private Page<Trigger> prepareTriggersPage(List<Trigger> triggers, Pager pager) {
        if (pager != null) {
            if (pager.getOrder() != null
                    && !pager.getOrder().isEmpty()
                    && pager.getOrder().get(0).getField() == null) {
                pager = Pager.builder()
                        .withPageSize(pager.getPageSize())
                        .withStartPage(pager.getPageNumber())
                        .orderBy(TriggerComparator.Field.NAME.getName(), Order.Direction.DESCENDING).build();
            }
            List<Trigger> ordered = triggers;
            if (pager.getOrder() != null) {
                pager.getOrder()
                        .stream()
                        .filter(o -> o.getField() != null && o.getDirection() != null)
                        .forEach(o -> {
                            TriggerComparator comparator = new TriggerComparator(o.getField(), o.getDirection());
                            Collections.sort(ordered, comparator);
                        });
            }
            if (!pager.isLimited() || ordered.size() < pager.getStart()) {
                pager = new Pager(0, ordered.size(), pager.getOrder());
                return new Page<>(ordered, pager, ordered.size());
            }
            if (pager.getEnd() >= ordered.size()) {
                return new Page<>(ordered.subList(pager.getStart(), ordered.size()), pager, ordered.size());
            }
            return new Page<>(ordered.subList(pager.getStart(), pager.getEnd()), pager, ordered.size());
        } else {
            pager = Pager.builder().withPageSize(triggers.size()).orderBy(TriggerComparator.Field.ID.getName(),
                    Order.Direction.ASCENDING).build();
            return new Page<>(triggers, pager, triggers.size());
        }
    }

    private Trigger copyGroupTrigger(Trigger group, Trigger member, boolean isNewMember) {
        member.setActions(group.getActions());
        member.setAutoDisable(group.isAutoDisable());
        member.setAutoEnable(group.isAutoEnable());
        member.setAutoResolve(group.isAutoResolve());
        member.setAutoResolveAlerts(group.isAutoResolveAlerts());
        member.setAutoResolveMatch(group.getAutoResolveMatch());
        member.setEnabled(group.isEnabled());
        member.setEventType(group.getEventType());
        member.setFiringMatch(group.getFiringMatch());
        member.setMemberOf(group.getId());
        member.setSeverity(group.getSeverity());
        member.setType(TriggerType.MEMBER);

        // On update don't override fields that can be customized at the member level. Make sure new
        // Context or Tag settings are merged in but don't remove or reset any existing keys.
        if (isNewMember) {
            member.setDataIdMap(group.getDataIdMap()); // likely irrelevant but here for completeness
            member.setDescription(group.getDescription());
            member.setContext(group.getContext());
            member.setTags(group.getTags());
        } else {
            if (!isEmpty(group.getContext())) {
                // add new group-level context
                Map<String, String> combinedContext = new HashMap<>();
                combinedContext.putAll(member.getContext());
                for (Map.Entry<String, String> entry : group.getContext().entrySet()) {
                    combinedContext.putIfAbsent(entry.getKey(), entry.getValue());
                }
                member.setContext(combinedContext);
            }
            if (!isEmpty(group.getTags())) {
                // add new group-level tags
                Map<String, String> combinedTags = new HashMap<>();
                combinedTags.putAll(member.getTags());
                for (Map.Entry<String, String> entry : group.getTags().entrySet()) {
                    combinedTags.putIfAbsent(entry.getKey(), entry.getValue());
                }
                member.setTags(combinedTags);
            }
        }

        return member;
    }

    private void updateTriggerEnablement(String tenantId, Collection<Trigger> triggers, boolean enabled)
            throws Exception {

        try {
            deferNotifications();

            for (Trigger trigger : triggers) {
                trigger.setEnabled(enabled);
                updateTrigger(trigger);
            }
        } finally {
            releaseNotifications();
        }
    }

    private Dampening addDampening(Dampening dampening) throws Exception {
        try {
            backend.put(pk(dampening), new IspnDampening(dampening));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        if (null != alertsEngine) {
            alertsEngine.reloadTrigger(dampening.getTenantId(), dampening.getTriggerId());
        }

        notifyListeners(new DefinitionsEvent(Type.DAMPENING_CHANGE, dampening));

        return dampening;
    }

    @Override
    public void createFullTrigger(String tenantId, FullTrigger fullTrigger) throws Exception {
        if (null == fullTrigger) {
            throw new IllegalArgumentException("FullTrigger must be not null");
        }
        Trigger trigger = fullTrigger.getTrigger();
        if (null == trigger) {
            throw new IllegalArgumentException("FullTrigger.Trigger must be not null");
        }
        if (trigger.isMember()) {
            throw new IllegalArgumentException("FullTrigger.Trigger.Type can not be a member trigger");
        }

        checkTenantId(tenantId, trigger);

        try {
            deferNotifications();
            addFullTrigger(tenantId, fullTrigger);

        } finally {
            releaseNotifications();
        }
    }

    // caller should be deferring notifications
    private void addFullTrigger(String tenantId, FullTrigger fullTrigger) throws Exception {
        if (null == fullTrigger) {
            throw new IllegalArgumentException("FullTrigger must be not null");
        }
        if (fullTrigger.getTrigger() != null) {
            Trigger trigger = fullTrigger.getTrigger();
            trigger.setTenantId(tenantId);
            addTrigger(trigger);
            if (!isEmpty(fullTrigger.getDampenings())) {
                for (Dampening d : fullTrigger.getDampenings()) {
                    d.setTenantId(tenantId);
                    d.setTriggerId(trigger.getId());
                    addDampening(d);
                }
            }
            if (!isEmpty(fullTrigger.getConditions())) {
                setAllConditions(tenantId, trigger.getId(), fullTrigger.getConditions());
            }
        }
    }

    @Override
    public FullTrigger getFullTrigger(String tenantId, String triggerId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        try {
            Trigger t = getTrigger(tenantId, triggerId);
            List<Dampening> allDampenings = new ArrayList<>();
            Collection<Dampening> firingDampenings = getTriggerDampenings(tenantId, t.getId(), Mode.FIRING);
            Collection<Dampening> autoDampenings = getTriggerDampenings(tenantId, t.getId(), Mode.AUTORESOLVE);
            if (!isEmpty(firingDampenings)) {
                allDampenings.addAll(firingDampenings);
            }
            if (!isEmpty(autoDampenings)) {
                allDampenings.addAll(autoDampenings);
            }
            List<Condition> allConditions = new ArrayList<>();
            Collection<Condition> firingConditions = getTriggerConditions(tenantId, t.getId(), Mode.FIRING);
            Collection<Condition> autoConditions = getTriggerConditions(tenantId, t.getId(), Mode.AUTORESOLVE);
            if (!isEmpty(firingConditions)) {
                allConditions.addAll(firingConditions);
            }
            if (!isEmpty(autoConditions)) {
                allConditions.addAll(autoConditions);
            }
            return new FullTrigger(t, allDampenings, allConditions);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    private List<FullTrigger> getFullTriggers(String tenantId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        List<FullTrigger> fullTriggers = new ArrayList<>();
        try {
            Collection<Trigger> triggers = getTriggers(tenantId, null, null);
            for (Trigger t : triggers) {
                List<Dampening> allDampenings = new ArrayList<>();
                Collection<Dampening> firingDampenings = getTriggerDampenings(tenantId, t.getId(), Mode.FIRING);
                Collection<Dampening> autoDampenings = getTriggerDampenings(tenantId, t.getId(), Mode.AUTORESOLVE);
                if (!isEmpty(firingDampenings)) {
                    allDampenings.addAll(firingDampenings);
                }
                if (!isEmpty(autoDampenings)) {
                    allDampenings.addAll(autoDampenings);
                }
                List<Condition> allConditions = new ArrayList<>();
                Collection<Condition> firingConditions = getTriggerConditions(tenantId, t.getId(), Mode.FIRING);
                Collection<Condition> autoConditions = getTriggerConditions(tenantId, t.getId(), Mode.AUTORESOLVE);
                if (!isEmpty(firingConditions)) {
                    allConditions.addAll(firingConditions);
                }
                if (!isEmpty(autoConditions)) {
                    allConditions.addAll(autoConditions);
                }
                fullTriggers.add(new FullTrigger(t, allDampenings, allConditions));
            }
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
        return fullTriggers;
    }

    private List<ActionDefinition> getActionDefinitions(String tenantId) throws Exception {
        List<IspnActionDefinition> actionDefinitions = queryFactory.from(IspnActionDefinition.class)
                .having("tenantId")
                .eq(tenantId)
                .build()
                .list();
        return actionDefinitions.stream().map(a -> a.getActionDefinition()).collect(Collectors.toList());
    }

    private Collection<Condition> mapConditions(List<IspnCondition> ispnConditions) {
        return ispnConditions.stream()
                .map(c -> c.getCondition())
                .collect(Collectors.toList());
    }

    private void updateMemberTriggerDataIdMap(String tenantId, String memberTriggerId, Map<String, String> dataIdMap)
            throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(memberTriggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (isEmpty(dataIdMap)) {
            throw new IllegalArgumentException("DatIdMap must be not null");
        }

        try {
            String pk = pkFromTriggerId(tenantId, memberTriggerId);
            Trigger memberTrigger = (Trigger) backend.get(pk);
            memberTrigger.setDataIdMap(dataIdMap);
            backend.put(pk, memberTrigger);
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    private Condition getMemberCondition(Trigger member, Condition groupCondition, Map<String, String> dataIdMap) {
        Condition newCondition = null;
        switch (groupCondition.getType()) {
            case AVAILABILITY:
                newCondition = new AvailabilityCondition(member.getTenantId(), member.getId(),
                        groupCondition.getTriggerMode(),
                        groupCondition.getConditionSetSize(), groupCondition.getConditionSetIndex(),
                        dataIdMap.get(groupCondition.getDataId()),
                        ((AvailabilityCondition) groupCondition).getOperator());
                break;
            case COMPARE:
                newCondition = new CompareCondition(member.getTenantId(), member.getId(),
                        groupCondition.getTriggerMode(),
                        groupCondition.getConditionSetSize(), groupCondition.getConditionSetIndex(),
                        dataIdMap.get(groupCondition.getDataId()),
                        ((CompareCondition) groupCondition).getOperator(),
                        ((CompareCondition) groupCondition).getData2Multiplier(),
                        dataIdMap.get(((CompareCondition) groupCondition).getData2Id()));
                break;
            case EVENT:
                newCondition = new EventCondition(member.getTenantId(), member.getId(),
                        groupCondition.getTriggerMode(),
                        groupCondition.getConditionSetSize(), groupCondition.getConditionSetIndex(),
                        dataIdMap.get(groupCondition.getDataId()),
                        ((EventCondition) groupCondition).getExpression());
                break;
            case EXTERNAL:
                String tokenDataId = groupCondition.getDataId();
                String memberDataId = dataIdMap.get(tokenDataId);
                String tokenExpression = ((ExternalCondition) groupCondition).getExpression();
                String memberExpression = isEmpty(tokenExpression) ? tokenExpression
                        : tokenExpression.replace(tokenDataId, memberDataId);
                newCondition = new ExternalCondition(member.getTenantId(), member.getId(),
                        groupCondition.getTriggerMode(),
                        groupCondition.getConditionSetSize(), groupCondition.getConditionSetIndex(),
                        memberDataId,
                        ((ExternalCondition) groupCondition).getAlerterId(),
                        memberExpression);
                break;
            case MISSING:
                newCondition = new MissingCondition(member.getTenantId(), member.getId(),
                        groupCondition.getTriggerMode(),
                        groupCondition.getConditionSetSize(), groupCondition.getConditionSetIndex(),
                        dataIdMap.get(groupCondition.getDataId()),
                        ((MissingCondition) groupCondition).getInterval());
                break;
            case NELSON:
                newCondition = new NelsonCondition(member.getTenantId(), member.getId(),
                        groupCondition.getTriggerMode(),
                        groupCondition.getConditionSetSize(), groupCondition.getConditionSetIndex(),
                        dataIdMap.get(groupCondition.getDataId()),
                        ((NelsonCondition) groupCondition).getActiveRules(),
                        ((NelsonCondition) groupCondition).getSampleSize());
                break;
            case RANGE:
                newCondition = new ThresholdRangeCondition(member.getTenantId(), member.getId(),
                        groupCondition.getTriggerMode(),
                        groupCondition.getConditionSetSize(), groupCondition.getConditionSetIndex(),
                        dataIdMap.get(groupCondition.getDataId()),
                        ((ThresholdRangeCondition) groupCondition).getOperatorLow(),
                        ((ThresholdRangeCondition) groupCondition).getOperatorHigh(),
                        ((ThresholdRangeCondition) groupCondition).getThresholdLow(),
                        ((ThresholdRangeCondition) groupCondition).getThresholdHigh(),
                        ((ThresholdRangeCondition) groupCondition).isInRange());
                break;
            case RATE:
                newCondition = new RateCondition(member.getTenantId(), member.getId(),
                        groupCondition.getTriggerMode(),
                        groupCondition.getConditionSetSize(), groupCondition.getConditionSetIndex(),
                        dataIdMap.get(groupCondition.getDataId()),
                        ((RateCondition) groupCondition).getDirection(),
                        ((RateCondition) groupCondition).getPeriod(),
                        ((RateCondition) groupCondition).getOperator(),
                        ((RateCondition) groupCondition).getThreshold());
                break;
            case STRING:
                newCondition = new StringCondition(member.getTenantId(), member.getId(),
                        groupCondition.getTriggerMode(),
                        groupCondition.getConditionSetSize(), groupCondition.getConditionSetIndex(),
                        dataIdMap.get(groupCondition.getDataId()),
                        ((StringCondition) groupCondition).getOperator(),
                        ((StringCondition) groupCondition).getPattern(),
                        ((StringCondition) groupCondition).isIgnoreCase());
                break;
            case THRESHOLD:
                newCondition = new ThresholdCondition(member.getTenantId(), member.getId(),
                        groupCondition.getTriggerMode(),
                        groupCondition.getConditionSetSize(), groupCondition.getConditionSetIndex(),
                        dataIdMap.get(groupCondition.getDataId()),
                        ((ThresholdCondition) groupCondition).getOperator(),
                        ((ThresholdCondition) groupCondition).getThreshold());
                break;
            default:
                throw new IllegalArgumentException("Unexpected Condition type: " + groupCondition.getType().name());
        }

        newCondition.setContext(groupCondition.getContext());
        return newCondition;
    }

    private Collection<Condition> setConditions(String tenantId, String triggerId, Mode triggerMode,
            Collection<Condition> conditions, Set<String> dataIds) throws Exception {

        // Get rid of the prior condition set
        removeConditions(tenantId, triggerId, triggerMode);

        // Now add the new condition set
        try {
            Map<String, IspnCondition> newConditions = new HashMap<>();
            int indexCondition = 0;
            for (Condition cond : conditions) {
                cond.setTenantId(tenantId);
                cond.setTriggerId(triggerId);
                cond.setTriggerMode(triggerMode);
                cond.setConditionSetSize(conditions.size());
                cond.setConditionSetIndex(++indexCondition);

                dataIds.add(cond.getDataId());
                switch (cond.getType()) {
                    case COMPARE:
                        CompareCondition cCond = (CompareCondition) cond;
                        dataIds.add(cCond.getData2Id());
                        break;

                    case AVAILABILITY:
                    case EVENT:
                    case EXTERNAL:
                    case MISSING:
                    case NELSON:
                    case RANGE:
                    case RATE:
                    case STRING:
                    case THRESHOLD:
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected ConditionType: " + cond);
                }
                newConditions.put(pk(cond), new IspnCondition(cond));
            }
            backend.putAll(newConditions);

        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        return conditions;
    }

    private void removeConditions(String tenantId, String triggerId, Mode triggerMode) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must not be null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must not be null");
        }

        try {
            getTriggerConditions(tenantId, triggerId, triggerMode).stream()
                    .forEach(c -> backend.remove(pk(c)));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    private void removeDampening(Dampening dampening) throws Exception {
        try {
            backend.remove(pk(dampening));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        if (null != alertsEngine) {
            alertsEngine.reloadTrigger(dampening.getTenantId(), dampening.getTriggerId());
        }

        notifyListeners(new DefinitionsEvent(Type.DAMPENING_CHANGE, dampening));
    }

    private Dampening updateDampening(Dampening dampening) throws Exception {
        try {
            backend.put(pk(dampening), new IspnDampening(dampening));
        } catch (Exception e) {
            msgLog.errorDatabaseException(e.getMessage());
            throw e;
        }

        if (null != alertsEngine) {
            alertsEngine.reloadTrigger(dampening.getTenantId(), dampening.getTriggerId());
        }

        notifyListeners(new DefinitionsEvent(Type.DAMPENING_CHANGE, dampening));

        return dampening;
    }

    private Collection<Dampening> mapDampenings(List<IspnDampening> ispnDampenings) {
        return ispnDampenings.stream()
                .map(d -> d.getDampening())
                .collect(Collectors.toList());
    }
}
