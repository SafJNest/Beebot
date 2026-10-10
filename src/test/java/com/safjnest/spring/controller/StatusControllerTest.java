package com.safjnest.spring.controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

public class StatusControllerTest {

    @Test
    public void sharedHiveStatusesPreserveTheirJsonContract() throws Exception {
        var progress = new com.hive.status.JobProgress(2, 5);
        var job = new com.hive.status.JobStatus(1, 0, "SyncScheduler", "tracking", "tracking summoners", null,
            com.hive.job.JobPriority.IMMEDIATE, com.hive.job.JobState.WAITING_CHILDREN,
            null, 10, 11L, null, "TRACKING", "item", progress, java.util.Map.of(), java.util.Map.of(), java.util.List.of(2L));
        var worker = new com.hive.status.WorkerStatus(1, com.hive.worker.WorkerState.RUNNING,
            job, 0, 1, java.util.List.of());
        var scheduler = new com.hive.status.SchedulerStatus("riot", java.util.List.of(
            new com.hive.status.QueueStatus("EUW1", worker)), java.util.List.of(
            new com.hive.status.RunStatus("1", "TRACKING", job.state(), 10L, 11L, progress, java.util.List.of(job))));
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var json = mapper.valueToTree(scheduler);
        assertEquals("riot", json.get("id").asText());
        assertEquals("EUW1", json.get("queues").get(0).get("route").asText());
        assertEquals("RUNNING", json.get("queues").get(0).get("worker").get("state").asText());
        assertEquals("TRACKING", json.get("runs").get(0).get("type").asText());
        assertEquals(mapper.readTree("{\"pid\":1,\"ppid\":0,\"type\":\"SyncScheduler\",\"key\":\"tracking\",\"name\":\"tracking summoners\",\"route\":null,\"priority\":\"IMMEDIATE\",\"state\":\"WAITING_CHILDREN\",\"followingPid\":null,\"queuedAt\":10,\"startedAt\":11,\"completedAt\":null,\"phase\":\"TRACKING\",\"currentItem\":\"item\",\"progress\":{\"current\":2,\"total\":5},\"items\":{},\"itemLabels\":{},\"children\":[2]}"), mapper.readTree(mapper.writeValueAsString(job)));
    }

    @Test
    public void exposesGetStatusUnderApiPrefix() throws Exception {
        GetMapping mapping = StatusController.class.getMethod("status").getAnnotation(GetMapping.class);
        RequestMapping base = StatusController.class.getAnnotation(RequestMapping.class);

        assertEquals("/status", mapping.value()[0]);
        assertEquals("/api", base.value()[0]);
    }
}
