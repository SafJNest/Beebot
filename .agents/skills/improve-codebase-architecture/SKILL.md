---
name: improve-codebase-architecture
description: Audit Beebot architecture across packages, trace shared concepts and duplicated flows, and propose evidence-based consolidation and refactoring plans. Use for repository surveys, architecture reviews, cleanup plans, or focused investigations that must retain the whole-repository view.
---

# Improve codebase architecture

Ridurre la complessità di Beebot seguendo concetti, flussi e responsabilità attraverso tutta la repo. Un package è una posizione attuale, non il confine automatico dell'analisi. Riutilizzare i proprietari esistenti e valutare sia accorpamenti sia separazioni: meno file o metodi più corti non sono obiettivi autonomi.

## Perimetro e autorizzazione

- Un sopralluogo produce una mappa e proposte. Una revisione completa richiede anche copertura esplicita di classi e metodi. Un refactoring richiesto applica le modifiche nel perimetro autorizzato; l'analisi non autorizza da sola a cambiare codice operativo.
- Leggere `AGENTS.md`, `docs/architecture/README.md` e le decisioni pertinenti. Per Beebot consultare [riferimenti e vincoli](references/beebot.md). Le regole del progetto e del richiedente prevalgono sulle preferenze di questa skill.
- Il piano concordato è `docs/architecture/repository-review.md`: leggerlo prima di proporre spostamenti o riprendere il riordino. Quello è l'unico albero finale; aggiornare lì le nuove decisioni concordate, senza mantenere nella skill una seconda mappa divergente. Distinguere sempre destinazioni future, sorgenti della baseline e movimenti già eseguiti dall'utente.
- Prima di ordinare i rilievi applicare le esclusioni e le priorità concordate nel rapporto e nel riferimento Beebot. Un difetto già censito non torna automaticamente nel piano attivo se il proprietario lo ha rimandato. Per una proposta di ristrutturazione indicare classe sorgente, destinazione, operazioni da assorbire, chiamanti e condizione per rimuovere il vecchio file.
- Fotografare commit e modifiche preesistenti. Non alterare lavoro estraneo, dati reali o decisioni accettate. Segnalare un conflitto con un ADR e fermare solo l'intervento in conflitto, proseguendo l'indagine consentita.

## Prima la vista globale

1. Verificare CodeGraph con `codegraph status`; sincronizzare se necessario secondo le regole della repo. Usare `explore` per sorgenti e relazioni, `impact` prima di modificare. L'indice non inizializzato richiede la decisione dell'utente; non inizializzarlo silenziosamente. Non sostituire l'indice con una seconda scansione regex del codice.
2. Censire sorgenti attive, test, script, configurazione, schemi, documentazione e archivi. Il helper `scripts/inventory.py` usa Git e CodeGraph senza leggere credenziali né eseguire il bot. I suoi risultati sono metadati, non prova che il codice sia stato revisionato.
3. Identificare ingressi reali (Discord, HTTP, timer, avvio), dati canonici, risorse condivise e uscite. Individuare i consumatori anche fuori dal package che si sta studiando.
4. Prima di approfondire una sola area, costruire una tabella dei concetti trasversali: consumatori reali, proprietario attuale, comportamento condiviso, differenze da preservare. Includere almeno errori, identità/permessi, persistenza, conversioni/serializzazione, cache, esecuzione asincrona e gestione delle risorse quando presenti.

Esempio di inventario:

```bash
python3 .agents/skills/improve-codebase-architecture/scripts/inventory.py --repo . --output /tmp/beebot-architecture-inventory.json
```

Per una revisione esaustiva aggiungere `--symbols`. L'elenco AST comprende anche costruttori e classi annidate; riportare il significato dei conteggi e le eventuali limitazioni dell'indice.

## Esaminare un flusso

Seguire un caso d'uso dal suo ingresso a validazione, autorizzazione, servizio, cache, storage, integrazioni e presentazione. Confrontare almeno un altro ingresso allo stesso caso d'uso quando esiste: per esempio comando e dashboard. Non imporre che tutti i flussi abbiano tutti questi livelli.

Per ogni responsabilità sospetta:

- Leggere il proprietario, i chiamanti rilevanti e i test o contratti disponibili. Un import identifica una dipendenza sintattica; non dimostra una chiamata runtime, un accesso al database o un difetto.
- Verificare se si tratta dello stesso concetto o soltanto di codice simile. Confrontare input, output, errori, identità, permessi, transazioni, invalidazione, concorrenza e ciclo di vita.
- Considerare prima riuso o accorpamento con un proprietario esistente, poi lo spostamento, infine una nuova astrazione. Proporre una parte comune solo con consumatori attuali verificati e una semantica comune esplicita. Una futura funzione, da sola, non giustifica un framework condiviso.
- Mostrare dove va la complessità dopo la modifica: quali conoscenze spariscono dai chiamanti, quali passaggi vengono eliminati, quali dipendenze si aggiungono. Rifiutare una proposta che distribuisce la stessa complessità su più classi senza un beneficio concreto.
- Conservare le differenze di dominio dietro il meccanismo comune. Membership e amministrazione, timer e job, cache di dati e cache di file possono richiedere politiche diverse.
- Quando gli oggetti operativi già persistono con i propri setter, preservare questo modello se richiesto. Studiare identità delle istanze, caricamenti concorrenti, letture coerenti, riferimenti dopo eviction e transazioni composte prima di suggerire `synchronized` o una cache comune. Bot e API devono entrare nello stesso percorso; una query bulk non autorizza un secondo modello di stato.
- Cercare l'intero utilizzo prima di proporre eliminazioni, considerando Spring, reflection, registro comandi, callback e configurazione. Assenza di chiamanti statici non equivale a codice morto.
- Per prestazioni descrivere prima il lavoro evitabile (query ripetute, scansioni, blocchi, download duplicati), poi una misura utile. Non promettere velocità o riduzione della memoria senza dati rappresentativi.

## Copertura e continuità

- Nel sopralluogo distinguere `inventariato`, `sorgente esaminato parzialmente`, `flusso verificato` e `da approfondire`. Elencare campioni, esclusioni e limiti. Non presentare il numero di file indicizzati come numero di file letti.
- Nell'audit completo mantenere un registro per file e simbolo con stato, evidenza e decisione. Distinguere un corpo letto dai suoi usi verificati. Ricontrollare il registro rispetto all'inventario finale, includendo file nuovi o rimossi; non dichiarare completamento con elementi ancora non analizzati o senza una motivata esclusione.
- Salvare risultati e punti aperti in un rapporto durevole sotto `docs/architecture/`, con commit di riferimento. L'inventario può stare in un file separato; i giudizi manuali non devono essere sovrascritti rigenerando metadati.
- Gli approfondimenti LoL, Spring, audio o guild devono aggiornare la stessa mappa globale. Ogni passaggio termina controllando i consumatori esterni, le proposte di riuso e gli effetti sulle altre aree. Non creare una skill per package come separazione automatica del ragionamento.

## Risultato atteso

Consegnare una sintesi leggibile e un rapporto con:

- perimetro, baseline e copertura effettiva;
- mappa di ingressi, responsabilità e parti condivise, con un diagramma quando chiarisce le relazioni;
- rilievi ordinati per impatto pratico: evidenza con file/simbolo/linea, conseguenza, intervento minimo, proprietario proposto, consumatori coinvolti e verifica;
- distinzione tra fatti osservati, rischi da riprodurre, decisioni proposte e ipotesi di prestazione;
- accorpamenti possibili e separazioni che conviene mantenere, con motivazione;
- sequenza di interventi piccoli, dipendenze e controlli. Separare correzioni del comportamento da riorganizzazioni che lo preservano;
- parti già coerenti da riutilizzare e punti aperti realmente necessari.

Non assegnare punteggi arbitrari o applicare soglie di righe come verdetti. Non produrre nuovi layer, gerarchie o cartelle `shared`/`common` per principio.

## Quando si implementa

Applicare la sequenza autorizzata per gruppi coerenti. Verificare impatto prima del cambiamento e tutti i chiamanti dopo; preservare contratto API e presentazione. Usare test esistenti e aggiungere caratterizzazioni mirate ai comportamenti a rischio, senza replicare l'implementazione nei test. Sincronizzare la documentazione pertinente. Riportare esattamente i controlli eseguiti e i limiti delle verifiche offline.

## Provenienza

Procedura specifica per Beebot, sviluppata sulle richieste del proprietario. Riferimenti concettuali: [indagine architetturale di Matt Pocock](https://github.com/mattpocock/skills/blob/main/skills/engineering/improve-codebase-architecture/SKILL.md) e [pianificazione dei refactoring di GitHub](https://github.com/github/awesome-copilot/blob/main/skills/refactor-plan/SKILL.md). È autonoma: non richiede di installare o invocare quelle skill.
