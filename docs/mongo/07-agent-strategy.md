# Agent strategy

## Objective

Reduce risk by splitting inventory, implementation, verification, and migration with separate ownership.

| Agent | Responsibility | Gate |
|---|---|---|
| Guardian | checks AGENTS.md, ADRs, schema, names, and invariants | no forbidden abstraction |
| Query inventory | catalogs every LoLDB query/write and its Mongo counterpart | no query in use without a mapping |
| Mongo core | implements MongoDB, QueryRecordParser, schema, and indexes | conversion test |
| Write-path | moves LoL runtime writes directly to MongoDB | no MariaDB query outside migration |
| Read migration | moves LoL consumers to Mongo-only | no MariaDB fallback |
| Data migration | implements MongoMigration and checkpoints | dry-run, resume, high-water mark, bulk write |
| Contract/test | verifies bans, enums, flat participants, and API | targeted tests and final audit |

## Sequence

1. Guardian freezes invariants and blocks conflicts.
2. Query inventory updates 08-query-inventory.md.
3. Mongo core implements the three main files.
4. Write-path removes LeagueDB from the runtime and keeps only the migration boundary.
5. Read migration switches consumers.
6. Data migration implements the backfill.
7. Contract/test runs compilation, tests, and audit.
8. Guardian approves or reopens the gate with exact file and line references.

Each handoff includes modified files, covered queries/writes, residual risk, command, and verification result. An agent stops when facing a conflict with an ADR or public API. Do not create Mongo DTOs to work around a mapping problem.
