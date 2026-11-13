# Analisi del core guild e sound

Questo documento raccoglie la discussione sull'organizzazione del core del bot, sulle API della dashboard e sulle modifiche da analizzare. È un'analisi e una proposta di lavoro, non una descrizione di modifiche già implementate né una decisione architetturale accettata.

## Principi richiesti

- Il core guild e sound è parte del bot da anni. Ogni modifica va studiata rispetto al codice esistente, ai suoi chiamanti e al comportamento attuale.
- Riutilizzare le funzionalità, i modelli e i metodi già presenti; unire le duplicazioni e rimuovere codice e file non più necessari.
- Usare il codice esistente come riferimento per nomi, struttura, ordine dei metodi, formattazione, spazi, tabulazioni e parentesi. Non introdurre uno stile estraneo al progetto.
- Mantenere responsabilità e logica nelle classi appropriate. Non spostare semplicemente il contenuto di una classe in un'altra per ridurre il numero di file.
- Le modifiche effettuate dai comandi e dal sito devono usare lo stesso percorso e risultare immediatamente coerenti tra loro.
- Le query necessarie possono stare in `BotDB`, comprese query bulk. Chi usa le guild deve recuperarle tramite `GuildCache`.
- Conservare il comportamento e la presentazione dei comandi e degli embed e il contratto API esistente, salvo modifiche esplicitamente richieste.

## Core guild esistente

`GuildCache` è il punto di accesso esistente a `GuildData`. `getGuild` cerca la guild nella cache e, se assente, la carica dal database; `getGuildOrPut` può crearla se manca. Comandi ed eventi lo usano già.

`GuildData` contiene le impostazioni generali e coordina gli oggetti esistenti, fra cui `ChannelData`, `BlacklistData`, `AlertData`, `RewardData`, `TwitchData` e le azioni automatiche. Parte dei dati è caricata alla costruzione; gli alert sono caricati al primo accesso.

Il flusso desiderato è:

```text
Comandi ─┐
         ├─> GuildCache ─> GuildData ─> AlertData / ChannelData / BlacklistData / ...
Sito ────┘                       └─────> BotDB
```

Il database resta la persistenza. La cache e gli oggetti del core sono il percorso condiviso per leggere e modificare la configurazione.

## Problemi dell'implementazione dashboard

`GuildService` costruisce una risposta dalla lettura SQL di `GuildSettingsDB`, invece di partire dalla `GuildData` già gestita da `GuildCache`. Gli aggiornamenti passano da un percorso SQL separato e poi invalidano la cache. L'invalidazione non aggiorna riferimenti a oggetti già in uso.

`GuildSettings` è una rappresentazione creata per la risposta API: contiene informazioni Discord e una mappa delle impostazioni, oltre a rappresentazioni di risultati ed alert. Non è l'oggetto operativo usato dal bot. Duplica parte delle informazioni presenti nei modelli esistenti.

L'obiettivo è eliminare `GuildSettingsDB` e `GuildSettings` come percorsi o modelli paralleli, dopo aver trasferito o riutilizzato le operazioni necessarie nelle classi proprietarie. La risposta API deve continuare a mantenere la forma documentata, incluse informazioni Discord, validità, risultati ed errori. Non è sufficiente serializzare direttamente l'oggetto operativo `GuildData`.

`GuildService` va ridotto eliminando letture e scritture duplicate. Solo dopo aver definito la divisione finale delle responsabilità si potrà decidere se il file sia eliminabile: non si devono trasferire indiscriminatamente validazione e composizione nel controller.

`DiscordGuildService` e `DiscordGuildCache` gestiscono invece le guild autorizzate per un utente e la relativa cache, non la configurazione `GuildData`. Valutare un accorpamento con il flusso di autenticazione esistente, ma non sostituirli automaticamente con `GuildCache` senza preservare permessi, sessioni e comportamento delle credenziali.

La cronologia esaminata colloca il percorso dashboard guild nel commit `63f11ad8` e le API sound nel commit `38b73c0c`. I commit indicano quando il codice è stato introdotto, non dimostrano una motivazione per le scelte progettuali.

## Coerenza e concorrenza

Usare la stessa cache non basta, da solo, a garantire la sincronizzazione. Sono emersi questi punti da affrontare:

- `GuildCache.getGuild` esegue controllo, caricamento e inserimento separatamente: accessi contemporanei possono creare più istanze per lo stesso ID.
- La cache ha scadenza e limite di elementi. Un oggetto può uscire dalla cache mentre un chiamante ne conserva ancora un riferimento; un successivo caricamento può produrre un'altra istanza modificabile.
- `GuildData.getAlerts()` assegna la mappa prima che il caricamento sia completo. Una lettura concorrente può osservare dati incompleti; un errore durante il caricamento può lasciare la mappa assegnata.
- Alcuni comandi e handler modificano direttamente la mappa restituita da `getAlerts()`. Le operazioni di creazione, inserimento e rimozione devono passare attraverso il proprietario, così da poter essere coordinate.
- `GuildData.deleteAlert()` rimuove l'alert dalla memoria prima dell'esito della cancellazione SQL. Se la cancellazione fallisce, memoria e database possono divergere.
- La creazione dei reward e altre operazioni composte effettuano più scritture separate. La sostituzione dei ruoli va trattata come operazione atomica, includendo correttamente la lista vuota.
- I setter esistenti non condividono tutti lo stesso coordinamento; non è sufficiente sincronizzare solo le scritture della dashboard. Vanno considerati anche lettori, oggetti figli, comandi, eventi e riferimenti già ottenuti.
- `AbstractDB.lineQuery` intercetta anche errori e restituisce una riga vuota. Il percorso di lettura deve distinguere una guild non presente da un database indisponibile.

La soluzione deve coordinare per guild caricamenti, letture e modifiche e gestire il ciclo di vita degli oggetti. Per ogni aggiornamento composto: validare, eseguire tutte le scritture necessarie nella stessa transazione, fare commit e rendere visibile lo stato condiviso in modo coordinato. Un errore non deve lasciare modifiche parziali in memoria o nel database. Le eventuali operazioni esterne, come la registrazione Twitch, non condividono la transazione SQL e devono mantenere il comportamento documentato.

Questa garanzia richiede che tutti i percorsi interessati partecipino allo stesso coordinamento. Una cache locale può coordinare un singolo processo; più istanze del bot o scrittori diretti esterni richiedono un meccanismo di coordinamento e coerenza tra istanze, da progettare secondo l'ambiente effettivo.

## Caricamento bulk

Valutare un accesso bulk a `GuildCache` per recuperare una lista di ID rapidamente:

1. Riutilizzare le istanze già presenti.
2. Caricare solo quelle mancanti tramite query aggregate in `BotDB`, suddividendo gli ID in gruppi quando necessario.
3. Coordinare caricamenti singoli e bulk sugli stessi ID, senza pubblicare copie indipendenti.
4. Distinguere gli ID assenti dagli errori del database e definire ordinamento, duplicati e risultati mancanti.
5. Una lettura non deve creare righe nel database.

L'attuale `retrieveAllGuilds()` non soddisfa già questi requisiti: costruisce nuove istanze e la query usata seleziona meno campi di quelli richiesti dal costruttore di `GuildData`. Inoltre il costruttore carica canali e azioni per singola guild: una query bulk limitata alla tabella `guild` lascerebbe query ripetute. Considerare caricamenti aggregati anche per canali, azioni, alert e ruoli dove richiesto, senza caricare sempre dati non necessari. Misurare query e prestazioni su dati rappresentativi.

## Contratto da preservare

Le API guild esistenti sono documentate in `docs/api/beebot-guilds.md`. In particolare vanno mantenuti:

- risposta GET con impostazioni, canali e ruoli Discord, senza creare configurazioni mancanti;
- autorizzazione e gestione degli errori esistenti;
- aggiornamenti parziali e transazioni indipendenti per blocco o elemento;
- validazione completa prima del salvataggio del blocco e conservazione dei campi omessi;
- creazione di alert mancanti con i valori previsti, inclusi alert ancora non validi;
- sostituzione dei ruoli, inclusa `[]` per rimuoverli tutti;
- risultati per blocco, stato e motivi di validità degli alert, inclusa la registrazione Twitch dopo il commit;
- presentazione, comandi ed embed invariati.

Aggiornare la documentazione quando il percorso di accesso e sincronizzazione cambia: oggi descrive `GuildSettingsDB` e l'invalidazione locale della cache.

## Sound: problemi individuati nella prima analisi

Anche il package `com.safjnest.sound` e le classi collegate richiedono una revisione con lo stesso criterio di riuso e responsabilità:

- `SoundCache` contiene anche operazioni API, validazione e logica di voto, oltre alla responsabilità di cache.
- `SoundAccess` introduce un percorso di verifica delle guild dell'utente separato da quello esistente per Discord. Va confrontato con i requisiti effettivi di membership e permessi prima di unificarlo: non assumere che i due controlli siano identici.
- `SoundAnalytics` costruisce risposte con mappe generiche; la forma delle risposte e i calcoli vanno ricondotti ai concetti e modelli appropriati.
- `SoundValues` mescola costruzione di mappe, validazione degli ID ed errori HTTP.
- `SoundDB` contiene, oltre alle query, controlli di accesso e regole applicative. Le transazioni e le analitiche possono richiedere nuove operazioni SQL, ma le responsabilità vanno riesaminate.
- `SoundQueryCache` e `SoundPeriod` vanno valutati rispetto a cache, utilità e modelli già presenti, mantenendo le funzionalità necessarie ed eliminando duplicazioni.

Questa è una prima analisi dei sound, non ancora una verifica completa di ogni percorso e dipendenza. Non eliminare funzionalità o cambiare contratti sulla sola base di questi rilievi.

## Verifiche per l'implementazione

- Modifica da comando e lettura dal sito; modifica dal sito e lettura da comando.
- Caricamento contemporaneo singolo e bulk degli stessi ID, cache vuota e piena, scadenza e rimozione durante operazioni in corso.
- Creazione, aggiornamento e cancellazione di guild, canali e alert, inclusi Twitch, reward e ruoli vuoti.
- Errori SQL e rollback per ogni fase di operazioni composte; nessuno stato parziale pubblicato.
- Letture concorrenti durante un aggiornamento e inizializzazione concorrente degli alert.
- Preservazione del contratto API, dei permessi, dei comandi e della presentazione; aggiornamento dei documenti interessati.
- Misure delle query bulk e verifica dei limiti effettivi. Integrazioni Discord, database reale e più istanze richiedono verifiche appropriate all'ambiente disponibile.

## Stato

La conversazione ha prodotto un'analisi e una direzione proposta. Nessuna delle modifiche descritte è stata implementata o verificata con test nell'ambito di questa analisi.
