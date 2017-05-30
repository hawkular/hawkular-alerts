package org.hawkular.alerts.engine.impl.ispn;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.export.Definitions;
import org.hawkular.alerts.api.model.export.ImportType;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.model.trigger.Mode;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.DefinitionsEvent;
import org.hawkular.alerts.api.services.DefinitionsListener;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.DistributedListener;
import org.hawkular.alerts.api.services.TriggersCriteria;
import org.hawkular.alerts.log.AlertingLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IspnDefinitionsServiceImpl implements DefinitionsService {
    private final AlertingLogger log = MsgLogging.getMsgLogger(AlertingLogger.class, IspnDefinitionsServiceImpl.class);

    @Override
    public void addTrigger(String tenantId, Trigger trigger) throws Exception {

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

    }

    @Override
    public void removeGroupTrigger(String tenantId, String groupId, boolean keepNonOrphans, boolean keepOrphans) throws Exception {

    }

    @Override
    public Trigger updateTrigger(String tenantId, Trigger trigger) throws Exception {
        return null;
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
        return null;
    }

    @Override
    public Page<Trigger> getTriggers(String tenantId, TriggersCriteria criteria, Pager pager) throws Exception {
        return null;
    }

    @Override
    public Collection<Trigger> getMemberTriggers(String tenantId, String groupId, boolean includeOrphans) throws Exception {
        return null;
    }

    @Override
    public Collection<Trigger> getAllTriggers() throws Exception {
        return null;
    }

    @Override
    public Collection<Trigger> getAllTriggersByTag(String name, String value) throws Exception {
        return null;
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
    public Collection<Condition> addCondition(String tenantId, String triggerId, Mode triggerMode, Condition condition) throws Exception {
        return null;
    }

    @Override
    public Collection<Condition> removeCondition(String tenantId, String conditionId) throws Exception {
        return null;
    }

    @Override
    public Collection<Condition> updateCondition(String tenantId, Condition condition) throws Exception {
        return null;
    }

    @Override
    public Collection<Condition> setConditions(String tenantId, String triggerId, Mode triggerMode, Collection<Condition> conditions) throws Exception {
        return null;
    }

    @Override
    public Collection<Condition> setGroupConditions(String tenantId, String groupId, Mode triggerMode, Collection<Condition> groupConditions, Map<String, Map<String, String>> dataIdMemberMap) throws Exception {
        return null;
    }

    @Override
    public Condition getCondition(String tenantId, String conditionId) throws Exception {
        return null;
    }

    @Override
    public Collection<Condition> getTriggerConditions(String tenantId, String triggerId, Mode triggerMode) throws Exception {
        return null;
    }

    @Override
    public Collection<Condition> getConditions(String tenantId) throws Exception {
        return null;
    }

    @Override
    public Collection<Condition> getAllConditions() throws Exception {
        return null;
    }

    @Override
    public void addActionPlugin(String actionPlugin, Set<String> properties) throws Exception {

    }

    @Override
    public void addActionPlugin(String actionPlugin, Map<String, String> defaultProperties) throws Exception {

    }

    @Override
    public void removeActionPlugin(String actionPlugin) throws Exception {

    }

    @Override
    public void updateActionPlugin(String actionPlugin, Set<String> properties) throws Exception {

    }

    @Override
    public void updateActionPlugin(String actionPlugin, Map<String, String> defaultProperties) throws Exception {

    }

    @Override
    public Collection<String> getActionPlugins() throws Exception {
        return null;
    }

    @Override
    public Set<String> getActionPlugin(String actionPlugin) throws Exception {
        return null;
    }

    @Override
    public Map<String, String> getDefaultActionPlugin(String actionPlugin) throws Exception {
        return null;
    }

    @Override
    public void addActionDefinition(String tenantId, ActionDefinition actionDefinition) throws Exception {

    }

    @Override
    public void removeActionDefinition(String tenantId, String actionPlugin, String actionId) throws Exception {

    }

    @Override
    public void updateActionDefinition(String tenantId, ActionDefinition actionDefinition) throws Exception {

    }

    @Override
    public Map<String, Map<String, Set<String>>> getAllActionDefinitionIds() throws Exception {
        return null;
    }

    @Override
    public Collection<ActionDefinition> getAllActionDefinitions() throws Exception {
        return null;
    }

    @Override
    public Map<String, Set<String>> getActionDefinitionIds(String tenantId) throws Exception {
        return null;
    }

    @Override
    public Collection<String> getActionDefinitionIds(String tenantId, String actionPlugin) throws Exception {
        return null;
    }

    @Override
    public ActionDefinition getActionDefinition(String tenantId, String actionPlugin, String actionId) throws Exception {
        return null;
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
}
