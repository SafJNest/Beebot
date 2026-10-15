# SQL database

SQL files under [`database/`](../../database/) describe the legacy schema.
MariaDB is used only as source data by the LoL `MongoMigration` backfill; LoL
runtime reads and writes use MongoDB. See [MongoDB documentation](../mongo/README.md)
for migration ownership and current storage rules.
