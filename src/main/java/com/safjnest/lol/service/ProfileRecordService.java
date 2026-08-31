package com.safjnest.lol.service;

import java.util.List;

import com.safjnest.lol.model.ApiResult;
import com.safjnest.lol.model.Filter;
import com.safjnest.lol.model.ResponseMetadata;
import com.safjnest.lol.model.record.ProfileRecord;
import com.safjnest.lol.model.record.ProfileRecordPage;
import com.safjnest.lol.model.record.RecordMetric;
import com.safjnest.lol.model.record.RecordPage;
import com.safjnest.lol.queue.scheduler.ComputeScheduler;
import com.safjnest.nosql.MongoDB;

import no.stelar7.api.r4j.basic.constants.api.regions.LeagueShard;

public final class ProfileRecordService {

    public ApiResult<ProfileRecordPage> get(String puuid, LeagueShard shard, Filter filter) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null) return ApiResult.notFound();
        List<ProfileRecord> records = MongoDB.findProfileRecords(puuid, filter);
        if (!records.isEmpty()) {
            long lastUpdate = lastUpdate(records);
            ProfileRecordPage page = new ProfileRecordPage(records, lastUpdate, ResponseMetadata.ready(lastUpdate, filter));
            return ApiResult.ready(page, page.metadata());
        }
        ComputeScheduler.startProfileRecords(puuid, shard, filter, false);
        return ApiResult.pending(ResponseMetadata.pending(null, filter));
    }

    public RecordPage getGlobal(Filter filter, RecordMetric metric, LeagueShard region, int limit, int offset) {
        List<ProfileRecord> records = MongoDB.findGlobalProfileRecords(filter, metric, region, limit, offset);
        long total = MongoDB.countGlobalProfileRecords(filter, metric, region);
        long lastUpdate = lastUpdate(records);
        ResponseMetadata.Pagination pagination = new ResponseMetadata.Pagination(
            null, null, limit, offset, total, null, offset + records.size() < total);
        return new RecordPage(metric, records, limit, offset, total, offset + records.size() < total,
            new ResponseMetadata(pagination, lastUpdate == 0 ? null : lastUpdate, false, filter));
    }

    public boolean generate(String puuid, LeagueShard shard, Filter filter) {
        if (puuid == null || puuid.isBlank() || shard == null || filter == null) return false;
        ProfileRecordAnalyzer.Accumulator accumulator = ProfileRecordAnalyzer.accumulator(puuid, filter);
        MongoDB.forEachProfileRecordMatch(puuid, shard, filter, accumulator::accept);
        return MongoDB.upsertProfileRecords(puuid, filter, accumulator.finish());
    }

    // ============================================================================

    private static long lastUpdate(List<ProfileRecord> records) {
        long result = 0;
        if (records == null) return result;
        for (ProfileRecord record : records) if (record != null) result = Math.max(result, record.lastUpdate);
        return result;
    }
}
