package com.safjnest.spring.dto;

public record LolApiError(
    int status,
    String code,
    String message
) {}
