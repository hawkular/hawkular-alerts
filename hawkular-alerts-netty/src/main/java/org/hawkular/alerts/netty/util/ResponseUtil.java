package org.hawkular.alerts.netty.util;

import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.hawkular.alerts.api.json.JsonUtil.toJson;
import static reactor.core.publisher.Mono.just;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.hawkular.alerts.api.json.GroupMemberInfo;
import org.hawkular.alerts.api.json.UnorphanMemberInfo;
import org.hawkular.alerts.api.model.dampening.Dampening;
import org.hawkular.alerts.api.model.event.Event;
import org.hawkular.alerts.api.model.paging.Order;
import org.hawkular.alerts.api.model.paging.Page;
import org.hawkular.alerts.api.model.paging.PageContext;
import org.hawkular.alerts.api.model.paging.Pager;
import org.hawkular.alerts.api.model.trigger.Trigger;
import org.reactivestreams.Publisher;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.netty.handler.codec.http.HttpHeaders;
import reactor.ipc.netty.http.server.HttpServerRequest;
import reactor.ipc.netty.http.server.HttpServerResponse;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ResponseUtil {
    public final static String ACCEPT = "Accept";
    public final static String CONTENT_TYPE = "Content-Type";
    public final static String APPLICATION_JSON = "application/json";

    public static class ApiError {
        @JsonInclude
        private final String errorMsg;

        public ApiError(String errorMsg) {
            this.errorMsg = errorMsg != null && !errorMsg.trim().isEmpty() ? errorMsg : "No details";
        }

        public String getErrorMsg() {
            return errorMsg;
        }
    }

    public static Publisher<Void> badRequest(HttpServerResponse resp, String errorMsg) {
        return resp
                .addHeader(ACCEPT, APPLICATION_JSON)
                .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                .status(BAD_REQUEST)
                .sendString(just(toJson(new ApiError(errorMsg))));
    }

    public static Publisher<Void> internalServerError(HttpServerResponse resp, String errorMsg) {
        return resp
                .addHeader(ACCEPT, APPLICATION_JSON)
                .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                .status(INTERNAL_SERVER_ERROR)
                .sendString(just(toJson(new ApiError(errorMsg))));
    }

    public static Publisher<Void> notFound(HttpServerResponse resp, String errorMsg) {
        return resp
                .addHeader(ACCEPT, APPLICATION_JSON)
                .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                .status(NOT_FOUND)
                .sendString(just(toJson(new ApiError(errorMsg))));
    }

    public static Publisher<Void> ok(HttpServerResponse resp, Object o) {
        return resp
                .addHeader(ACCEPT, APPLICATION_JSON)
                .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                .status(OK)
                .sendString(just(toJson(o)));
    }

    public static Publisher<Void> ok(HttpServerResponse resp) {
        return resp
                .addHeader(ACCEPT, APPLICATION_JSON)
                .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                .status(OK)
                .send();
    }

    public static <T> Publisher<Void> paginatedOk(HttpServerRequest req, HttpServerResponse resp, Page<T> page, String uri) {
        return resp
                .addHeader(ACCEPT, APPLICATION_JSON)
                .addHeader(CONTENT_TYPE, APPLICATION_JSON)
                .status(OK)
                .headers(createPagingHeaders(resp.responseHeaders(), page, uri))
                .sendString(just(toJson(page)));
    }

    public static <T> HttpHeaders createPagingHeaders(HttpHeaders headers, Page<T> resultList, String uri) {

        PageContext pc = resultList.getPageContext();
        int page = pc.getPageNumber();

        List<Link> links = new ArrayList<>();

        if (pc.isLimited() && resultList.getTotalSize() > (pc.getPageNumber() + 1) * pc.getPageSize()) {
            int nextPage = page + 1;
            links.add(new Link("next", replaceQueryParam(uri, "page", String.valueOf(nextPage))));
        }

        if (page > 0) {
            int prevPage = page - 1;
            links.add(new Link("prev", replaceQueryParam(uri, "page", String.valueOf(prevPage))));
        }

        if (pc.isLimited()) {
            long lastPage = resultList.getTotalSize() / pc.getPageSize();
            if (resultList.getTotalSize() % pc.getPageSize() == 0) {
                lastPage -= 1;
            }
            links.add(new Link("last", replaceQueryParam(uri, "page", String.valueOf(lastPage))));
        }

        StringBuilder linkHeader = new StringBuilder(new Link("current", uri).rfc5988String());

        //followed by the rest of the link defined above
        links.forEach((l) -> linkHeader.append(", ").append(l.rfc5988String()));

        headers.remove("Link");
        headers.add("Link", linkHeader.toString());
        headers.remove("X-Total-Count");
        headers.add("X-Total-Count", resultList.getTotalSize());

        return headers;
    }

    public static String replaceQueryParam(String uri, String param, String value) {
        boolean isQuestion = uri.indexOf('?') != -1;
        boolean isPresent = uri.indexOf(param + "=") != -1;
        if (!isQuestion) {
            return uri + "?" + param + "=" + value;
        }
        if (isPresent) {
            String paramToken = uri.substring(uri.indexOf(param));
            int separator = paramToken.indexOf('&');
            if (separator != -1) {
                paramToken = paramToken.substring(0, separator);
            }
            return uri.replace(paramToken, param + "=" + value);
        } else {
            return uri + "&" + param + "=" + value;
        }
    }

    public static Pager extractPaging(Map<String, List<String>> params) {
        String pageS = params.get("page") == null ? null : params.get("page").get(0);
        String perPageS = params.get("per_page") == null ? null : params.get("per_page").get(0);
        List<String> sort = params.get("sort");
        List<String> order = params.get("order");

        int page = pageS == null ? 0 : Integer.parseInt(pageS);
        int perPage = perPageS == null ? PageContext.UNLIMITED_PAGE_SIZE : Integer.parseInt(perPageS);

        List<Order> ordering = new ArrayList<>();

        if (sort == null || sort.isEmpty()) {
            ordering.add(Order.unspecified());
        } else {
            for (int i = 0; i < sort.size(); ++i) {
                String field = sort.get(i);
                Order.Direction dir = Order.Direction.ASCENDING;
                if (order != null && i < order.size()) {
                    dir = Order.Direction.fromShortString(order.get(i));
                }

                ordering.add(Order.by(field, dir));
            }
        }
        return new Pager(page, perPage, ordering);
    }

    public static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public static boolean isEmpty(Collection c) {
        return c == null || c.isEmpty();
    }

    public static boolean isEmpty(Map m) {
        return m == null || m.isEmpty();
    }

    public static Map<String, String> parseTags(String tags) {
        if (isEmpty(tags)) {
            return null;
        }
        String[] tagTokens = tags.split(",");
        Map<String, String> tagsMap = new HashMap<>(tagTokens.length);
        for (String tagToken : tagTokens) {
            String[] fields = tagToken.split("\\|");
            if (fields.length == 2) {
                tagsMap.put(fields[0], fields[1]);
            } else {
                throw new IllegalArgumentException("Invalid Tag Criteria " + Arrays.toString(fields));
            }
        }
        return tagsMap;
    }

    public static String parseTagQuery(Map<String, String> tags) {
        if (isEmpty(tags)) {
            return null;
        }
        StringBuilder tagQuery = new StringBuilder();
        Iterator it = tags.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> tag = (Map.Entry<String, String>)it.next();
            tagQuery.append(tag.getKey());
            if (!"*".equals(tag.getValue())) {
                tagQuery.append(" = ").append("'").append(tag.getValue()).append("'");
            }
            if (it.hasNext()) {
                tagQuery.append(" or ");
            }
        }
        return tagQuery.toString();
    }

    public static boolean checkTags(Event event) {
        return checkTags(event.getTags());
    }

    public static boolean checkTags(Trigger trigger) {
        return checkTags(trigger.getTags());
    }

    public static boolean checkTags(GroupMemberInfo groupMemberInfo) {
        return checkTags(groupMemberInfo.getMemberTags());
    }

    public static boolean checkTags(UnorphanMemberInfo groupMemberInfo) {
        return checkTags(groupMemberInfo.getMemberTags());
    }

    private static boolean checkTags(Map<String, String> tagsMap) {
        if (isEmpty(tagsMap)) {
            return true;
        }
        for (Map.Entry<String, String> entry : tagsMap.entrySet()) {
            if (isEmpty(entry.getKey()) || isEmpty(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    public static Set<String> getTenants(String tenantId) {
        Set<String> tenantIds = new TreeSet<>();
        for (String t : tenantId.split(",")) {
            tenantIds.add(t);
        }
        return tenantIds;
    }

    public static Dampening getCleanDampening(Dampening dampening) throws Exception {
        switch (dampening.getType()) {
            case STRICT:
                return Dampening.forStrict(dampening.getTenantId(), dampening.getTriggerId(),
                        dampening.getTriggerMode(),
                        dampening.getEvalTrueSetting());

            case STRICT_TIME:
                return Dampening.forStrictTime(dampening.getTenantId(), dampening.getTriggerId(),
                        dampening.getTriggerMode(),
                        dampening.getEvalTimeSetting());

            case STRICT_TIMEOUT:
                return Dampening.forStrictTimeout(dampening.getTenantId(), dampening.getTriggerId(),
                        dampening.getTriggerMode(),
                        dampening.getEvalTimeSetting());
            case RELAXED_COUNT:
                return Dampening.forRelaxedCount(dampening.getTenantId(), dampening.getTriggerId(),
                        dampening.getTriggerMode(),
                        dampening.getEvalTrueSetting(),
                        dampening.getEvalTotalSetting());
            case RELAXED_TIME:
                return Dampening.forRelaxedTime(dampening.getTenantId(), dampening.getTriggerId(),
                        dampening.getTriggerMode(),
                        dampening.getEvalTrueSetting(), dampening.getEvalTimeSetting());

            default:
                throw new Exception("Unhandled Dampening Type: " + dampening.toString());
        }
    }

    public static Publisher<Void> handleExceptions(HttpServerResponse resp, Throwable e) {
        if (e instanceof BadRequestException) {
            return badRequest(resp, e.toString());
        }
        if (e instanceof NotFoundException) {
            return notFound(resp, e.getMessage());
        }
        return internalServerError(resp, e.toString());
    }

    public static class BadRequestException extends RuntimeException {

        public BadRequestException(String message) {
            super(message);
        }

        public BadRequestException(Throwable cause) {
            super(cause);
        }

        public BadRequestException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class InternalServerException extends RuntimeException {

        public InternalServerException(String message) {
            super(message);
        }

        public InternalServerException(Throwable cause) {
            super(cause);
        }

        public InternalServerException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class NotFoundException extends RuntimeException {

        public NotFoundException(String message) {
            super(message);
        }

        public NotFoundException(Throwable cause) {
            super(cause);
        }

        public NotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
