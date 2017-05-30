package org.hawkular.alerts.handlers.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Jay Shaughnessy
 * @author Lucas Ponce
 */
public class ResponseUtilTest {

    @Test
    public void replaceQueryParams() {
        String uri = "http://server:8080/prefix/url";
        Assert.assertEquals(uri + "?page=1", ResponseUtil.replaceQueryParam(uri, "page", "1"));

        String uri2 = uri + "?page2=3";
        Assert.assertEquals(uri2 + "&page=1", ResponseUtil.replaceQueryParam(uri2, "page", "1"));

        String uri3 = uri + "?page2=3&page=4";
        String expected3 = uri + "?page2=5&page=4";
        Assert.assertEquals(expected3, ResponseUtil.replaceQueryParam(uri3, "page2", "5"));
    }
}
