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
package org.hawkular.alerts.engine.impl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.ejb.EJB;

import org.hawkular.alerts.api.model.condition.AvailabilityCondition;
import org.hawkular.alerts.api.model.condition.CompareCondition;
import org.hawkular.alerts.api.model.condition.Condition;
import org.hawkular.alerts.api.model.condition.StringCondition;
import org.hawkular.alerts.api.model.condition.ThresholdCondition;
import org.hawkular.alerts.api.model.condition.ThresholdRangeCondition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.model.trigger.Trigger.Mode;
import org.hawkular.alerts.api.model.trigger.TriggerTemplate;
import org.hawkular.alerts.api.services.DefinitionsService;
import org.hawkular.alerts.api.services.NotificationsService;
import org.hawkular.alerts.engine.log.MsgLogger;

import org.jboss.logging.Logger;

/**
 * A memory implementation of {@link org.hawkular.alerts.api.services.DefinitionsService}.
 * It is intended only for early prototype phases.
 * It will be replaced for a proper implementation based on a persistence repository.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
// @Singleton
public class MemDefinitionsServiceImpl implements DefinitionsService {
    private final MsgLogger msgLog = MsgLogger.LOGGER;
    private final Logger log = Logger.getLogger(MemDefinitionsServiceImpl.class);

    private static final String JBOSS_DATA_DIR = "jboss.server.data.dir";
    private static final String INIT_FOLDER = "hawkular-alerts";

    private Map<String, Trigger> triggers = new ConcurrentHashMap<String, Trigger>();
    private Map<String, Condition> conditions = new ConcurrentHashMap<String, Condition>();
    private Map<String, Dampening> dampenings = new ConcurrentHashMap<String, Dampening>();
    private Map<String, Set<String>> notifierTypes = new ConcurrentHashMap<String, Set<String>>();
    private Map<String, Map<String, String>> notifiers = new ConcurrentHashMap<String, Map<String, String>>();

    @EJB
    NotificationsService notifications;

    public MemDefinitionsServiceImpl() {
        log.debugf("Creating instance.");
    }

    @PostConstruct
    public void init() {
        log.debugf("Initial load from file");

        String data = System.getProperty(JBOSS_DATA_DIR);
        if (data == null) {
            msgLog.errorFolderNotFound(data);
            return;
        }
        String folder = data + "/" + INIT_FOLDER;
        initFiles(folder);
    }

    /*
        Helper method to initialize data from files.
        It doesn't validate all possible incorrect situations.
        It expects a good file.
        Used only for demo/poc purposes.
     */
    private void initFiles(String folder) {

        if (folder == null) {
            msgLog.errorFolderMustBeNotNull();
            return;
        }

        File initFolder = new File(folder);

        /*
            Triggers
         */
        File triggers = new File(initFolder, "triggers.data");
        if (triggers.exists() && triggers.isFile()) {
            List<String> lines = null;
            try {
                lines = Files.readAllLines(Paths.get(triggers.toURI()), Charset.forName("UTF-8"));
            } catch (IOException e) {
                log.debugf(e.toString(), e);
                msgLog.errorReadingFile("triggers.data");
            }
            if (lines != null && !lines.isEmpty()) {
                for (String line : lines) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    if (fields.length == 8) {
                        String triggerId = fields[0];
                        boolean enabled = new Boolean(fields[1]).booleanValue();
                        boolean safetyEnabled = new Boolean(fields[2]).booleanValue();
                        String name = fields[3];
                        String description = fields[4];
                        TriggerTemplate.Match firingMatch = TriggerTemplate.Match.valueOf(fields[5]);
                        TriggerTemplate.Match safetyMatch = TriggerTemplate.Match.valueOf(fields[6]);
                        String[] notifiers = fields[7].split("\\|");

                        Trigger trigger = new Trigger(triggerId, name);
                        trigger.setEnabled(enabled);
                        trigger.setSafetyEnabled(safetyEnabled);
                        trigger.setDescription(description);
                        trigger.setFiringMatch(firingMatch);
                        trigger.setSafetyMatch(safetyMatch);
                        for (String notifier : notifiers) {
                            trigger.addNotifier(notifier);
                        }

                        this.triggers.put(triggerId, trigger);
                        log.debugf("Init file - Inserting [%s]", trigger);
                    }
                }
            }
        } else {
            msgLog.errorFileNotFound("triggers.data");
        }

        /*
            Conditions
         */
        File conditions = new File(initFolder, "conditions.data");
        if (conditions.exists() && conditions.isFile()) {
            List<String> lines = null;
            try {
                lines = Files.readAllLines(Paths.get(conditions.toURI()), Charset.forName("UTF-8"));
            } catch (IOException e) {
                msgLog.errorReadingFile("conditions.data");
            }
            if (lines != null && !lines.isEmpty()) {
                for (String line : lines) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    if (fields.length > 4) {
                        String triggerId = fields[0];
                        Mode triggerMode = Mode.valueOf(fields[1]);
                        int conditionSetSize = new Integer(fields[2]).intValue();
                        int conditionSetIndex = new Integer(fields[3]).intValue();
                        String type = fields[4];
                        if (type != null && !type.isEmpty() && type.equals("threshold") && fields.length == 8) {
                            String dataId = fields[5];
                            String operator = fields[6];
                            Double threshold = new Double(fields[7]).doubleValue();

                            ThresholdCondition newCondition = new ThresholdCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setDataId(dataId);
                            newCondition.setOperator(ThresholdCondition.Operator.valueOf(operator));
                            newCondition.setThreshold(threshold);

                            this.conditions.put(newCondition.getConditionId(), newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                        if (type != null && !type.isEmpty() && type.equals("range") && fields.length == 11) {
                            String dataId = fields[5];
                            String operatorLow = fields[6];
                            String operatorHigh = fields[7];
                            Double thresholdLow = new Double(fields[8]).doubleValue();
                            Double thresholdHigh = new Double(fields[9]).doubleValue();
                            boolean inRange = new Boolean(fields[10]).booleanValue();

                            ThresholdRangeCondition newCondition = new ThresholdRangeCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setDataId(dataId);
                            newCondition.setOperatorLow(ThresholdRangeCondition.Operator.valueOf(operatorLow));
                            newCondition.setOperatorHigh(ThresholdRangeCondition.Operator.valueOf(operatorHigh));
                            newCondition.setThresholdLow(thresholdLow);
                            newCondition.setThresholdHigh(thresholdHigh);
                            newCondition.setInRange(inRange);

                            this.conditions.put(newCondition.getConditionId(), newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                        if (type != null && !type.isEmpty() && type.equals("compare") && fields.length == 9) {
                            String data1Id = fields[5];
                            String operator = fields[6];
                            Double data2Multiplier = new Double(fields[7]).doubleValue();
                            String data2Id = fields[8];

                            CompareCondition newCondition = new CompareCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setData1Id(data1Id);
                            newCondition.setOperator(CompareCondition.Operator.valueOf(operator));
                            newCondition.setData2Multiplier(data2Multiplier);
                            newCondition.setData2Id(data2Id);

                            this.conditions.put(newCondition.getConditionId(), newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                        if (type != null && !type.isEmpty() && type.equals("string") && fields.length == 9) {
                            String dataId = fields[5];
                            String operator = fields[6];
                            String pattern = fields[7];
                            boolean ignoreCase = new Boolean(fields[8]).booleanValue();

                            StringCondition newCondition = new StringCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setDataId(dataId);
                            newCondition.setOperator(StringCondition.Operator.valueOf(operator));
                            newCondition.setPattern(pattern);
                            newCondition.setIgnoreCase(ignoreCase);

                            this.conditions.put(newCondition.getConditionId(), newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                        if (type != null && !type.isEmpty() && type.equals("availability") && fields.length == 7) {
                            String dataId = fields[5];
                            String operator = fields[6];

                            AvailabilityCondition newCondition = new AvailabilityCondition();
                            newCondition.setTriggerId(triggerId);
                            newCondition.setTriggerMode(triggerMode);
                            newCondition.setConditionSetSize(conditionSetSize);
                            newCondition.setConditionSetIndex(conditionSetIndex);
                            newCondition.setDataId(dataId);
                            newCondition.setOperator(AvailabilityCondition.Operator.valueOf(operator));

                            this.conditions.put(newCondition.getConditionId(), newCondition);
                            log.debugf("Init file - Inserting [%s]", newCondition);
                        }
                    }
                }
            }
        } else {
            msgLog.errorFileNotFound("conditions.data");
        }

        /*
            Dampening
         */
        File dampening = new File(initFolder, "dampening.data");
        if (dampening.exists() && dampening.isFile()) {
            List<String> lines = null;
            try {
                lines = Files.readAllLines(Paths.get(dampening.toURI()), Charset.forName("UTF-8"));
            } catch (IOException e) {
                msgLog.errorReadingFile("dampening.data");
            }
            if (lines != null && !lines.isEmpty()) {
                for (String line : lines) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    if (fields.length == 6) {
                        String triggerId = fields[0];
                        Mode triggerMode = Mode.valueOf(fields[1]);
                        String type = fields[2];
                        int evalTrueSetting = new Integer(fields[3]);
                        int evalTotalSetting = new Integer(fields[4]);
                        int evalTimeSetting = new Integer(fields[5]);

                        Dampening newDampening = new Dampening(triggerId, triggerMode, Dampening.Type.valueOf(type),
                                evalTrueSetting, evalTotalSetting, evalTimeSetting);

                        this.dampenings.put(triggerId, newDampening);
                        log.debugf("Init file - Inserting [%s]", newDampening);
                    }
                }
            }
        } else {
            msgLog.errorFileNotFound("dampening.data");
        }

        /*
            Notifiers
         */
        File notifiers = new File(initFolder, "notifiers.data");
        if (notifiers.exists() && notifiers.isFile()) {
            List<String> lines = null;
            try {
                lines = Files.readAllLines(Paths.get(notifiers.toURI()), Charset.forName("UTF-8"));
            } catch (IOException e) {
                log.error(e.toString(), e);
            }
            if (lines != null && !lines.isEmpty()) {
                for (String line : lines) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String[] fields = line.split(",");
                    if (fields.length == 3) {
                        String notifierId = fields[0];
                        String notifierType = fields[1];
                        String description = fields[2];
                        Map<String, String> newNotifier = new HashMap<String, String>();
                        newNotifier.put("NotifierID", notifierId);
                        newNotifier.put("NotifierType", notifierType);
                        newNotifier.put("description", description);

                        this.notifiers.put(notifierId, newNotifier);
                        log.debugf("Init file - Inserting [%s]", newNotifier);
                    }
                }
            }
        } else {
            msgLog.errorFileNotFound("notifiers.data");
        }

        log.debugf("Triggers: %s size.", this.triggers.keySet().size());
        log.debugf("Conditions: %s size.", this.conditions.keySet().size());
        log.debugf("Dampenings: %s size.", this.dampenings.keySet().size());
        log.debugf("Notifiers Types: %s size.", this.notifierTypes.keySet().size());
        log.debugf("Notifiers: %s size.", this.notifiers.keySet().size());
    }

    @Override
    public void addCondition(Condition condition) {
        if (condition == null || condition.getConditionId() == null || condition.getConditionId().isEmpty()) {
            throw new IllegalArgumentException("Condition must be not null");
        }
        if (conditions.containsKey(condition.getConditionId())) {
            throw new IllegalArgumentException("Condition already exists on repository");
        }
        conditions.put(condition.getConditionId(), condition);
    }

    @Override
    public void addNotifier(String notifierId, Map<String, String> properties) {
        if (notifierId == null || notifierId.isEmpty()) {
            throw new IllegalArgumentException("NotifierId must be not null");
        }
        if (properties == null) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        if (notifiers.containsKey(notifierId)) {
            throw new IllegalArgumentException("Notifier already exists on repository");
        }
        notifiers.put(notifierId, properties);
        notifications.register(notifierId, properties);
    }

    @Override
    public void addNotifierType(String notifierType, Set<String> properties) {
        if (notifierType == null || notifierType.isEmpty()) {
            throw new IllegalArgumentException("NotifierType must be not null");
        }
        if (properties == null) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        if (notifierTypes.containsKey(notifierType)) {
            throw new IllegalArgumentException("NotifierType already exists on repository");
        }
        notifierTypes.put(notifierType, properties);
    }

    @Override
    public void addTrigger(Trigger trigger) {
        if (trigger == null || trigger.getId() == null || trigger.getId().isEmpty()) {
            throw new IllegalArgumentException("Trigger must be not null");
        }
        if (triggers.containsKey(trigger.getId())) {
            throw new IllegalArgumentException("Trigger already exists on repository");
        }
        triggers.put(trigger.getId(), trigger);
    }

    @Override
    public void addDampening(Dampening dampening) {
        if (dampening == null || dampening.getTriggerId() == null || dampening.getTriggerId().isEmpty()) {
            throw new IllegalArgumentException("Dampening must be not null");
        }
        if (dampenings.containsKey(dampening.getTriggerId())) {
            throw new IllegalArgumentException("Dampening already exists on repository");
        }
        dampenings.put(dampening.getTriggerId(), dampening);
    }

    @Override
    public Condition getCondition(String conditionId) {
        if (conditionId == null || conditionId.isEmpty()) {
            throw new IllegalArgumentException("ConditionId must be not null");
        }
        return conditions.get(conditionId);
    }

    @Override
    public Collection<Condition> getConditions() {
        return Collections.unmodifiableCollection(conditions.values());
    }

    @Override
    public Collection<Condition> getConditions(String triggerId) {
        /*
            Not indexed search.
            This method is not optimized in any way, it's just for demo purposes.
         */
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        Collection<Condition> search = new ArrayList<Condition>();
        Collection<Condition> values = conditions.values();
        for (Condition cond : values) {
            if (cond.getTriggerId().equals(triggerId)) {
                search.add(cond);
            }
        }
        return Collections.unmodifiableCollection(search);
    }

    @Override
    public Map<String, String> getNotifier(String notifierId) {
        if (notifierId == null || notifierId.isEmpty()) {
            throw new IllegalArgumentException("NotifierId must be not null");
        }
        return notifiers.get(notifierId);
    }

    @Override
    public Collection<String> getNotifiers() {
        return Collections.unmodifiableSet(notifiers.keySet());
    }

    @Override
    public Set<String> getNotifierType(String notifierType) {
        if (notifierType == null || notifierType.isEmpty()) {
            throw new IllegalArgumentException("NotifierType must be not null");
        }
        return notifierTypes.get(notifierType);
    }

    @Override
    public Collection<String> getNotifiers(String notifierType) {
        if (notifierType == null || notifierType.isEmpty()) {
            throw new IllegalArgumentException("NotifierType must be not null");
        }
        /*
            This is a non optimized search example.
            It is used just for demo/poc purposes.
         */
        List<String> filtered = new ArrayList<String>();
        Set<String> keys = notifiers.keySet();
        for (String notifierId: keys) {
            Map<String, String> properties = notifiers.get(notifierId);
            if (properties != null && properties.isEmpty()
                    && properties.containsKey("NotifierType")
                    && properties.get("NotifierType").equals(notifierType)) {
                filtered.add(notifierId);
            }
        }
        return Collections.unmodifiableCollection(filtered);
    }

    @Override
    public Collection<String> getNotifierTypes() {
        return Collections.unmodifiableSet(notifierTypes.keySet());
    }

    @Override
    public Trigger getTrigger(String triggerId) {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        return triggers.get(triggerId);
    }

    @Override
    public Collection<Trigger> getTriggers() {
        return Collections.unmodifiableCollection(triggers.values());
    }

    @Override
    public Collection<Dampening> getDampenings() {
        return Collections.unmodifiableCollection(dampenings.values());
    }

    @Override
    public Dampening getDampening(String triggerId) {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        return dampenings.get(triggerId);
    }

    @Override
    public void removeCondition(String conditionId) {
        if (conditionId == null || conditionId.isEmpty()) {
            throw new IllegalArgumentException("ConditionId must be not null");
        }
        if (conditions.containsKey(conditionId)) {
            conditions.remove(conditionId);
        }
    }

    @Override
    public void removeNotifier(String notifierId) {
        if (notifierId == null || notifierId.isEmpty()) {
            throw new IllegalArgumentException("NotifierId must be not null");
        }
        if (notifiers.containsKey(notifierId)) {
            notifiers.remove(notifierId);
            notifications.deregister(notifierId);
        }
    }

    @Override
    public void removeNotifierType(String notifierType) {
        if (notifierType == null || notifierType.isEmpty()) {
            throw new IllegalArgumentException("NotifierType must be not null");
        }
        if (notifierTypes.containsKey(notifierType)) {
            notifierTypes.remove(notifierType);
        }
    }

    @Override
    public void removeTrigger(String triggerId) {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (triggers.containsKey(triggerId)) {
            triggers.remove(triggerId);
        }
    }

    @Override
    public void removeDampening(String triggerId) {
        if (triggerId == null || triggerId.isEmpty()) {
            throw new IllegalArgumentException("TriggerId must be not null");
        }
        if (dampenings.containsKey(triggerId)) {
            dampenings.remove(triggerId);
        }
    }

    @Override
    public void updateCondition(Condition condition) {
        if (condition == null || condition.getConditionId() == null || condition.getConditionId().isEmpty()) {
            throw new IllegalArgumentException("Condition must be not null");
        }
        if (!conditions.containsKey(condition.getConditionId())) {
            throw new IllegalArgumentException("Condition must exist on repository");
        }
        conditions.put(condition.getConditionId(), condition);
    }

    @Override
    public void updateNotifier(String notifierId, Map<String, String> properties) {
        if (notifierId == null || notifierId.isEmpty()) {
            throw new IllegalArgumentException("NotifierId must be not null");
        }
        if (properties == null) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        if (!notifiers.containsKey(notifierId)) {
            throw new IllegalArgumentException("Notifier must exist on repository");
        }
        notifiers.put(notifierId, properties);
        notifications.register(notifierId, properties);
    }

    @Override
    public void updateNotifierType(String notifierType, Set<String> properties) {
        if (notifierType == null || notifierType.isEmpty()) {
            throw new IllegalArgumentException("NotifierType must be not null");
        }
        if (properties == null) {
            throw new IllegalArgumentException("Properties must be not null");
        }
        if (!notifierTypes.containsKey(notifierType)) {
            throw new IllegalArgumentException("NotifierType must exist on repository");
        }
        notifierTypes.put(notifierType, properties);
    }

    @Override
    public void updateTrigger(Trigger trigger) {
        if (trigger == null || trigger.getId() == null || trigger.getId().isEmpty()) {
            throw new IllegalArgumentException("Trigger must be not null");
        }
        if (!triggers.containsKey(trigger.getId())) {
            throw new IllegalArgumentException("Trigger must exist on repository");
        }
        triggers.put(trigger.getId(), trigger);
    }

    @Override
    public void updateDampening(Dampening dampening) {
        if (dampening == null || dampening.getTriggerId() == null || dampening.getTriggerId().isEmpty()) {
            throw new IllegalArgumentException("Dampening must be not null");
        }
        if (!dampenings.containsKey(dampening.getTriggerId())) {
            throw new IllegalArgumentException("Dampening must exists on repository");
        }
        dampenings.put(dampening.getTriggerId(), dampening);
    }
}
