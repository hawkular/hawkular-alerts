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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hawkular.alerts.api.model.condition.ConditionEval;

/**
 * A representation of dampening status.
 *
 * @author Jay Shaughnessy
 */
public class Dampening {

    public enum Type {
        STRICT, RELAXED_COUNT, RELAXED_TIME
    };

    private String triggerId;
    private Type type;
    private int evalTrueSetting;
    private int evalTotalSetting;
    private long evalTimeSetting;

    private int numTrueEvals;
    private int numEvals;
    private long trueEvalsStartTime;
    private boolean satisfied;
    private List<Set<ConditionEval>> satisfyingEvals = new ArrayList<Set<ConditionEval>>();

    public Dampening() {
        this("Default", Type.RELAXED_COUNT, 0, 0, 0);
    }

    /**
     * Fire if we have <code>numTrueEvals</code> consecutive true evaluations of the condition set.
     * @param triggerId
     * @param numConsecutiveTrueEvals
     * @return
     */
    public static Dampening forStrict(String triggerId, int numConsecutiveTrueEvals) {
        return new Dampening(triggerId, Type.STRICT, numConsecutiveTrueEvals, numConsecutiveTrueEvals, 0);
    }

    /**
     * Fire if we have <code>numTrueEvals</code> of the condition set out of <code>numTotalEvals</code>.
     * @param triggerId
     * @param numTrueEvals
     * @param numTotalEvals
     * @return
     */
    public static Dampening forRelaxedCount(String triggerId, int numTrueEvals, int numTotalEvals) {
        return new Dampening(triggerId, Type.RELAXED_COUNT, numTrueEvals, numTotalEvals, 0);
    }

    /**
     * Fire if we have <code>numTrueEvals</code> of the condition set within <code>evalTimeSetting</code>.
     * @param triggerId
     * @param numTrueEvals
     * @param evalPeriod Elapsed real time, in milliseconds. In other words, this is not measured against
     * collectionTimes (i.e. the timestamp on the data) but rather the evaluation times.
     * evaluation times.
     * @return
     */
    public static Dampening forRelaxedTime(String triggerId, int numTrueEvals, long evalPeriod) {
        return new Dampening(triggerId, Type.RELAXED_TIME, numTrueEvals, 0, evalPeriod);
    }

    public Dampening(String triggerId, Type type, int evalTrueSetting, int evalTotalSetting, long evalTimeSetting) {
        super();
        this.triggerId = triggerId;
        this.type = type;
        this.evalTrueSetting = evalTrueSetting;
        this.evalTotalSetting = evalTotalSetting;
        this.evalTimeSetting = evalTimeSetting;

        reset();
    }

    public String getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(String triggerId) {
        this.triggerId = triggerId;
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

    public int getNumTrueEvals() {
        return numTrueEvals;
    }

    public void setNumTrueEvals(int numTrueEvals) {
        this.numTrueEvals = numTrueEvals;
    }

    public long getTrueEvalsStartTime() {
        return trueEvalsStartTime;
    }

    public void setTrueEvalsStartTime(long trueEvalsStartTime) {
        this.trueEvalsStartTime = trueEvalsStartTime;
    }

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

    public boolean isSatisfied() {
        return satisfied;
    }

    /**
     * @return a safe, but not deep, copy of the satisfying evals List
     */
    public List<Set<ConditionEval>> getSatisfyingEvals() {
        return new ArrayList<Set<ConditionEval>>(satisfyingEvals);
    }

    public void addSatisfyingEvals(Set<ConditionEval> satisfyingEvals) {
        this.satisfyingEvals.add(satisfyingEvals);
    }

    public void addSatisfyingEvals(ConditionEval... satisfyingEvals) {
        this.satisfyingEvals.add(new HashSet<ConditionEval>(Arrays.asList(satisfyingEvals)));
    }

    public void perform(ConditionEval... conditionEvals) {
        boolean trueEval = true;
        for (ConditionEval ce : conditionEvals) {
            if (!ce.isMatch()) {
                trueEval = false;
                break;
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
            addSatisfyingEvals(conditionEvals);

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
            }
        } else {
            switch (type) {
                case STRICT:
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

    @Override
    public String toString() {
        return "Dampening [triggerId=" + triggerId + ", type=" + type + ", evalTrueSetting=" + evalTrueSetting
                + ", evalTotalSetting=" + evalTotalSetting + ", evalTimeSetting=" + evalTimeSetting
                + ", numTrueEvals="
                + numTrueEvals + ", numEvals=" + numEvals + ", trueEvalsStartTime=" + trueEvalsStartTime
                + ", satisfied=" + satisfied + ", satisfyingEvals=" + satisfyingEvals + "]";
    }

}