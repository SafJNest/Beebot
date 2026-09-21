package com.safjnest.spring.controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.web.bind.annotation.GetMapping;

public class ProfileIndexableControllerTest {

    @Test
    public void shouldExposeGlobalIndexablesEndpointWithoutParameters() throws Exception {
        java.lang.reflect.Method method = ProfileIndexableController.class.getDeclaredMethod("indexables");
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        assertEquals("/profile/indexables", mapping.value()[0]);
        assertEquals(0, method.getParameterCount());
    }
}
