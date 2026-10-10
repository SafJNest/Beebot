# Arena Phase 3 synthetic sizing probe

> Misura storica del precedente modello ad alta cardinalità. Non usare questi numeri per stimare il payload semplificato; il nuovo schema richiede un sizing dedicato prima del rollout.

JVM OpenJDK 26.0.2; `-Xmx512m`. Four isolated JVM runs. No Mongo, Redis, Riot or scheduler calls. Not a representative rollout gate.

Dataset: champion 27, full patch 26.19.123, CHERRY; one participant per match (id=1, puuid=synthetic-participant), unique EUW1_1..N IDs. Source loaded/released in batches of 100. No timeline and completeItemHistory=false, so all reconstruction is labelled fallback. Boots=3006, P1=447001, two Legendary items and three augments. Fixed scenarios share equipment/context IDs; varied scenarios change one Legendary ID per match. No pruning or thresholds.

Catalog is detached/immutable and present in the baseline. Baseline follows class/Jackson warmup. Heap uses Runtime totalMemory-freeMemory after three explicit GC requests separated by 100ms; no exact GC completion guarantee. Retained figures include accumulator (and where named snapshot/BSON). Sampled used heap is an absolute sampled allocation high-water, not precise peak or RSS. BSON uses DocumentCodec+BsonBinaryWriter+BasicOutputBuffer, including the synthetic combined standard Build+Arena envelope.

| Scenario | Matches | Steps | Builds | Arena BSON bytes | Combined fixture BSON bytes | Retained accumulator MiB | Accumulator + snapshot MiB | With BSON MiB | Sampled used heap MiB |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| fixed-contexts | 1000 | 24 | 1 | 42,672 | 43,408 | 0.26 | 0.30 | 1.01 | 24.81 |
| fixed-contexts | 10000 | 24 | 1 | 42,672 | 43,408 | 1.29 | 1.33 | 2.08 | 170.02 |
| unique-Legendary-per-match | 500 | 7010 | 500 | 10,406,912 | 10,407,648 | 19.10 | 25.29 | 116.52 | 179.33 |
| unique-Legendary-per-match | 1000 | 14010 | 1000 | 20,798,917 | 20,799,653 | 37.82 | 50.17 | 232.24 | 300.56 |

The 1,000 varied-context fixture encodes to 20,799,653 bytes (19.84 MiB), exceeding the Mongo 16 MiB document limit; a production guard must reject it without losing valid previous data. The 500 fixture is 9.93 MiB. Stable contexts keep BSON fixed while deduplication memory grows with match count. These synthetic results demonstrate cardinality growth and do not justify splitting, truncation, pruning, rollout, or expected production memory assumptions.

Combined envelope contains a synthetic standard Build (100 games/50 wins, empty option lists), identity and owner metadata; it is a sizing fixture, not a live persisted document. Actual catalog/source populations, multi-participant distribution, valid timeline payloads, server round-trip, explain and production heap remain unmeasured.

Artifacts: /tmp/ArenaPhase3Probe.java; /tmp/arena-phase3-probe-results.json; /tmp/arena-phase3-probe-*.log.
