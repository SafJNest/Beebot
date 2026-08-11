package com.safjnest.lol.model;

public record ResponseMetadata(
    Pagination pagination,
    Long lastUpdate,
    Boolean refresh,
    Filter filter
) {

    public static ResponseMetadata ready(Long lastUpdate, Filter filter) {
        return new ResponseMetadata(null, lastUpdate, false, filter);
    }

    public static ResponseMetadata pending(Long lastUpdate, Filter filter) {
        return new ResponseMetadata(null, lastUpdate, true, filter);
    }

    public record Pagination(
        Integer page,
        Integer pageSize,
        Integer limit,
        Integer offset,
        Long total,
        Long pages,
        Boolean hasMore
    ) {}
}
