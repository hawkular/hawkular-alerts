package org.hawkular.alerts.engine.impl.ispn;

import org.hawkular.alerts.api.model.action.Action;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.hawkular.alerts.api.services.ActionListener;
import org.hawkular.alerts.api.services.ActionsCriteria;
import org.hawkular.alerts.api.services.ActionsService;
import org.hawkular.alerts.log.AlertingLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * Infinispan implementation of {@link org.hawkular.alerts.api.services.ActionsService}.
 *
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IspnActionsServiceImpl implements ActionsService {
    private final AlertingLogger log = MsgLogging.getMsgLogger(AlertingLogger.class, IspnActionsServiceImpl.class);

    @Override
    public void send(Trigger trigger, Event event) {

    }

    @Override
    public void updateResult(Action action) {

    }

    @Override
    public Page<Action> getActions(String tenantId, ActionsCriteria criteria, Pager pager) throws Exception {
        return null;
    }

    @Override
    public int deleteActions(String tenantId, ActionsCriteria criteria) throws Exception {
        return 0;
    }

    @Override
    public void addListener(ActionListener listener) {

    }
}
