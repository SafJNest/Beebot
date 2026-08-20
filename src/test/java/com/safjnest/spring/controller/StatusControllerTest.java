package com.safjnest.spring.controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

public class StatusControllerTest {

    @Test
    public void exposesGetStatusUnderApiPrefix() throws Exception {
        GetMapping mapping = StatusController.class.getMethod("status").getAnnotation(GetMapping.class);
        RequestMapping base = StatusController.class.getAnnotation(RequestMapping.class);

        assertEquals("/status", mapping.value()[0]);
        assertEquals("/api", base.value()[0]);
    }
}
