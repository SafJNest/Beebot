# Fase 3 — rapporto storico del primo gate

Data: 2026-10-02. Rapporto storico del primo gate. La successiva decisione utente
approva un documento combinato con `$set` su path disgiunti, senza CAS né
read-merge-replace; vedere [contratto corrente](contracts.md#single-collection-and-write-ownership--approved-phase-3).
Le alternative e i blocchi descritti sotto fotografano lo stato prima di tale decisione.

Stato al primo gate: analisi completata con tre agenti in parallelo;
implementazione non avviata per il conflitto di identity/schema. Questo rapporto
registra evidenze e proposte, senza approvare un nuovo contratto o modificare ADR.

## Stato iniziale e Fase 2

- Branch `arena-build`, checkout inizialmente pulito; HEAD `5d364aa9` (`phase 2 refactor`).
- Commit e modifiche precedenti preservati. Nessun commit, rebuild, backfill o
  operazione di produzione eseguiti.
- Letti AGENTS, architecture README, ADR-0001/0005/0006/0009/0012/0014,
  handbook §5–§7 e Arena README/contracts/phases/schema.
- Applicata la skill canonica `beebot-handbook`.
- CodeGraph aggiornato: 532 file, 13.210 nodi, 35.069 archi; sync non necessario.
  Explore/impact svolti sugli owner provider, Mongo/build/filter e service/queue.
- I 105 test della Fase 2 sono risultati documentati in `phases.md`, non un nuovo
  run di questo lavoro. Nessuna ragione per ripetere parser/accumulatore prima
  di risolvere il contratto. Non provano provider Arena operativo, Mongo reale,
  explain, cardinalità BSON, heap o rollout.

## Blocco: collisione standard CHERRY / Arena

Prova dal codice corrente:

| Owner | Evidenza |
|---|---|
| `Filter.java:36,246` | Factory Arena neutra e `toKey()` non includono il tipo di aggregato; patch conservata da `setPatch` a riga 168. |
| `MongoDB.java:4096` | `_id = filterKey = build.filter().toKey()`. |
| `MongoDB.java:2950,2957,4218` | Writer singolo e bulk sostituiscono il documento completo per `_id`. |
| `Build.java:52` | Factory Arena valorizza i contatori root con quelli Arena e lascia vuote le liste standard. |
| `MongoDB.java:1557,4065` | Lettore standard filtra solo filterKey/buildVersion e deserializza senza discriminator Arena. |

Uno standard CHERRY con lo stesso champion, patch e dimensioni neutre ha la
stessa identity di Arena. Non si assume che quel documento esista già in Mongo:
è dimostrato che il contratto corrente consente la collisione. Un write Arena
lo sostituirebbe; il reader standard potrebbe accettarlo; il refresh standard
successivo eliminerebbe Arena. Aggiungere solo schemaVersion non risolve questo.

Il contratto Arena richiede un documento completo e l'identity normale ma non
specifica la conservazione reciproca. L'istruzione corrente richiede di fermarsi
quando serve una decisione di schema/ADR: il principale non approva il wiring
attuale. Nessuna implementazione A/B/C autorizzata agli agenti.

### Alternative concrete da approvare

**A — un documento combinato, proposta preferita perché conserva l'identity.**
Mantenere `_id=filterKey`, root e liste standard; aggiungere Arena in `build.arena`
con statistiche, versioni e aggiornamento propri. Le popolazioni standard/Arena
rimangono separate. Ogni write dell'unico owner Mongo deve conservare l'altra
popolazione, con replace completo e revisione/CAS con retry o ricomputazione
congiunta. Un semplice read-merge-replace è insufficiente contro lost update.
Il percorso pubblico standard deve proiettare il Build precedente senza esporre
automaticamente `arena`. Vanno definiti documento senza standard, ready-empty,
versioni/staleness separate e comportamento delle enumerazioni di refresh.
Questo cambia il contratto persistito e il comportamento interno dei writer:
richiede approvazione e sincronizzazione documentale prima del codice.

**B — due documenti discriminati nella stessa collection.**
Definire esattamente `_id`, filterKey, variante e indici. Se filterKey resta uguale,
ogni lookup, timestamp ed enumerazione deve discriminare il tipo e gli indici live
devono consentire la coesistenza. Se cambia filterKey, cambiano le convenzioni
dei consumer. Questa alternativa modifica il vincolo documentato di identity
normale/unico documento. Non è una nuova collection e non implica un secondo
writer, ma non può essere adottata implicitamente.

## Blocco: schema JSON semantico

La Fase 2 serializza `Build.Choice(kind,id)` e `Build.Slot(kind)` (`Build.java:67,75`),
`EquipmentKey(kind,id)` e `AugmentKey(id)` (`ArenaBuildData.java:61,63`). Lo schema
e la fixture JSON documentano questa forma; JsonCodec non la adatta per Arena.
Il requisito corrente vieta kind/id nel payload finale. Prima di persistere
occorre congelare la forma semantica con core/items/augments/prismatics e le
altre chiavi previste, versionarla e provarne il round-trip. Non basta aggiornare
l'esempio, né modificare globalmente Choice/Slot cambiando lo standard.
Le versioni attuali schema=2 e aggregazione=3 non certificano la nuova forma.

## A — provider e input detached, proposta subordinata

- Riutilizzare `ChampionBuildProvider.java` con entrypoint Arena esplicito e
  `MongoDB.java` con bounded left join, massimo 100 match per batch.
- I reader standard scartano match senza timeline: `flushChampionBuildMatches`
  (`MongoDB.java:648`) e reader matrix (`:1634–1676`). Mantenerli invariati.
- Iterare i match come fonte primaria e collegare gli eventuali match_events.
  La mancanza di eventi incrementa coverage/fallback senza eliminare la partita.
- Proiettare `_id`, region, queue, patch e tutti i participant con id, puuid,
  champion, win, subTeamPlacement, boots, item0..item5, augments. Conservare tutti
  i participant per verificare identità duplicate prima di mutare l'accumulatore.
- Hydration detached prima del rilascio distruttivo dei documenti/batch tramite
  MatchMemoryUtils; riutilizzare QueryRecordParser.fromDocument/fromMap e
  detachedValue (`QueryRecordParser.java:52,66,97`). Catalog già immutabile dopo
  costruzione; caricarlo fuori
  dall'accumulatore. Nessun accesso esterno nell'accumulatore puro.
- Query standard usa patchMajor, accumulatore Arena compara patch esatta:
  non riscrivere il match per simulare uno scope diverso. La filter key conserva
  la stringa patch; la query e l'interfaccia catalog devono rispettare full patch.
- StaticDataService.getItems usa patch corrente/cache unica: catalog storico
  relativo allo scope non dimostrato. Estendere l'owner solo dopo aver definito
  l'interfaccia patch, oppure fornire catalog detached esplicito.
- `completeItemHistory=false` finché non c'è prova sorgente. Timeline presente
  non significa storia completa. Decode/checksum errato resta errore esplicito.

Il join bounded non prova heap totale bounded: set di dedup e cardinalità degli
aggregati crescono col dataset. Nessuna potatura di step per superare i gate.

## B — persistenza, proposta subordinata

Unico owner `MongoDB.java`, collection `champion_builds`. Il replace attuale
verifica acknowledgment. Costruire/serializzare e codificare il documento intero
prima di inviare il write; misurare BSON effettivo, envelope incluso, e verificare
limite/headroom. Errori size/serializzazione non devono fare delete, write parziali
o truncation. Per A, guard e CAS riguardano il documento combinato. Bulk unordered
è atomico per documento, non rollback dell'intera lista.

L'indice `champion_builds_filter` è documentato in Mongo inventory, ma chiavi,
opzioni e uniqueness live non sono state lette. Bootstrap corrente crea solo
collection; sezioni handbook/ADR/Mongo che descrivono bootstrap degli indici
sono incoerenti con l'amendment operativo ADR-0009 e il codice. Non risolvere
questa incoerenza modificando implicitamente un ADR.

## C — orchestrazione, proposta subordinata

Estendere gli owner esistenti `ChampionService.java` e `ComputeScheduler.java`
con entrypoint Arena interno e opt-in, via QueueHandler su CHAMPION. Nessun nuovo
service, queue, endpoint o collegamento automatico al refresh standard/API.
Gli owner standard restano `refreshBuild`, matrix coalesced e scheduled refresh.

Job supporta phase/currentItem e progress completed/total; il normale status
omette le mappe item (`Job.java:65–79`, `Registry.java:332–348`). Registrare ogni
match manterrebbe memoria O(match). Proposta: un solo item aggregate, champion/
full patch/shard=ALL nel currentItem e contatori source nella phase, aggiornati
per batch. `total` indica candidati scoperti, `completed` candidati esaminati con
successo incluso fallback, `missing` è il sottoinsieme senza timeline utilizzabile,
`failed` gli errori. Coverage delle rejection resta distinta. Finché il cursor
è aperto total è scoperto, non un totale preventivo. Errori writer fanno fallire
il job e propagano l'errore. Il JobProgress strutturato dell'unico item aggregate
resterebbe 0/1 → 1/1; i contatori nella phase descrivono invece le partite sorgente.
Contratto progress ancora da congelare.

## Confini agenti e revisione futura

| Sequenza | Agente / file assegnabili dopo approvazione | Revisore diverso |
|---|---|---|
| A | provider: ChampionBuildProvider, sezioni reader/projection MongoDB, test relativi; StaticDataService se necessario | mongo |
| B | mongo: sezioni document/read/write MongoDB, schema/serializzazione Arena confinata, test relativi | provider |
| C | orchestration: ChampionService, ComputeScheduler, test relativi | mongo |

MongoDB è condiviso: A consegna il diff e supera review prima che B lo modifichi.
Schema/interfacce congelati dal principale prima delle modifiche. Questo lavoro
ha utilizzato tutti i tre agenti disponibili soltanto in analisi.

## Gate e disponibilità reali

- Nessun nuovo test eseguito: fermata al gate di schema, senza ripetere Fase 2.
- Futuri test: con/senza/empty/corrupt timeline; detached hydration/release;
  eligibility standard; full patch/catalog; coesistenza nei due ordini di refresh;
  concorrenza; JSON/BSON semantico; intero envelope size guard; errore prima del
  replace conserva precedente; route/dedup/job progress e fallimento writer.
- Nel PATH non disponibili mongod, mongosh, docker; agente Mongo non ha trovato
  listener locale 27017. Nessun Mongo isolato di test dimostrato, nessuna
  connessione all'URI configurato eseguita. Indici/explain e round-trip live aperti.
- ATTENZIONE: codice corrente `MongoDB.java:165–166` usa PRODUCTION_DATABASE=
  `beebot_test` e TEST_DATABASE=`beebot_test_test`. Le indicazioni ADR con nomi
  `beebot`/`beebot_test` sono obsolete: il nome beebot_test non prova isolamento.
- BSON su popolazioni rappresentative, heap, source reale e rollout non misurati.
- Review API/documenti svolta: nessuna implementazione o esposizione nuova;
  controller, payload pubblico, presentazione, handbook, schema e ADR esistenti
  restano invariati. Questo rapporto è l'unico file aggiunto. La sincronizzazione
  dei documenti di contratto aspetta la decisione di schema.

Al primo gate la Fase 3 non era completata: richiedeva una decisione esplicita
sulla coesistenza e sul JSON semantico. Il successivo contratto utente ha risolto
questi blocchi tramite ownership su path disgiunti e JSON semantico schema 3.
Lo stato corrente e le verifiche sono in [phases.md](phases.md); la prontezza
per produzione resta subordinata ai gate reali.

Review del rapporto svolta in sola lettura da tutti i tre agenti: nessun errore
materiale segnalato. I chiarimenti detached mapping e progress aggregate/source
sono inclusi sopra.
