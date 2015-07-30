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
package org.hawkular.alerts.api.model.dampening;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.trigger.Trigger.Mode;
import org.hawkular.alerts.api.model.trigger.TriggerTemplate.Match;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.wordnik.swagger.annotations.ApiModelProperty;

/**
 * A representation of dampening status.
 *
 * @author Jay Shaughnessy
 */
public class Dampening {

    public enum Type {
        STRICT, RELAXED_COUNT, RELAXED_TIME, STRICT_TIME, STRICT_TIMEOUT
    };

    @JsonInclude
    private String triggerId;

    @JsonInclude
    private Mode triggerMode;

    @JsonInclude
    private Type type;

    @JsonInclude
    @ApiModelProperty(
            value = "Number of required true evaluations for STRICT, RELAXED_COUNT, RELAXED_TIME",
            allowableValues = ">= 1")
    private int evalTrueSetting;

    @JsonInclude
    @ApiModelProperty(
            value = "Number of allowed evaluation attempts for RELAXED_COUNT",
            allowableValues = "> evalTrueSetting")
    private int evalTotalSetting;

    @JsonInclude
    @ApiModelProperty(
            value = "Time period in milliseconds for RELAXED_TIME, STRICT_TIME, STRICT_TIMEOUT",
            allowableValues = "> 0")
    private long evalTimeSetting;

    /**
     * A composed key for the dampening
     */
    @JsonInclude
    protected String dampeningId;

    // The following fields are only relevant while the engine is executing.
    @JsonIgnore
    private transient int numTrueEvals;

    @JsonIgnore
    private transient int numEvals;

    @JsonIgnore
    private transient long trueEvalsStartTime;

    @JsonIgnore
    // This Map<conditionSetIndex,ConditionEval> holds the most recent eval for each member of the condition set
    private transient Map<Integer, ConditionEval> currentEvals = new HashMap<>(5);

    @JsonIgnore
    private transient boolean satisfied;

    @JsonIgnore
    private transient List<Set<ConditionEval>> satisfyingEvals = new ArrayList<Set<ConditionEval>>();

    @JsonInclude
    private String tenantId;

    public Dampening() {
        this("Default", Mode.FIRING, Type.STRICT, 1, 1, 0);
    }

    /**
     * Fire if we have <code>numTrueEvals</code> consecutive true evaluations of the condition set.  There is
     * no time limit for the evaluations.
     * @param triggerId the triggerId
     * @param triggerMode the trigger mode for when this dampening is active
     * @param numConsecutiveTrueEvals the numConsecutiveTrueEvals
     * @return the configured Dampening
     */
    public static Dampening forStrict(String triggerId, Mode triggerMode, int numConsecutiveTrueEvals) {
        return new Dampening(triggerId, triggerMode, Type.STRICT, numConsecutiveTrueEvals, numConsecutiveTrueEvals, 0);
    }

    /**
     * Fire if we have <code>numTrueEvals</code> of the condition set out of <code>numTotalEvals</code>. There is
     * no time limit for the evaluations.
     * @param triggerId the triggerId
     * @param triggerMode the trigger mode for when this dampening is active
     * @param numTrueEvals the numTrueEvals
     * @param numTotalEvals the numTotalEvals
     * @return the configured Dampening
     */
    public static Dampening forRelaxedCount(String triggerId, Mode triggerMode, int numTrueEvals, int numTotalEvals) {
        return new Dampening(triggerId, triggerMode, Type.RELAXED_COUNT, numTrueEvals, numTotalEvals, 0);
    }

    /**
     * Fire if we have <code>numTrueEvals</code> of the condition set within <code>evalPeriod</code>. This can only
     * fire if the condition set is evaluated the required number of times in the given <code>evalPeriod</code>, so
     * the requisite data must be supplied in a timely manner.
     * @param triggerId the triggerId
     * @param triggerMode the trigger mode for when this dampening is active
     * @param numTrueEvals the numTrueEvals
     * @param evalPeriod Elapsed real time, in milliseconds. In other words, this is not measured against
     * collectionTimes (i.e. the timestamp on the data) but rather the evaluation times.
     * @return the configured Dampening
     */
    public static Dampening forRelaxedTime(String triggerId, Mode triggerMode, int numTrueEvals, long evalPeriod) {
        return new Dampening(triggerId, triggerMode, Type.RELAXED_TIME, numTrueEvals, 0, evalPeriod);
    }

    /**
     * Fire if we have only true evaluations of the condition set for at least <code>evalPeriod</code>.  In other
     * words, fire the Trigger after N consecutive true condition set evaluations, such that <code>N GTE 2</code>
     * and <code>delta(evalTime-1,evalTime-N) GTE evalPeriod</code>.  Any false evaluation resets the dampening.
     * @param triggerId the triggerId
     * @param triggerMode the trigger mode for when this dampening is active
     * @param evalPeriod Elapsed real time, in milliseconds. In other words, this is not measured against
     * collectionTimes (i.e. the timestamp on the data) but rather the evaluation times.
     * @return the configured Dampening
     */
    public static Dampening forStrictTime(String triggerId, Mode triggerMode, long evalPeriod) {
        return new Dampening(triggerId, triggerMode, Type.STRICT_TIME, 0, 0, evalPeriod);
    }

    /**
     * Fire if we have only true evaluations of the condition set for <code>evalPeriod</code>.  In other
     * words, fire the Trigger after N consecutive true condition set evaluations, such that <code>N GTE 1</code>
     * and <code>delta(evalTime-1,currentTime) == evalPeriod</code>.  Any false evaluation resets the dampening.
     * @param triggerId the triggerId
     * @param triggerMode the trigger mode for when this dampening is active
     * @param evalPeriod Elapsed real time, in milliseconds. In other words, this is not measured against
     * collectionTimes (i.e. the timestamp on the data) but rather the clock starts at true-evaluation-time-1.
     * @return the configured Dampening
     */
    public static Dampening forStrictTimeout(String triggerId, Mode triggerMode, long evalPeriod) {
        return new Dampening(triggerId, triggerMode, Type.STRICT_TIMEOUT, 0, 0, evalPeriod);
    }

    public Dampening(String triggerId, Mode triggerMode, Type type, int evalTrueSetting, int evalTotalSetting,
            long evalTimeSetting) {
        super();
        this.triggerId = triggerId;
        this.type = type;
        this.evalTrueSetting = evalTrueSetting;
        this.evalTotalSetting = evalTotalSetting;
        this.evalTimeSetting = evalTimeSetting;
        this.triggerMode = triggerMode;
        updateId();

        reset();
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
        updateId();
    }

    public Mode getTriggerMode() {
        return triggerMode;
    }

    public void setTriggerMode(Mode triggerMode) {
        this.triggerMode = triggerMode;
        updateId();
    }

    public void setEvalTimeSetting(long evalTimeSetting) {
        this.evalTimeSetting = evalTimeSetting;
    }

    public void setEvalTotalSetting(int evalTotalSetting) {
        this.evalTotalSetting = evalTotalSetting;
    }

    public void setEvalTrueSetting(int evalTrueSetting) {
        this.evalTrueSetting = evalTrueSetting;
    }

    public void setSatisfied(boolean satisfied) {
        this.satisfied = satisfied;
    }

    public void setSatisfyingEvals(List<Set<ConditionEval>> satisfyingEvals) {
        this.satisfyingEvals = satisfyingEvals;
    }

    public void setType(Type type) {
        this.type = type;
    }

    @JsonIgnore
    public int getNumTrueEvals() {
        return numTrueEvals;
    }

    public void setNumTrueEvals(int numTrueEvals) {
        this.numTrueEvals = numTrueEvals;
    }

    @JsonIgnore
    public long getTrueEvalsStartTime() {
        return trueEvalsStartTime;
    }

    public void setTrueEvalsStartTime(long trueEvalsStartTime) {
        this.trueEvalsStartTime = trueEvalsStartTime;
    }

    @JsonIgnore
    public int getNumEvals() {
        return numEvals;
    }

    public void setNumEvals(int numEvals) {
        this.numEvals = numEvals;
    }

    public Type getType() {
        return type;
    }

    public int getEvalTrueSetting() {
        return evalTrueSetting;
    }

    public int getEvalTotalSetting() {
        return evalTotalSetting;
    }

    public long getEvalTimeSetting() {
        return evalTimeSetting;
    }

    public Map<Integer, ConditionEval> getCurrentEvals() {
        return currentEvals;
    }

    @JsonIgnore
    public boolean isSatisfied() {
        return satisfied;
    }

    /**
     * @return a safe, but not deep, copy of the satisfying evals List
     */
    @JsonIgnore
    public List<Set<ConditionEval>> getSatisfyingEvals() {
        return new ArrayList<Set<ConditionEval>>(satisfyingEvals);
    }

    public void addSatisfyingEvals(Set<ConditionEval> satisfyingEvals) {
        this.satisfyingEvals.add(satisfyingEvals);
    }

    public void addSatisfyingEvals(ConditionEval... satisfyingEvals) {
        this.satisfyingEvals.add(new HashSet<ConditionEval>(Arrays.asList(satisfyingEvals)));
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public void perform(Match match, ConditionEval conditionEval) {
        if (null == match) {
            throw new IllegalArgumentException("Match can not be null");
        }
        if (null == conditionEval) {
            throw new IllegalArgumentException("ConditionEval can not be null");
        }

        // The currentEvals map holds the most recent eval for each condition in the condition set.
        currentEvals.put(conditionEval.getConditionSetIndex(), conditionEval);

        boolean trueEval = false;
        switch (match) {
            case ALL:
                // Don't perform a dampening eval until we have a conditionEval for each member of the ConditionSet.
                if (currentEvals.size() < conditionEval.getConditionSetSize()) {
                    return;
                }
                // Otherwise, all condition evals must be true for the condition set eval to be true
                trueEval = true;
                for (ConditionEval ce : currentEvals.values()) {
                    if (!ce.isMatch()) {
                        trueEval = false;
                        break;
                    }
                }
                break;
            case ANY:
                // we only need one true condition eval for the condition set eval to be true
                trueEval = false;
                for (ConditionEval ce : currentEvals.values()) {
                    if (ce.isMatch()) {
                        trueEval = true;
                        break;
                    }
                }
                break;
            default:
                throw new IllegalArgumentException("Unexpected Match type: " + match.name());
        }

        if (Match.ALL == match) {
        } else {
            for (ConditionEval ce : currentEvals.values()) {

                if (!ce.isMatch()) {
                    trueEval = false;
                    break;
                }
            }
        }

        // If we had previously started our time and now have exceeded our time limit then we must start over
        long now = System.currentTimeMillis();
        if (type == Type.RELAXED_TIME && trueEvalsStartTime != 0L) {
            if ((now - trueEvalsStartTime) > evalTimeSetting) {
                reset();
            }
        }

        numEvals += 1;
        if (trueEval) {
            numTrueEvals += 1;
            addSatisfyingEvals(new HashSet<>(currentEvals.values()));

            switch (type) {
                case STRICT:
                case RELAXED_COUNT:
                    if (numTrueEvals == evalTrueSetting) {
                        satisfied = true;
                    }
                    break;

                case RELAXED_TIME:
                    if (trueEvalsStartTime == 0L) {
                        trueEvalsStartTime = now;
                    }
                    if ((numTrueEvals == evalTrueSetting) && ((now - trueEvalsStartTime) < evalTimeSetting)) {
                        satisfied = true;
                    }
                    break;
                case STRICT_TIME:
                case STRICT_TIMEOUT:
                    if (trueEvalsStartTime == 0L) {
                        trueEvalsStartTime = now;

                    } else if ((now - trueEvalsStartTime) >= evalTimeSetting) {
                        satisfied = true;
                    }
                    break;
            }
        } else {
            switch (type) {
                case STRICT:
                case STRICT_TIME:
                case STRICT_TIMEOUT:
                    reset();
                    break;
                case RELAXED_COUNT:
                    int numNeeded = evalTrueSetting - numTrueEvals;
                    int chancesLeft = evalTotalSetting - numEvals;
                    if (numNeeded > chancesLeft) {
                        reset();
                    }
                    break;
                case RELAXED_TIME:
                    break;
            }
        }
    }

    public void reset() {
        this.numTrueEvals = 0;
        this.numEvals = 0;
        this.trueEvalsStartTime = 0L;
        this.satisfied = false;
        this.satisfyingEvals.clear();
    }

    public String log() {
        StringBuilder sb = new StringBuilder("[" + triggerId + ", numTrueEvals="
                + numTrueEvals + ", numEvals=" + numEvals + ", trueEvalsStartTime=" + trueEvalsStartTime
                + ", satisfied=" + satisfied);
        if (satisfied) {
            for (Set<ConditionEval> ces : satisfyingEvals) {
                sb.append("\n\t[");
                String space = "";
                for (ConditionEval ce : ces) {
                    sb.append(space);
                    sb.append("[");
                    sb.append(ce.getLog());
                    sb.append("]");
                    space = " ";
                }
                sb.append("]");

            }
        }
        return sb.toString();
    }

    public String getDampeningId() {
        return dampeningId;
    }

    private void updateId() {
        StringBuilder sb = new StringBuilder(triggerId);
        sb.append("-").append(triggerMode.name());
        this.dampeningId = sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dampeningId == null) ? 0 : dampeningId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Dampening other = (Dampening)obj;
        if (dampeningId == null) {
            if (other.dampeningId != null)
                return false;
        } else if (!dampeningId.equals(other.dampeningId))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "Dampening [satisfied=" + satisfied + ", triggerId=" + triggerId + ", triggerMode=" + triggerMode
                + ", type=" + type
                + ", evalTrueSetting=" + evalTrueSetting + ", evalTotalSetting=" + evalTotalSetting
                + ", evalTimeSetting=" + evalTimeSetting + ", numTrueEvals="
                + numTrueEvals + ", numEvals=" + numEvals + ", trueEvalsStartTime=" + trueEvalsStartTime + "]";
    }

}
