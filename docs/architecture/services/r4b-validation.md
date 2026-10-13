# Verifica R4B

Decisione: [ADR-0018](../adr/0018-r4b-shared-contracts.md).
Baseline: branch `beebot-servicies`, HEAD `8a88645e`, working tree pulito.
L’utente ha salvato la prima iterazione e la rinomina in `615dfaa7`.
L’agente non esegue ulteriori commit, push o PR. La verifica finale segue
i namespace scelti dall’utente `com.R4B.api.dto` e `com.R4B.lol`.

## Baseline verificata

- SDK dichiarato e JAR locale: `com.github.stelar7:R4J:2.8.1`.
- Sorgenti ufficiali del tag `2.8.1` confrontati con l'artifact realmente usato.
- CodeGraph iniziale aggiornato: 525 file, 12.945 nodi, 34.117 archi.
- Build baseline offline in checkout temporaneo indipendente dal working tree.
- Probe del mapper HTTP baseline: 98 fixture JSON di radici/componenti; 90
  fixture pubbliche nel gate persistente. Sette tipi interni
  (`Augment`, `CompetitiveEntry`, `ChampionNode`, `ChampionLeafStats`,
  `MatchupStats`, `TrendStats`, `WinLossStats`) sono esclusi esplicitamente
  dal gate dei contratti pubblici, senza considerarli DTO da estrarre.
  Anche `ProfileMatchups.Metric`, enum privato del produttore, è escluso.

- Probe baseline delle etichette bot: 173 stringhe (126 queue, 8 lane e
  39 rank compatti), senza rete o bootstrap operativo.

## Gate riproducibili

`R4BIntegrationContractTest` confronta le fixture pubbliche con `TransportJson`,
verifica che Spring utilizzi quel profilo e caratterizza la differenza con
`JsonCodec` per storage. Le fixture sono JSON leggibile in
`src/test/resources/r4b/`, con revisione ed esclusioni in un file metadata.
Tre payload baseline popolati coprono profilo/ranks, statistics con chiavi
champion/queue/lane e cast D/F, rune/statShards e roundtrip pubblico. Il test non carica database né chiama Riot.

`R4BSemanticEnumsTest` confronta famiglie complete, ordine, codici, ID, realm,
routing e lookup con l'SDK dichiarato. Include queue con ID condivisi,
sconosciuti e alias, lane/role distinti. L'SDK è usato soltanto come oracolo
nel test d'integrazione lato `r4j`.

`R4BSemanticHelpersTest` caratterizza rank/MMR, divisioni storiche, media
preesistente, rank boundaries, canonicalizzazione queue, lane/duo, routing
Account/Match/cache e lettura di una state-key baseline con ordinali storici.

`R4BSemanticPresentationTest` confronta le 173 etichette bot baseline,
controllando testo queue/lane/rank senza modificare la presentazione.

`scripts/r4b-verify-independence.py` esegue una compilazione limitata ai contratti
e Hive con sole dipendenze JSON, analizza i classfile, compila il monolite senza
jar SDK e richiede che i diagnostici restino nel lato `r4j`. Analizza inoltre
i riferimenti SDK dei classfile dei consumatori. Una ricerca testuale degli
import non è considerata sufficiente.

## Prove già eseguite

- Compilazione isolata delle 24 famiglie enum estratte e dei bounds mappa:
  solo JDK/JUnit, senza SDK o servizi.
- `R4BSemanticEnumsTest`: 6/6 superati contro il JAR SDK 2.8.1;
  completezza/ordinali, codici/ID/routing, lookup/alias e relazioni verificati.

## Esito implementativo

La migrazione è in corso; build finali, gate e revisioni del risultato saranno
registrati qui dopo l'esecuzione. Le descrizioni dei test sopra identificano
la copertura prevista e non costituiscono una dichiarazione di superamento.

## Limiti delle prove

Nessuna prova runtime positiva Mongo, Redis, Riot o Discord è dimostrata
dalla baseline offline o dai test di contratto. Un tentativo iniziale della
suite completa ha eseguito il sampler Mongo dipendente dalla rete ed è stato
interrotto; la verifica offline del limite serie usa ora un sampler isolato e
la stessa operazione di append/troncamento, senza chiamare Mongo. Non sono introdotti client HTTP, processi
distinti o una nuova queue. Il mantenimento del JSON non prova disponibilità
dei servizi reali né l'uso degli indici su un database attivo.
