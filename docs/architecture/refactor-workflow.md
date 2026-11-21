# Come lavoriamo al refactor di Beebot

Il [piano concordato](repository-review.md) contiene la struttura dei package e lo storico delle sei fasi. Questa guida raccoglie il metodo, le convenzioni e gli esempi da usare nei prossimi interventi. È anche il contesto dell’agente di progetto `beebot agent`.

## Prima di modificare il codice

1. Controlla branch, cambiamenti locali e istruzioni in [AGENTS.md](../../AGENTS.md). Usa CodeGraph per le domande strutturali. Verifica lo stato dell’indice e controlla i chiamanti prima di cambiare firme o responsabilità.
2. Segui lo stesso caso d’uso dal comando Discord e dall’endpoint API fino a cache, modello e persistenza. Cerca responsabilità e invarianti in comune anche fuori dal package interessato.
3. Mostra il percorso proposto con un esempio. Per ogni spostamento indica il file attuale, la destinazione, le operazioni che assorbe e quando si può eliminare il vecchio file. Distingui i problemi osservati dai rischi ancora da verificare.
4. Durante l’analisi resta in lettura. Dopo il via libera, intervieni per gruppi piccoli. Se i file sono già stati spostati dal proprietario, controlla package, import e registrazioni e crea i nuovi file nella destinazione concordata.

## Un proprietario per il comportamento condiviso

- Comandi, eventi e API usano la stessa cache e gli stessi oggetti. I controller gestiscono input, permessi e risposta HTTP. Prima di eliminare un service o repository duplicato, migra anche chiamanti indiretti, fake dei test e registrazioni.
- Il bulk riusa gli oggetti presenti e carica insieme i soli mancanti. Con 10 hit e 10 miss si caricano 10 ID. Una richiesta singola e una bulk sovrapposte condividono il caricamento per chiave. La risposta HTTP attende il risultato completo e conserva l’ordine richiesto.
- Gli oggetti di dominio conservano i setter persistenti. La cache tiene stabile l’identità durante la callback e coordina gli accessi per chiave. Voti e statistiche personali di un suono stanno nelle copie di risposta.
- `exception.Exception` è l’errore applicativo comune. Chi lo solleva fornisce messaggio e codice. `ApiErrors` e `ApiExceptionHandler` traducono il risultato HTTP. L’autenticazione appartiene a `discord.auth`, con le query in `AuthDB`. Lista amministrativa e membership per i suoni applicano permessi diversi.
- `HttpUtils` condivide costruzione e invio. Le integrazioni conservano timeout, decoding e interpretazione degli status propri. `ParseUtils` condivide conversioni con la stessa semantica di trim, fallback e valori nulli. Per gli enum usa il resolver esistente, ad esempio `AudioType.getFromOrdinal()`.
- Le nuove letture SQL seguono `QueryRecord` e il parser delle righe dove applicabile. Le scritture mantengono statement preparati, transazioni ed esito per righe modificate. La struttura SQL e Mongo di LoL restano fuori dal refactor concordato.

## Esempio di modifica a una gilda

Un setter singolo salva già sul database.

```java
boolean saved = GuildCache.withGuild(guildId, guild -> guild.setPrefix(prefix));
```

Per modificare più campi di un alert esistente, lavora su una copia e salva una volta.

```java
boolean saved = GuildCache.withGuild(guildId, guild -> {
    AlertData edit = guild.getAlert(AlertType.LEAVE).deferEdit();

    edit.setMessage(message);
    edit.setPrivateMessage(privateMessage);

    return edit.update();
});
```

`deferEdit()` prepara una copia privata. `update()` salva i campi del solo blocco con SQL esplicito e aggiorna l’originale dopo il commit. Un errore, una cancellazione o una revisione superata lasciano intatto l’originale. L’endpoint settings dà un esito per blocco o elemento. Le chiamate esterne, come la registrazione Twitch, partono dopo il commit e fuori dal lock. [Dettagli del passaggio 2](repository-review.md#passaggio-2-implementato-copie-di-modifica-e-salvataggi-espliciti)

## Stile Java

- Mantieni il raggruppamento dei campi e delle assegnazioni nei costruttori. Conserva le righe vuote e i banner ASCII. Applica lo stesso criterio ai blocchi nuovi e controlla il diff per individuare cambi di formato involontari.
- Separa guardie, preparazione, caricamento, trasformazione e ritorno con una riga vuota. Scrivi il corpo di `if`, `else`, `for` e `while` sulla riga indentata successiva. Dai un nome al criterio di una condizione quando per capirlo occorre ricostruire più confronti.
- Usa tipi locali e import espliciti nei file riscritti. `var` è ammesso dentro una lambda se aiuta a leggere. I singleton si chiamano `instance`. Usa cicli, switch expression e factory statiche quando rendono l’intento evidente. Il target Java si legge dal `pom.xml`.
- Negli analytics separa caricamento, accumulo e composizione della risposta. Usa tipi per chiavi, conteggi e tempi. Costruisci le mappe JSON dove serve al contratto. `SoundHistory` mostra come separare accumulo streaming, filtri del cursore e buffer limitato. Estrai una classe quando possiede una responsabilità riconoscibile.

## Audio e storage

`SoundCache` coordina identità e visibilità, `Sound` contiene il comportamento del modello, `BotDB` le query e `SoundFiles` la gestione dei file e il collegamento a R2. Per il lookup da comando usa la stessa regola di visibilità per nome e ID nella gilda corrente.

- Il caricamento degli effetti passa per `PlayerManager.loadSound`. Comandi e pulsanti usano `TrackScheduler.play(track, type)`. Per gli effetti lo scheduler delega a `GuildMusicManager.playSound`, che avvia la traccia e restituisce un UUID per fermarla con `stopSound`. Il flag di gilda `overlapSound` decide se fermare gli altri effetti prima della partenza. `AudioForwarder` miscela il player della coda e i player degli effetti in PCM stereo a 48 kHz, riusa lo stesso buffer PCM per l'input dell'encoder Lavaplayer e passa a JDA pacchetti Opus già codificati. Comandi e pulsanti Stop chiamano tutti `TrackScheduler.stop()`, che ferma effetti e musica. La disconnessione libera gli effetti e l'encoder e svuota la coda.
- Il TTS genera la traccia in memoria e passa dallo stesso ingresso dei suoni. Con l'overlap attivo si somma ai suoni già in riproduzione.
- In lettura riusa la copia locale. In caso di miss, pubblica la copia solo quando il download dal CDN è completo. Player, greet, pulsanti e download Discord riusano questo percorso.
- In upload valida il file, calcola `duration_ms` una volta e riserva l’ID in MariaDB. Usa quell’ID per caricare l’oggetto R2 con i metadati, tag inclusi. Gli edit aggiornano la riga e i metadati dell’oggetto. API ed embed leggono la durata dal DB.
- Le copertine usano il bucket audio e una chiave `soundboard/thumbnail/<soundboardId>/<uuid>.<ext>`. Il DB conserva la chiave e l’embed usa il CDN. Per errori, cancellazione e conservazione consulta [il contratto R2](../audio-storage-r2.md).
- I comandi di migrazione di metadati e copertine e il fallback ai BLOB sono stati rimossi. Le copie sul server e gli oggetti R2 vengono conservati. Cleanup e autorizzazione dei suoni privati richiedono una decisione distinta.

## Verifica e consegna

- Verifica i contratti toccati, inclusi permessi, rollback, concorrenza e dati preesistenti. Usa test di caratterizzazione per i comportamenti che rischiano di cambiare, come date, cursori e ordinamento negli analytics.
- Compila e lancia la suite locale quando possibile. Indica cosa prova ogni controllo. H2, MockMvc e fake di Redis o R2 coprono casi locali. Comportamento su VPS, integrazioni reali e prestazioni richiedono evidenze dedicate.
- Rileggi il diff e verifica che i chiamanti usino il nuovo percorso, che il codice assorbito sia stato rimosso e che la documentazione canonica sia aggiornata. Conserva campi, ordine, testo, layout e output Discord quando cambia soltanto la sorgente dei dati.
- Fai commit solo quando richiesto per quella fase. Se il proprietario chiede di committare la fase precedente mentre lavori su quella corrente, includi solo la fase precedente. Un commit locale e la pubblicazione hanno autorizzazioni distinte.
- Alla fine di ogni fase usa il promemoria richiesto, «fai commit prima che inzii con una nuova fase refactor».

## Chiusura di novembre 2026

Le sei fasi e la pulizia Sound sono state confermate chiuse. Logging, `Chronos`, BotStatus, il vecchio `bot.audio.SoundAnalytics` e la possibile rimozione di `Sound.isOpus()` o del costruttore vuoto sono fuori dal perimetro. Valgono anche le esclusioni originali su MongoDB, struttura SQL, nomi storici e riscritture generali di comandi e handler. [Perimetro e stato del piano](repository-review.md#perimetro-attivo)
