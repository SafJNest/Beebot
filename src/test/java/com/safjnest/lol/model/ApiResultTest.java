package com.safjnest.lol.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ApiResultTest {

    @Test
    public void exposesTheFourApiStates() {
        assertEquals(ApiResult.Status.READY, ApiResult.ready("ready").status());
        assertEquals(ApiResult.Status.PARTIAL, ApiResult.partial("partial").status());
        assertEquals(ApiResult.Status.PENDING, ApiResult.pending().status());
        assertEquals(ApiResult.Status.NOT_FOUND, ApiResult.notFound().status());
        assertNull(ApiResult.pending().payload());
        assertNull(ApiResult.notFound().payload());
    }
}
