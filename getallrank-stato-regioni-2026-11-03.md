# Stato getAllRank per regione

Aggiornato usando l'ultimo log allegato del 3 novembre 2026. Per ogni regione è indicata l'ultima pagina per cui compare `Start analyzing`.

| Regione | Tier | Ultima pagina avviata | Entry riportate |
|---|---|---:|---:|
| BR1 | BRONZE_II | 1 | 205 |
| EUN1 | BRONZE_III | 17 | 205 |
| EUW1 | IRON_I | 315 | 205 |
| JP1 | — | — | Completata (DIAMOND_I pagina 12 vuota) |
| KR | BRONZE_IV | 266 | 205 |
| LA1 | BRONZE_I | 88 | 205 |
| LA2 | SILVER_IV | 205 | 205 |
| ME1 | — | — | Completata |
| NA1 | BRONZE_IV | 353 | 205 |
| OC1 | — | — | Completata |
| RU | — | — | Completata |
| SG2 | — | — | Completata (DIAMOND_I pagina 11 vuota) |
| TR1 | SILVER_III | 168 | 205 |
| TW2 | EMERALD_I | 9 | 205 |
| VN2 | SILVER_IV | 149 | 205 |

## Note

- BR1 ha terminato BRONZE_III: il log riporta pagina 231 con 0 entry, poi l'avvio di BRONZE_II pagina 1.
- `Start analyzing` registra l'avvio della pagina, non la conferma che tutte le sue entry siano state elaborate.
- `LeagueShardUtils.getActives()` elenca 15 regioni attive. Le regioni non presenti nel log (ME1, OC1 e RU) sono indicate come completate, secondo la precisazione dell'utente.
- Una pagina con 0 entry conclude il tier: si procede al tier successivo. BR1 ha quindi concluso BRONZE_III a pagina 231 e ha avviato BRONZE_II a pagina 1.
- JP1 e SG2 hanno 0 entry rispettivamente all'ultima pagina di DIAMOND_I (12 e 11), quindi sono completate. TW2 è a EMERALD_I pagina 9 con 205 entry.
