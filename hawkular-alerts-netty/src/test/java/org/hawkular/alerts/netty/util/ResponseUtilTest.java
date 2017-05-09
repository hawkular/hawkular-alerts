package org.hawkular.alerts.netty.util;

import static org.hawkular.alerts.netty.util.ResponseUtil.replaceQueryParam;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ResponseUtilTest {

    @Test
    public void replaceQueryParams() {
        String uri = "http://server:8080/prefix/url";
        assertEquals(uri + "?page=1", replaceQueryParam(uri, "page", "1"));

        String uri2 = uri + "?page2=3";
        assertEquals(uri2 + "&page=1", replaceQueryParam(uri2, "page", "1"));

        String uri3 = uri + "?page2=3&page=4";
        String expected3 = uri + "?page2=5&page=4";
        assertEquals(expected3, replaceQueryParam(uri3, "page2", "5"));
    }
}
