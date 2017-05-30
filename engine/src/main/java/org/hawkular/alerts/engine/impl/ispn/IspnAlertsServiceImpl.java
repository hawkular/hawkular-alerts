package org.hawkular.alerts.engine.impl.ispn;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hawkular.alerts.api.model.condition.ConditionEval;
import org.hawkular.alerts.api.model.data.Data;
import org.hawkular.alerts.api.model.event.Alert;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.services.AlertsCriteria;
import org.hawkular.alerts.api.services.AlertsService;
import org.hawkular.alerts.api.services.EventsCriteria;
import org.hawkular.alerts.log.AlertingLogger;
import org.hawkular.commons.log.MsgLogging;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class IspnAlertsServiceImpl implements AlertsService {
    private static final AlertingLogger log = MsgLogging.getMsgLogger(AlertingLogger.class, IspnAlertsServiceImpl.class);

    @Override
    public void ackAlerts(String tenantId, Collection<String> alertIds, String ackBy, String ackNotes) throws Exception {

    }

    @Override
    public void addAlerts(Collection<Alert> alerts) throws Exception {

    }

    @Override
    public void addAlertTags(String tenantId, Collection<String> alertIds, Map<String, String> tags) throws Exception {

    }

    @Override
    public void addEvents(Collection<Event> events) throws Exception {

    }

    @Override
    public void addEventTags(String tenantId, Collection<String> eventIds, Map<String, String> tags) throws Exception {

    }

    @Override
    public void persistEvents(Collection<Event> events) throws Exception {

    }

    @Override
    public void addNote(String tenantId, String alertId, String user, String text) throws Exception {

    }

    @Override
    public int deleteAlerts(String tenantId, AlertsCriteria criteria) throws Exception {
        return 0;
    }

    @Override
    public int deleteEvents(String tenantId, EventsCriteria criteria) throws Exception {
        return 0;
    }

    @Override
    public Alert getAlert(String tenantId, String alertId, boolean thin) throws Exception {
        return null;
    }

    @Override
    public Page<Alert> getAlerts(String tenantId, AlertsCriteria criteria, Pager pager) throws Exception {
        return null;
    }

    @Override
    public Page<Alert> getAlerts(Set<String> tenantIds, AlertsCriteria criteria, Pager pager) throws Exception {
        return null;
    }

    @Override
    public Event getEvent(String tenantId, String eventId, boolean thin) throws Exception {
        return null;
    }

    @Override
    public Page<Event> getEvents(String tenantId, EventsCriteria criteria, Pager pager) throws Exception {
        return null;
    }

    @Override
    public Page<Event> getEvents(Set<String> tenantIds, EventsCriteria criteria, Pager pager) throws Exception {
        return null;
    }

    @Override
    public void removeAlertTags(String tenantId, Collection<String> alertIds, Collection<String> tags) throws Exception {

    }

    @Override
    public void removeEventTags(String tenantId, Collection<String> eventIds, Collection<String> tags) throws Exception {

    }

    @Override
    public void resolveAlerts(String tenantId, Collection<String> alertIds, String resolvedBy, String resolvedNotes, List<Set<ConditionEval>> resolvedEvalSets) throws Exception {

    }

    @Override
    public void resolveAlertsForTrigger(String tenantId, String triggerId, String resolvedBy, String resolvedNotes, List<Set<ConditionEval>> resolvedEvalSets) throws Exception {

    }

    @Override
    public void sendData(Collection<Data> data) throws Exception {

    }

    @Override
    public void sendData(Collection<Data> data, boolean ignoreFiltering) throws Exception {

    }

    @Override
    public void sendEvents(Collection<Event> events) throws Exception {

    }

    @Override
    public void sendEvents(Collection<Event> events, boolean ignoreFiltering) throws Exception {

    }
}
