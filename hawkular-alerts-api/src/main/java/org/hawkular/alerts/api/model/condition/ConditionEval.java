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
    protected long time;
    // flag noting whether this condition eval was used in a tested Tuple and already applied to dampening
    protected boolean used;

    public ConditionEval(boolean match) {
        this.match = match;
        this.time = System.currentTimeMillis();
        this.used = false;
    }

    public boolean isMatch() {
        return match;
    }

    public void setMatch(boolean match) {
        this.match = match;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
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
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ConditionEval that = (ConditionEval) o;

        if (match != that.match)
            return false;
        if (time != that.time)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = (match ? 1 : 0);
        result = 31 * result + (int) (time ^ (time >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "ConditionEval [match=" + match + ", time=" + time + ", used=" + used + "]";
    }

}
