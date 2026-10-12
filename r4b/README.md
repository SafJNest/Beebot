# R4B — Riot 4 Beebot

The standalone artifact compiles the canonical `../src/main/java/com/R4B`
sources. It contains shared data contracts under `com.R4B.AB.DTO` and pure LoL
values/helpers under `com.R4B.LOL`. It does not contain an HTTP client or
request builder. SDK acquisition and conversion remain in
`com.safjnest.r4j.RiotGateway` in the application.

Runtime dependencies are JSON libraries and the independently buildable Hive
artifact for existing generic status contracts. The R4B artifact does not
embed a second copy of Hive or application classes.

Build from the repository root with a JDK supporting the configured release:

```sh
mvn -f hive/pom.xml install
mvn -f r4b/pom.xml package
```

The monolith still compiles the same canonical sources. Its Java 25 build,
compatibility tests and the SDK-free consumer-closure check are documented in
[`r4b-validation.md`](../docs/architecture/services/r4b-validation.md).

`TransportJson.mapper()` reproduces the public HTTP profile. It is distinct
from the application's storage/BSON codec; internal filters and summoner
tracking data do not become public fields.

Architecture and inventory: [ADR-0018](../docs/architecture/adr/0018-r4b-shared-contracts.md)
and [R4B inventory](../docs/architecture/services/r4b-inventory.md).
Upstream source attribution: [third-party/NOTICE.txt](third-party/NOTICE.txt).
