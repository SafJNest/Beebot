var MATCH_COUNT_INDEXES_V4 = [
  {
    name: "match_player_champion_lane_time_v4",
    key: {
      "participants.puuid": 1,
      region: 1,
      "participants.champion": 1,
      "participants.lane": 1,
      timeStart: -1,
      queue: 1
    }
  },
  {
    name: "match_player_lane_time_v4",
    key: {
      "participants.puuid": 1,
      region: 1,
      "participants.lane": 1,
      timeStart: -1,
      queue: 1
    }
  }
];

var matchCountBenchmarkReportV4 = {status: "idle"};

async function matchIndexCommandV4(value) {
  const result = await db.runCommand(value);
  if (Number(result.ok) !== 1) throw new Error(result.errmsg || "Mongo command failed");
  return result;
}

async function createMatchCountIndexesV4() {
  const report = {database: await db.getName(), applied: [], status: "running"};
  try {
    for (const index of MATCH_COUNT_INDEXES_V4) {
      await matchIndexCommandV4({createIndexes: "match", indexes: [index]});
      report.applied.push(index.name);
    }
    report.status = "completed";
  } catch (error) {
    report.status = "error";
    report.error = error.message;
  }
  print(EJSON.stringify(report, null, 2));
}

function matchCountMetricsV4(explain) {
  let root = explain;
  if (Array.isArray(explain.stages)) {
    for (const stage of explain.stages) {
      if (stage.$cursor) {
        root = stage.$cursor;
        break;
      }
    }
  }
  const stats = root.executionStats || {};
  const indexes = [];
  const bounds = [];
  let collscan = false;
  function visit(node) {
    if (!node || typeof node !== "object") return;
    if (node.indexName && indexes.indexOf(node.indexName) < 0) indexes.push(node.indexName);
    if (node.indexName && node.indexBounds) bounds.push({index: node.indexName, bounds: node.indexBounds});
    if (node.stage === "COLLSCAN") collscan = true;
    for (const key in node) {
      if (key !== "rejectedPlans" && key !== "allPlansExecution") visit(node[key]);
    }
  }
  if (root.queryPlanner) visit(root.queryPlanner.winningPlan);
  visit(stats.executionStages);
  return {
    indexes,
    bounds,
    serverMs: stats.executionTimeMillis == null ? null : Number(stats.executionTimeMillis),
    keys: stats.totalKeysExamined == null ? null : Number(stats.totalKeysExamined),
    docs: stats.totalDocsExamined == null ? null : Number(stats.totalDocsExamined),
    collscan
  };
}

async function benchmarkMatchCountsV4({
  puuid = "gk1Fy2k5oPDryMhWh14BUqnWdq3xdOrE-8cOLuRsEYmtGld7yqSqNguECMcgvLkCXh3zUFXaePos7A",
  region = "EUW1", champion = 887, lane = "TOP", queue = "TEAM_BUILDER_RANKED_SOLO",
  timeStart = 0, timeEnd = 0, patch = null, runs = 3, maxTimeMS = 10000
} = {}) {
  const report = {
    status: "running",
    phase: "indexes",
    context: {puuid, region, champion, lane, queue, timeStart, timeEnd, patch, runs, maxTimeMS},
    results: []
  };
  matchCountBenchmarkReportV4 = report;
  try {
    if (!puuid || !region || !Number.isInteger(runs) || runs < 1 || maxTimeMS < 1)
      throw new Error("PUUID, region and positive runs/maxTimeMS are required");
    timeStart = Number(timeStart);
    timeEnd = Number(timeEnd);
    if (!Number.isFinite(timeStart) || !Number.isFinite(timeEnd) || timeStart < 0 || timeEnd < 0
        || timeStart && timeEnd && timeStart > timeEnd) throw new Error("Invalid time bounds");
    report.context.timeStart = timeStart;
    report.context.timeEnd = timeEnd;
    report.context.database = await db.getName();
    const definitions = await matchIndexCommandV4({listIndexes: "match", cursor: {batchSize: 100}});
    report.indexes = definitions.cursor.firstBatch;
    const available = new Set();
    for (const index of report.indexes) if (!index.hidden) available.add(index.name);
    report.missingIndexes = [];
    for (const index of MATCH_COUNT_INDEXES_V4) {
      if (!available.has(index.name)) report.missingIndexes.push(index.name);
    }

    for (let mask = 0; mask < 8; mask++) {
      for (const selectedPatch of patch == null ? [null] : [null, patch]) {
        const participant = {puuid};
        const names = [];
        if (mask & 1) { participant.champion = champion; names.push("champion"); }
        if (mask & 2) { participant.lane = lane; names.push("lane"); }
        const filter = Object.keys(participant).length === 1
          ? {"participants.puuid": puuid, region}
          : {participants: {$elemMatch: participant}, region};
        if (mask & 4) { filter.queue = queue; names.push("queue"); }
        if (selectedPatch != null) filter.patchMajor = selectedPatch;
        const startBounds = {};
        if (timeStart) startBounds.$gte = timeStart;
        if (timeEnd) startBounds.$lte = timeEnd;
        if (Object.keys(startBounds).length) filter.timeStart = startBounds;
        if (timeEnd) filter.timeEnd = {$lte: timeEnd};
        const label = (names.join("+") || "base") + (selectedPatch == null ? "" : "/patch=" + selectedPatch);
        const hints = [null];
        if (available.has("match_player_search_v3")) hints.push("match_player_search_v3");
        const selector = mask & 1 ? MATCH_COUNT_INDEXES_V4[0].name
          : mask & 2 ? MATCH_COUNT_INDEXES_V4[1].name : null;
        if (selector && available.has(selector)) hints.push(selector);
        let baseline = null;
        for (const hint of hints) {
          const row = {label, hint: hint || "planner", clientMs: []};
          report.phase = label + "/" + row.hint;
          const query = {
            aggregate: "match",
            pipeline: [{$match: filter}, {$count: "total"}],
            cursor: {batchSize: 2},
            maxTimeMS
          };
          if (hint) query.hint = hint;
          try {
            for (let run = 0; run < runs; run++) {
              const started = Date.now();
              const result = await matchIndexCommandV4(query);
              row.clientMs.push(Date.now() - started);
              const batch = result.cursor.firstBatch;
              row.total = batch.length ? Number(batch[0].total) : 0;
            }
            if (baseline == null) baseline = row.total;
            row.sameTotal = row.total === baseline;
          } catch (error) {
            row.error = error.message;
          }
          try {
            const explain = await matchIndexCommandV4({
              explain: query, verbosity: row.error ? "queryPlanner" : "executionStats"
            });
            row.plan = matchCountMetricsV4(explain);
          } catch (error) {
            row.explainError = error.message;
          }
          report.results.push(row);
        }
      }
    }
    report.phase = "storage";
    const stats = await matchIndexCommandV4({collStats: "match"});
    const indexSizesMiB = {};
    for (const name in stats.indexSizes) indexSizesMiB[name] = Number(stats.indexSizes[name]) / 1048576;
    report.storage = {
      matches: Number(stats.count),
      storageMiB: Number(stats.storageSize) / 1048576,
      totalIndexMiB: Number(stats.totalIndexSize) / 1048576,
      indexSizesMiB
    };
    report.status = "completed";
    report.phase = null;
  } catch (error) {
    report.status = "error";
    report.error = error.message;
  }
  print(EJSON.stringify(report, null, 2));
}
