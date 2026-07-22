# Strategia ad agenti

## Obiettivo

Ridurre il rischio dividendo inventario, implementazione, verifica e migrazione con ownership separata.

| Agente | Responsabilità | Gate |
|---|---|---|
| Guardian | controlla AGENTS.md, ADR, schema, nomi e invarianti | nessuna astrazione vietata |
| Query inventory | cataloga ogni query/write LoLDB e la controparte Mongo | nessuna query usata senza mapping |
| Mongo core | implementa MongoDB, MongoRecord, schema e indici | tre file, test conversione |
| Write-path | porta le scritture runtime LoL direttamente su MongoDB | nessuna query MariaDB fuori migration |
| Read migration | porta i consumer LoL a Mongo-only | nessun fallback MariaDB |
| Data migration | implementa MongoMigration e checkpoint | dry-run, resume, high-water mark, bulk write |
| Contract/test | verifica bans, enum, participant flat e API | test mirati e audit finale |

## Sequenza

1. Guardian congela invarianti e blocca conflitti.
2. Query inventory aggiorna 08-query-inventory.md.
3. Mongo core implementa i tre file principali.
4. Write-path rimuove LeagueDB dal runtime e conserva solo il boundary migration.
5. Read migration cambia i consumer.
6. Data migration implementa il backfill.
7. Contract/test esegue compilazione, test e audit.
8. Guardian approva o riapre il gate con file e linee precise.

Ogni handoff include file modificati, query/write coperti, rischio residuo, comando e risultato della verifica. Un agente si ferma davanti a un conflitto con ADR o API pubbliche. Non crea DTO Mongo per risolvere un problema di mapping.
