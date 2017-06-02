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
import static org.hawkular.alerts.engine.impl.ispn.IspnPk.pk;
import static org.hawkular.alerts.engine.util.Utils.checkTenantId;
import static org.hawkular.alerts.engine.util.Utils.isEmpty;

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

import org.hawkular.alerts.api.exception.FoundException;
import org.hawkular.alerts.api.exception.NotFoundException;
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
import org.hawkular.alerts.api.model.export.Definitions;
import org.hawkular.alerts.api.model.export.ImportType;
import org.hawkular.alerts.api.model.paging.Order;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.model.paging.TriggerComparator;
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
import org.hawkular.alerts.engine.exception.NotFoundApplicationException;
import org.hawkular.alerts.engine.impl.AlertsContext;
import org.hawkular.alerts.engine.impl.ispn.model.ActionPlugin;
import org.hawkular.alerts.engine.service.AlertsEngine;
import org.hawkular.alerts.log.AlertingLogger;
import org.hawkular.commons.log.MsgLogging;
import org.infinispan.Cache;
import org.infinispan.query.Search;
import org.infinispan.query.dsl.FilterConditionContext;
import org.infinispan.query.dsl.QueryBuilder;
import org.infinispan.query.dsl.QueryFactory;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IspnDefinitionsServiceImpl implements DefinitionsService {
    private final AlertingLogger log = MsgLogging.getMsgLogger(AlertingLogger.class, IspnDefinitionsServiceImpl.class);

    AlertsEngine alertsEngine;

    AlertsContext alertsContext;

    PropertiesService properties;

    Cache<String, Object> backend;

    QueryFactory queryFactory;

    private List<DefinitionsEvent> deferredNotifications = new ArrayList<>();
    private int deferNotificationsCount = 0;

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
        ActionDefinition found = (ActionDefinition) backend.get(pk);
        if (found != null) {
            throw new FoundException(pk);
        }
        backend.put(pk(actionDefinition), actionDefinition);

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

    }

    @Override
    public Trigger addMemberTrigger(String tenantId, String groupId, String memberId, String memberName, String memberDescription, Map<String, String> memberContext, Map<String, String> memberTags, Map<String, String> dataIdMap) throws Exception {
        return null;
    }

    @Override
    public Trigger addDataDrivenMemberTrigger(String tenantId, String groupId, String source) throws Exception {
        return null;
    }

    @Override
    public void removeTrigger(String tenantId, String triggerId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }

        Trigger doomedTrigger = getTrigger(tenantId, triggerId);

        if (doomedTrigger.isGroup()) {
            throw new IllegalArgumentException("Trigger [" + tenantId + "/" + triggerId + "] is a group trigger.");
        }

        removeTrigger(tenantId, triggerId, doomedTrigger);
    }

    @Override
    public void removeGroupTrigger(String tenantId, String groupId, boolean keepNonOrphans, boolean keepOrphans) throws Exception {

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
        return null;
    }

    @Override
    public void updateGroupTriggerEnablement(String tenantId, String groupTriggerIds, boolean enabled) throws Exception {

    }

    @Override
    public void updateTriggerEnablement(String tenantId, String triggerIds, boolean enabled) throws Exception {

    }

    @Override
    public Trigger getTrigger(String tenantId, String triggerId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(triggerId)) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        String pk = pk(tenantId, triggerId);
        Trigger found = (Trigger) backend.get(pk);
        if (found == null) {
            throw new NotFoundException(pk);
        }
        return found;
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

        List<Trigger> triggers;
        if (filter) {
            StringBuilder query = new StringBuilder("from org.hawkular.alerts.api.model.trigger.Trigger where ");
            query.append("tenantId = '").append(tenantId).append("' and ");
            if (criteria.hasTriggerIdCriteria()) {
                Set<String> triggerIds = filterByTriggers(criteria);
                query.append("(");
                Iterator<String> iter = triggerIds.iterator();
                while (iter.hasNext()) {
                    String triggerId = iter.next();
                    query.append("id = '").append(triggerId).append("' ");
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
                while(iter.hasNext()) {
                    Map.Entry<String, String> tag = iter.next();
                    query.append("tags like '")
                            .append(tag.getKey())
                            .append(":")
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
            triggers = queryFactory.from(Trigger.class)
                    .having("tenantId")
                    .eq(tenantId)
                    .build()
                    .list();
        }
        return prepareTriggersPage(triggers, pager);
    }

    @Override
    public Collection<Trigger> getMemberTriggers(String tenantId, String groupId, boolean includeOrphans) throws Exception {
        return null;
    }

    @Override
    public Collection<Trigger> getAllTriggers() throws Exception {
        return queryFactory.from(Trigger.class).build().list();
    }

    @Override
    public Collection<Trigger> getAllTriggersByTag(String name, String value) throws Exception {
        if (isEmpty(name)) {
            throw new IllegalArgumentException("name must be not null");
        }
        if (isEmpty(value)) {
            throw new IllegalArgumentException("value must be not null (use '*' for all");
        }
        StringBuilder query = new StringBuilder("from org.hawkular.alerts.api.model.trigger.Trigger where tags like '")
                .append(name)
                .append(":")
                .append(value.equals("*") ? "%" : value)
                .append("'");
        return queryFactory.create(query.toString()).list();
    }

    @Override
    public Trigger orphanMemberTrigger(String tenantId, String memberId) throws Exception {
        return null;
    }

    @Override
    public Trigger unorphanMemberTrigger(String tenantId, String memberId, Map<String, String> memberContext, Map<String, String> memberTags, Map<String, String> dataIdMap) throws Exception {
        return null;
    }

    @Override
    public Dampening addDampening(String tenantId, Dampening dampening) throws Exception {
        return null;
    }

    @Override
    public Dampening addGroupDampening(String tenantId, Dampening groupDampening) throws Exception {
        return null;
    }

    @Override
    public void removeDampening(String tenantId, String dampeningId) throws Exception {

    }

    @Override
    public void removeGroupDampening(String tenantId, String groupDampeningId) throws Exception {

    }

    @Override
    public Dampening updateDampening(String tenantId, Dampening dampening) throws Exception {
        return null;
    }

    @Override
    public Dampening updateGroupDampening(String tenantId, Dampening groupDampening) throws Exception {
        return null;
    }

    @Override
    public Dampening getDampening(String tenantId, String dampeningId) throws Exception {
        return null;
    }

    @Override
    public Collection<Dampening> getTriggerDampenings(String tenantId, String triggerId, Mode triggerMode) throws Exception {
        return null;
    }

    @Override
    public Collection<Dampening> getAllDampenings() throws Exception {
        return null;
    }

    @Override
    public Collection<Dampening> getDampenings(String tenantId) throws Exception {
        return null;
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
        Mode otherTtriggerMode = triggerMode == Mode.FIRING ? Mode.AUTORESOLVE : Mode.FIRING;
        Collection<Condition> otherConditions = getTriggerConditions(tenantId, triggerId, otherTtriggerMode);

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

        conditions.stream().forEach(c -> c.setTenantId(tenantId));
        conditions.stream().forEach(c -> c.setTriggerId(triggerId));

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

    private Collection<Condition> setConditions(String tenantId, String triggerId, Mode triggerMode,
            Collection<Condition> conditions, Set<String> dataIds) throws Exception {

        // Get rid of the prior condition set
        removeConditions(tenantId, triggerId, triggerMode);

        // Now add the new condition set
        try {
            Map<String, Condition> newConditions = new HashMap<>();
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
                newConditions.put(pk(cond), cond);
            }
            backend.putAll(newConditions);

        } catch (Exception e) {
            log.errorDatabaseException(e.getMessage());
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
        if (triggerMode == null) {
            throw new IllegalArgumentException("TriggerMode must not be null");
        }

        try {
            getTriggerConditions(tenantId, triggerId, triggerMode).stream()
                    .forEach(c -> backend.remove(pk(c)));
        } catch (Exception e) {
            log.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public Collection<Condition> setGroupConditions(String tenantId, String groupId, Mode triggerMode, Collection<Condition> groupConditions, Map<String, Map<String, String>> dataIdMemberMap) throws Exception {
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

            Trigger group = getTrigger(tenantId, groupId);
            if (null == group) {
                throw new NotFoundApplicationException(Trigger.class.getName(), tenantId, groupId);
            }
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
                    removeTrigger(member.getTenantId(), member.getId(), member);
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
            String pk = pk(tenantId, memberTriggerId);
            Trigger memberTrigger = (Trigger) backend.get(pk);
            memberTrigger.setDataIdMap(dataIdMap);
            backend.put(pk, memberTrigger);
        } catch (Exception e) {
            log.errorDatabaseException(e.getMessage());
            throw e;
        }
    }

    @Override
    public Condition getCondition(String tenantId, String conditionId) throws Exception {
        // note that tenantId is already incorporated into the conditionId
        return (Condition) backend.get(conditionId);
    }

    @Override
    public Collection<Condition> getTriggerConditions(String tenantId, String triggerId, Mode triggerMode) throws Exception {
        FilterConditionContext qb = queryFactory.from(Condition.class)
                .having("tenantId").eq(tenantId).and()
                .having("triggerId").eq(triggerId);
        if (null != triggerMode) {
            qb = qb.and().having("triggerMode").eq(triggerMode.name());
        }
        return ((QueryBuilder) qb).build().list();
    }

    @Override
    public Collection<Condition> getConditions(String tenantId) throws Exception {
        return queryFactory.from(Condition.class).having("tenantId").eq(tenantId).build().list();
    }

    // TODO: This getAll* fetches are cross-tenant fetch and may be inefficient at scale
    @Override
    public Collection<Condition> getAllConditions() throws Exception {
        return queryFactory.from(Condition.class).build().list();
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
            throw new FoundException(pk);
        }
        backend.put(pk, new ActionPlugin(actionPlugin, defaultProperties));
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
        ActionPlugin found = (ActionPlugin) backend.get(pk);
        if (found == null) {
            throw new NotFoundException(pk);
        }
        found.setDefaultProperties(defaultProperties);
        backend.put(pk, found);
    }

    @Override
    public Collection<String> getActionPlugins() throws Exception {
        Set<String> pluginNames = new HashSet<>();
        List<ActionPlugin> plugins = queryFactory.from(ActionPlugin.class).build().list();
        for (ActionPlugin plugin : plugins) {
            pluginNames.add(plugin.getActionPlugin());
        }
        return pluginNames;
    }

    @Override
    public Set<String> getActionPlugin(String actionPlugin) throws Exception {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        ActionPlugin found = (ActionPlugin) backend.get(pk(actionPlugin));
        return found == null? null : found.getDefaultProperties().keySet();
    }

    @Override
    public Map<String, String> getDefaultActionPlugin(String actionPlugin) throws Exception {
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("actionPlugin must be not null");
        }
        ActionPlugin found = (ActionPlugin) backend.get(pk(actionPlugin));
        return found == null? null : found.getDefaultProperties();
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
        ActionDefinition found = (ActionDefinition) backend.get(pk);
        if (found == null) {
            throw new NotFoundException(pk);
        }
        backend.put(pk, actionDefinition);

        notifyListeners(new DefinitionsEvent(ACTION_DEFINITION_UPDATE, actionDefinition));
    }

    @Override
    public Map<String, Map<String, Set<String>>> getAllActionDefinitionIds() throws Exception {
        Map<String, Map<String, Set<String>>> actions = new HashMap<>();
        List<ActionDefinition> actionDefinitions = queryFactory.from(ActionDefinition.class).build().list();
        for (ActionDefinition action : actionDefinitions) {
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
        return queryFactory.from(ActionDefinition.class).build().list();
    }

    @Override
    public Map<String, Set<String>> getActionDefinitionIds(String tenantId) throws Exception {
        Map<String, Set<String>> actionIds = new HashMap<>();
        List<ActionDefinition> actionDefinitions = queryFactory.from(ActionDefinition.class)
                .having("tenantId")
                .eq(tenantId)
                .build()
                .list();
        for (ActionDefinition action : actionDefinitions) {
            String actionPlugin = action.getActionPlugin();
            String actionId = action.getActionId();
            if (actionIds.get(actionPlugin) == null) {
                actionIds.put(actionPlugin, new HashSet<>());
            }
            actionIds.get(actionPlugin).add(actionId);
        }
        return actionIds;
    }

    public Collection<ActionDefinition> getActionDefinitions(String tenantId) throws Exception {
        return queryFactory.from(ActionDefinition.class)
                .having("tenantId")
                .eq(tenantId)
                .build()
                .list();
    }

    @Override
    public Collection<String> getActionDefinitionIds(String tenantId, String actionPlugin) throws Exception {
        Set<String> actionIds = new HashSet<>();
        List<ActionDefinition> actionDefinitions = queryFactory.from(ActionDefinition.class)
                .having("tenantId")
                .eq(tenantId)
                .and()
                .having("actionPlugin")
                .eq(actionPlugin)
                .build()
                .list();
        for (ActionDefinition action : actionDefinitions) {
            actionIds.add(action.getActionId());
        }
        return actionIds;
    }

    @Override
    public ActionDefinition getActionDefinition(String tenantId, String actionPlugin, String actionId) throws Exception {
        if (isEmpty(tenantId)) {
            throw new IllegalArgumentException("TenantId must be not null");
        }
        if (isEmpty(actionPlugin)) {
            throw new IllegalArgumentException("ActionPlugin must be not null");
        }
        if (isEmpty(actionId)) {
            throw new IllegalArgumentException("actionId must be not null");
        }
        return (ActionDefinition) backend.get(pk(tenantId, actionPlugin, actionId));
    }

    @Override
    public void registerListener(DefinitionsListener listener, DefinitionsEvent.Type eventType, DefinitionsEvent.Type... eventTypes) {

    }

    @Override
    public Definitions exportDefinitions(String tenantId) throws Exception {
        return null;
    }

    @Override
    public Definitions importDefinitions(String tenantId, Definitions definitions, ImportType strategy) throws Exception {
        return null;
    }

    @Override
    public void registerDistributedListener(DistributedListener listener) {

    }

    // Private methods

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
        Trigger found = (Trigger) backend.get(pk);
        if (found != null) {
            throw new FoundException(pk);
        }
        backend.put(pk, trigger);

        if (null != alertsEngine) {
            alertsEngine.addTrigger(trigger.getTenantId(), trigger.getId());
        }

        notifyListeners(new DefinitionsEvent(DefinitionsEvent.Type.TRIGGER_CREATE, trigger));
    }

    private void removeTrigger(String tenantId, String triggerId, Trigger trigger) throws Exception {
        backend.remove(pk(tenantId, triggerId));
        // TODO delete dampenings and conditions

        /*
            Trigger should be removed from the alerts engine.
         */
        if (null != alertsEngine) {
            alertsEngine.removeTrigger(tenantId, triggerId);
        }

        notifyListeners(new DefinitionsEvent(DefinitionsEvent.Type.TRIGGER_REMOVE, tenantId, triggerId, trigger.getTags()));
    }

    private Trigger updateTrigger(Trigger trigger) throws Exception {
        String pk = pk(trigger);
        backend.put(pk, trigger);

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
}
