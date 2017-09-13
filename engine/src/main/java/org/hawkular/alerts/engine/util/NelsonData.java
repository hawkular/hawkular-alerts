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
package org.hawkular.alerts.engine.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.hawkular.alerts.api.model.condition.NelsonCondition;
import org.hawkular.alerts.api.model.condition.NelsonCondition.NelsonRule;
import org.hawkular.alerts.api.model.data.Data;

/**
 * There is one NelsonData for each [active] NelsonCondition in Drools working memory. This gives us the ability to
 * easily configure different sample sizes for different conditions. It also makes the NelsonData life-cycle more
 * straightforward, as it is "tied" to the NelsonCondition. So, if the condition's owning trigger is removed from
 * working memory (e.g. manually disabled, autoDisabled after firing, deleted), then so will be the NelsonCondition
 * and NelsonData.  Note that this means the baseline will be re-established (new samples gathered, new mean and
 * standard deviation) if and when the owning trigger is re-enabled.  One caveat, triggers that have autoResolve do
 * not get removed from working memory, and as such the NelsonData will remain.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class NelsonData {
    private NelsonCondition condition;

    // Currently violated rules for the currently ruleData
    protected List<NelsonRule> violations = new ArrayList<>(8);

    // the last 15 Data used to evaluate the rules. We keep 15 because that is the most needed to eval
    // any of the rules (rule7 uses 15)
    private LinkedList<Data> violationsData = new LinkedList<>();

    private Mean mean = new Mean();
    private StandardDeviation standardDeviation = new StandardDeviation();
    private double oneDeviation;
    private double twoDeviations;
    private double threeDeviations;

    private int rule2Count;
    private int rule3Count;
    private Double rule3PreviousSample;
    private int rule4Count;
    private Double rule4PreviousSample;
    private String rule4PreviousDirection;
    private LinkedList<String> rule5LastThree = new LinkedList<>();
    int rule5Above;
    int rule5Below;
    private LinkedList<String> rule6LastFive = new LinkedList<>();
    int rule6Above;
    int rule6Below;
    private int rule7Count;
    private int rule8Count;

    public NelsonData(NelsonCondition condition) {
        this.condition = condition;
    }

    public void clear() {
        mean.clear();
        standardDeviation.clear();

        violations.clear();

        rule2Count = 0;
        rule3Count = 0;
        rule3PreviousSample = null;
        rule4Count = 0;
        rule4PreviousSample = null;
        rule4PreviousDirection = null;
        rule5LastThree.clear();
        rule5Above = 0;
        rule5Below = 0;
        rule6LastFive.clear();
        rule6Above = 0;
        rule6Below = 0;
        rule7Count = 0;
        rule8Count = 0;
    }

    public boolean hasViolations() {
        return !violations.isEmpty();
    }

    public void addData(Data data) {
        // The rulebase will try to add the same data multiple times (once for each NelsonCondition using
        // the dataId).  Just ignore subsequent attempts.
        if (violationsData.contains(data)) {
            return;
        }

        Double sample;
        try {
            sample = Double.valueOf(data.getValue());
        } catch (Exception e) {
            // not a valid numeric data
            return;
        }

        if (!isValid(sample)) {
            // not a valid Double
            return;
        }

        violationsData.push(data);
        while (violationsData.size() > 15) {
            violationsData.removeLast();
        }

        // System.out.printf("\nViolationsData (size=%s)", violationsData.size());
        // violationsData.stream().forEach(d -> System.out.printf(" \n%d %s", d.getTimestamp(), d.getValue()));
        // System.out.println("");

        addSample(sample.doubleValue());
    }

    private void addSample(double sample) {
        if (mean.getN() < condition.getSampleSize()) {
            mean.increment(sample);
            standardDeviation.increment(sample);

            if (mean.getN() == condition.getSampleSize()) {
                oneDeviation = standardDeviation.getResult();
                twoDeviations = oneDeviation * 2;
                threeDeviations = oneDeviation * 3;
            }
        }

        violations.clear();

        if (rule1(sample)) {
            violations.add(NelsonRule.Rule1);
        }
        if (rule2(sample)) {
            violations.add(NelsonRule.Rule2);
        }
        if (rule3(sample)) {
            violations.add(NelsonRule.Rule3);
        }
        if (rule4(sample)) {
            violations.add(NelsonRule.Rule4);
        }
        if (rule5(sample)) {
            violations.add(NelsonRule.Rule5);
        }
        if (rule6(sample)) {
            violations.add(NelsonRule.Rule6);
        }
        if (rule7(sample)) {
            violations.add(NelsonRule.Rule7);
        }
        if (rule8(sample)) {
            violations.add(NelsonRule.Rule8);
        }
    }

    public boolean hasMean() {
        return mean != null && mean.getN() == condition.getSampleSize();
    }

    // one point is more than 3 standard deviations from the mean
    private boolean rule1(double sample) {
        if (!hasMean()) {
            return false;
        }

        return Math.abs(sample - mean.getResult()) > threeDeviations;
    }

    // Nine (or more) points in a row are on the same side of the mean
    private boolean rule2(double sample) {
        if (!hasMean()) {
            return false;
        }

        if (sample > mean.getResult()) {
            if (rule2Count > 0) {
                ++rule2Count;
            } else {
                rule2Count = 1;
            }
        } else {
            if (rule2Count < 0) {
                --rule2Count;
            } else {
                rule2Count = -1;
            }
        }

        return Math.abs(rule2Count) >= 9;
    }

    // Six (or more) points in a row are continually increasing (or decreasing)
    private boolean rule3(double sample) {
        if (null == rule3PreviousSample) {
            rule3PreviousSample = sample;
            rule3Count = 0;
            return false;
        }

        if (sample > rule3PreviousSample) {
            if (rule3Count > 0) {
                ++rule3Count;
            } else {
                rule3Count = 1;
            }
        } else if (sample < rule3PreviousSample) {
            if (rule3Count < 0) {
                --rule3Count;
            } else {
                rule3Count = -1;
            }
        } else {
            rule3Count = 0;
        }

        rule3PreviousSample = sample;

        return Math.abs(rule3Count) >= 6;
    }

    // Fourteen (or more) points in a row alternate in direction, increasing then decreasing
    private boolean rule4(Double sample) {
        if (null == rule4PreviousSample || sample.doubleValue() == rule4PreviousSample.doubleValue()) {
            rule4PreviousSample = sample;
            rule4PreviousDirection = "=";
            rule4Count = 0;
            return false;
        }

        String sampleDirection = (sample > rule4PreviousSample) ? ">" : "<";

        if (sampleDirection.equals(rule4PreviousDirection)) {
            rule4Count = 0;
        } else {
            ++rule4Count;
        }

        rule4PreviousSample = sample;
        rule4PreviousDirection = sampleDirection;

        return Math.abs(rule4Count) >= 14;
    }

    // At least 2 of 3 points in a row are > 2 standard deviations from the mean in the same direction
    private boolean rule5(double sample) {
        if (!hasMean()) {
            return false;
        }

        if (rule5LastThree.size() == 3) {
            switch (rule5LastThree.removeLast()) {
                case ">":
                    --rule5Above;
                    break;
                case "<":
                    --rule5Below;
                    break;
            }
        }
        if (Math.abs(sample - mean.getResult()) > twoDeviations) {
            if (sample > mean.getResult()) {
                ++rule5Above;
                rule5LastThree.push(">");
            } else {
                ++rule5Below;
                rule5LastThree.push("<");
            }
        } else {
            rule5LastThree.push("");
        }

        return rule5Above >= 2 || rule5Below >= 2;
    }

    // At least 4 of 5 points in a row are > 1 standard deviation from the mean in the same direction
    private boolean rule6(double sample) {
        if (!hasMean()) {
            return false;
        }

        if (rule6LastFive.size() == 5) {
            switch (rule6LastFive.removeLast()) {
                case ">":
                    --rule6Above;
                    break;
                case "<":
                    --rule6Below;
                    break;
            }
        }

        if (Math.abs(sample - mean.getResult()) > oneDeviation) {
            if (sample > mean.getResult()) {
                ++rule6Above;
                rule6LastFive.push(">");
            } else {
                ++rule6Below;
                rule6LastFive.push("<");
            }
        } else {
            rule6LastFive.push("");
        }

        return rule6Above >= 4 || rule6Below >= 4;
    }

    // Fifteen points in a row are all within 1 standard deviation of the mean on either side of the mean
    // Note: I have my doubts about this one wrt monitored metrics, i think it may not be uncommon to have
    // a very steady metric. Minimally, I have taken away the flat-line case where all samples are the mean.
    private boolean rule7(double sample) {
        if (!hasMean()) {
            return false;
        }

        if (sample == mean.getResult()) {
            rule7Count = 0;
            return false;
        }

        if (Math.abs(sample - mean.getResult()) <= oneDeviation) {
            ++rule7Count;
        } else {
            rule7Count = 0;
        }

        return rule7Count >= 15;
    }

    // Eight points in a row exist, but none within 1 standard deviation of the mean
    // and the points are in both directions from the mean
    private boolean rule8(Double sample) {
        if (!hasMean()) {
            return false;
        }

        if (Math.abs(sample - mean.getResult()) > oneDeviation) {
            ++rule8Count;
        } else {
            rule8Count = 0;
        }

        return rule8Count >= 8;
    }

    private boolean isValid(Double d) {
        return null != d && !d.isNaN() && !d.isInfinite();
    }

    public NelsonCondition getCondition() {
        return condition;
    }

    public List<NelsonRule> getViolations() {
        return Collections.unmodifiableList(violations);
    }

    public List<Data> getViolationsData() {
        return Collections.unmodifiableList(violationsData);
    }

    public Mean getMean() {
        return mean;
    }

    public double getMeanResult() {
        return mean.getResult();
    }

    public double getStandardDeviationResult() {
        return oneDeviation;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((condition == null) ? 0 : condition.hashCode());
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
        NelsonData other = (NelsonData) obj;
        if (condition == null) {
            if (other.condition != null)
                return false;
        } else if (!condition.equals(other.condition))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "NelsonData [condition=" + condition + ", violationsData=" + violationsData
                + ", violations=" + violations + ", mean=" + mean + ", standardDeviation=" + oneDeviation
                + ", twoDeviations=" + twoDeviations + ", threeDeviations=" + threeDeviations + "]";
    }

}
