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
package org.hawkular.alerts.api.model.condition;

import static org.hawkular.alerts.api.util.Util.isEmpty;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hawkular.alerts.api.model.trigger.Mode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

/**
 * A condition that uses historically collected data to perform a variety of tests for value instability. See:
 * https://en.wikipedia.org/wiki/Nelson_rules.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
@ApiModel(description = "A condition to detect instability based on historical data. + \n" +
        " + \n" +
        "From one to all of the defined Nelson rules can be evaluated. See + \n" +
        "https://en.wikipedia.org/wiki/Nelson_rules for a description of the rules.")
public class NelsonCondition extends Condition {
    private static final long serialVersionUID = 1L;

    public enum NelsonRule {
        Rule1("One point is more than 3 standard deviations from the mean"), //
        Rule2("Nine (or more) points in a row are on the same side of the mean."), //
        Rule3("Six (or more) points in a row are continually increasing (or decreasing)."), //
        Rule4("Fourteen (or more) points in a row alternate in direction, increasing then decreasing."), //
        Rule5("At least 2 of 3 points in a row are > 2 standard deviations from the mean in the same direction."), //
        Rule6("At least 4 of 5 points in a row are > 1 standard deviation from the mean in the same direction."), //
        Rule7("Fifteen points in a row are all within 1 standard deviation of the mean on either side of the mean."), //
        Rule8("Eight points in a row exist, but none within 1 standard deviation of the mean, "
                + "and the points are in both directions from the mean.");

        private String description;

        NelsonRule(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private static final int DEFAULT_SAMPLE_SIZE = 50;
    private static final Set<NelsonRule> DEFAULT_ACTIVE_RULES = EnumSet.allOf(NelsonRule.class);

    @JsonInclude(Include.NON_NULL)
    private String dataId;

    @ApiModelProperty(value = "Set of NelsonRule to evaluate.",
            position = 1,
            example = "All Rules",
            required = false)
    @JsonInclude(Include.NON_NULL)
    private Set<NelsonRule> activeRules;

    @ApiModelProperty(value = "Number of samples used to establish baseline information (mean, standard deviation).",
            position = 2,
            example = "50",
            required = false)
    @JsonInclude(Include.NON_NULL)
    private int sampleSize;

    /**
     * Used for JSON deserialization, not for general use.
     */
    public NelsonCondition() {
        /*
            Default constructor is needed for JSON libraries in JAX-RS context.
         */
        this("", "", Mode.FIRING, 1, 1, null, null, null);
    }

    public NelsonCondition(String tenantId, String triggerId, String dataId) {

        this(tenantId, triggerId, Mode.FIRING, 1, 1, dataId, null, null);
    }

    public NelsonCondition(String tenantId, String triggerId, String dataId, Set<NelsonRule> activeRules) {

        this(tenantId, triggerId, Mode.FIRING, 1, 1, dataId, activeRules, null);
    }

    public NelsonCondition(String tenantId, String triggerId, String dataId, Set<NelsonRule> activeRules,
            Integer sampleSize) {

        this(tenantId, triggerId, Mode.FIRING, 1, 1, dataId, activeRules, sampleSize);
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     */
    public NelsonCondition(String triggerId, String dataId, Set<NelsonRule> activeRules) {

        this("", triggerId, Mode.FIRING, 1, 1, dataId, activeRules, null);
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     */
    public NelsonCondition(String triggerId, String dataId, Set<NelsonRule> activeRules, Integer sampleSize) {

        this("", triggerId, Mode.FIRING, 1, 1, dataId, activeRules, sampleSize);
    }

    /**
     * This constructor requires the tenantId be assigned prior to persistence. It can be used when
     * creating triggers via Rest, as the tenant will be assigned automatically.
     */
    public NelsonCondition(String triggerId, Mode triggerMode, int conditionSetSize, int conditionSetIndex,
            String dataId, Set<NelsonRule> activeRules, Integer sampleSize) {

        this("", triggerId, triggerMode, conditionSetSize, conditionSetIndex, dataId, activeRules, sampleSize);
    }

    public NelsonCondition(String tenantId, String triggerId, Mode triggerMode, int conditionSetSize,
            int conditionSetIndex, String dataId, Set<NelsonRule> activeRules, Integer sampleSize) {

        super(tenantId, triggerId, (null == triggerMode ? Mode.FIRING : triggerMode), conditionSetSize,
                conditionSetIndex, Type.NELSON);
        this.dataId = dataId;
        setActiveRules(activeRules);
        setSampleSize(sampleSize);
        updateDisplayString();
    }

    public NelsonCondition(NelsonCondition condition) {
        super(condition);

        this.activeRules = new HashSet<>(condition.getActiveRules());
        this.dataId = condition.getDataId();
        this.sampleSize = condition.getSampleSize();
    }

    @Override
    public String getDataId() {
        return dataId;
    }

    public void setDataId(String dataId) {
        this.dataId = dataId;
    }

    public Set<NelsonRule> getActiveRules() {
        return activeRules;
    }

    public void setActiveRules(Set<NelsonRule> activeRules) {
        this.activeRules = (null == activeRules || activeRules.isEmpty()) ? DEFAULT_ACTIVE_RULES : activeRules;
    }

    public int getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(Integer sampleSize) {
        this.sampleSize = (null == sampleSize || sampleSize < 1) ? DEFAULT_SAMPLE_SIZE : sampleSize;
    }

    public boolean match(List<NelsonRule> violations) {
        if (isEmpty(violations) || isEmpty(activeRules)) {
            return false;
        }

        for (NelsonRule r : violations) {
            if (activeRules.contains(r)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void updateDisplayString() {
        String s = String.format("%s activeNelsonRules=%s sampleSize=%d", this.dataId,
                this.activeRules.stream().map(e -> e.name()).collect(Collectors.toSet()), this.sampleSize);
        setDisplayString(s);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((activeRules == null) ? 0 : activeRules.hashCode());
        result = prime * result + ((dataId == null) ? 0 : dataId.hashCode());
        result = prime * result + sampleSize;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        NelsonCondition other = (NelsonCondition) obj;
        if (activeRules == null) {
            if (other.activeRules != null)
                return false;
        } else if (!activeRules.equals(other.activeRules))
            return false;
        if (dataId == null) {
            if (other.dataId != null)
                return false;
        } else if (!dataId.equals(other.dataId))
            return false;
        if (sampleSize != other.sampleSize)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "NelsonCondition [dataId=" + dataId + ", activeRules=" + activeRules + ", sampleSize=" + sampleSize
                + "]";
    }

}
