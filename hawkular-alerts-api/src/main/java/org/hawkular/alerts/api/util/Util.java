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
package org.hawkular.alerts.api.util;

import java.util.Collection;
import java.util.Map;

import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.trigger.Trigger;

/**
 * Unify some utility methods used in several components related to API model or generic validations
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class Util {

    public static boolean isEmpty(String s) {
        return null == s || s.trim().isEmpty();
    }

    public static boolean isEmpty(Collection c) {
        return null == c || c.isEmpty();
    }

    public static boolean isEmpty(Map m) {
        return m == null || m.isEmpty();
    }

    public static boolean isEmpty(String... strings) {
        for (String s : strings) {
            if (null == s || s.trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEmpty(ActionDefinition a) {
        return a == null || isEmpty(a.getActionPlugin()) || isEmpty(a.getActionId());
    }

    public static boolean isEmpty(Dampening dampening) {
        return dampening == null || isEmpty(dampening.getTriggerId()) || isEmpty(dampening.getDampeningId());
    }

    public static boolean isEmpty(Trigger trigger) {
        return trigger == null || isEmpty(trigger.getId());
    }

}
