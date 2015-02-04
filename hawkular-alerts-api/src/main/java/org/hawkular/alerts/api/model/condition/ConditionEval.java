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
package org.hawkular.alerts.api.model.condition;

/**
 * An evaluation state of a specific condition.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public abstract class ConditionEval {

    // result of the condition evaluation
    protected boolean match;
    // time of condition evaluation (i.e. creation time)
    protected long evalTimestamp;
    // time stamped on the data used in the eval
    protected long dataTimestamp;
    // flag noting whether this condition eval was used in a tested Tuple and already applied to dampening
    protected boolean used;

    public ConditionEval(boolean match, long dataTimestamp) {
        this.match = match;
        this.dataTimestamp = dataTimestamp;
        this.evalTimestamp = System.currentTimeMillis();
        this.used = false;
    }

    public boolean isMatch() {
        return match;
    }

    public void setMatch(boolean match) {
        this.match = match;
    }

    public long getEvalTimestamp() {
        return evalTimestamp;
    }

    public void setEvalTimestamp(long evalTimestamp) {
        this.evalTimestamp = evalTimestamp;
    }

    public long getDataTimestamp() {
        return dataTimestamp;
    }

    public void setDataTimestamp(long dataTimestamp) {
        this.dataTimestamp = dataTimestamp;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public abstract String getTriggerId();

    public abstract int getConditionSetSize();

    public abstract int getConditionSetIndex();

    public abstract String getLog();

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (dataTimestamp ^ (dataTimestamp >>> 32));
        result = prime * result + (int) (evalTimestamp ^ (evalTimestamp >>> 32));
        result = prime * result + (match ? 1231 : 1237);
        result = prime * result + (used ? 1231 : 1237);
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
        ConditionEval other = (ConditionEval) obj;
        if (dataTimestamp != other.dataTimestamp)
            return false;
        if (evalTimestamp != other.evalTimestamp)
            return false;
        if (match != other.match)
            return false;
        if (used != other.used)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "ConditionEval [match=" + match + ", evalTimestamp=" + evalTimestamp + ", dataTimestamp="
                + dataTimestamp + ", used=" + used + "]";
    }

}
