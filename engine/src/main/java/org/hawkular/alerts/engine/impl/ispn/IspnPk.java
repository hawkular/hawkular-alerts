package org.hawkular.alerts.engine.impl.ispn;

import org.hawkular.alerts.api.model.action.ActionDefinition;
import org.hawkular.alerts.engine.impl.ispn.model.ActionPlugin;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IspnPk {

    public static String pk(String actionPlugin) {
        if (actionPlugin == null) {
            return null;
        }
        return new StringBuilder("ActionPlugin-").append(actionPlugin).toString();
    }

    public static String pk(ActionDefinition actionDefinition) {
        if (actionDefinition == null) {
            return null;
        }
        return new StringBuilder("ActionDefinition-")
                .append(actionDefinition.getTenantId())
                .append("-")
                .append(actionDefinition.getActionPlugin())
                .append("-")
                .append(actionDefinition.getActionId())
                .toString();
    }

    public static String pk(String tenantId, String actionPlugin, String actionId) {
        if (tenantId == null || actionPlugin == null || actionId == null) {
            return null;
        }
        return new StringBuilder("ActionDefinition-")
                .append(tenantId)
                .append("-")
                .append(actionPlugin)
                .append("-")
                .append(actionId)
                .toString();
    }

}
