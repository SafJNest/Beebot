# Cache HTTP delle API LoL

Analisi del 19 novembre 2026. Le configurazioni sotto sono proposte per Beebot e Iodid.

## Decisione consigliata

Cloudflare può aggiornare la cache da solo dopo la scadenza del TTL. Il bot deve intervenire soltanto se vogliamo che **lo stesso URL già in cache** rifletta un aggiornamento prima della scadenza per tutti i visitatori. Cloudflare riceve richieste HTTP e risposte dall’origine. Le scritture in MongoDB e le invalidazioni Redis avvengono a monte di quel punto. [Rivalidazione Cloudflare](https://developers.cloudflare.com/cache/concepts/revalidation/)

Per Beebot partirei con tre politiche.

| Lettura | Politica iniziale | Perché |
| --- | --- | --- |
| Dettaglio del match concluso | Cache Cloudflare lunga sul `200` pronto. La regola attuale usa 7 giorni. | Gli stessi dati servono a visitatori diversi. Una correzione dei dati o del formato può giustificare un purge mirato. |
| Tier list, statistiche champion e distribuzioni pubbliche | TTL automatico, per esempio 5–15 minuti per le risposte complete. | Le aggregazioni costano e tollerano un breve ritardo. Patch, regione, ruolo, filtri e paginazione fanno parte della chiave. |
| Profilo e letture che devono riflettere Update e F5 | Cloudflare in bypass all’inizio, riuso di Redis. Valutare `ETag` per le letture. | Un F5 raggiunge Beebot e vede ciò che il backend ha già pubblicato. La pubblicazione di storico e aggregati può richiedere più tempo. |

Un `202`, un errore o una risposta parziale può descrivere uno stato temporaneo. Per i JSON mutabili, far dichiarare a Spring quali risposte sono complete e condivisibili. Un override che assegna un TTL a ogni `200` può conservare anche uno stato parziale. [Cache Rules ed Edge TTL](https://developers.cloudflare.com/cache/how-to/cache-rules/settings/)

## Il flusso attuale di Update

1. Iodid esegue `POST /api/lol/{shard}/profile/{puuid}/refresh`. Poi invalida le query locali TanStack del profilo e delle relative viste. Le richieste ripartono verso l’API e possono ancora ricevere un hit da Cloudflare.
2. Il controller chiama `SummonerService.refresh`, marca il profilo come visto, accoda lo storico e risponde `204`. Il servizio può ignorare il refresh per cooldown o in caso di errore e il controller restituisce comunque `204`. Quando il refresh riesce, il servizio accoda il ricalcolo degli aggregati in un job separato.
3. Alcune letture possono dare `200` con `metadata.refresh=true` mentre il calcolo procede. La GET del profilo segnala questa condizione anche con `X-Profile-Refresh: true`. Il `202` copre gli stati in cui la risposta richiesta è ancora in preparazione.

Il contratto attuale del `204` va quindi chiarito prima di usarlo per promettere che **tutte le viste** sono aggiornate. La pagina di documentazione del refresh descrive lo storico come indipendente, mentre il controller di questo checkout lo accoda. Conviene distinguere nel frontend i dati del profilo già disponibili dalle sezioni ancora in aggiornamento. [Controller](../../src/main/java/com/safjnest/spring/controller/lol/LolController.java#L70) · [Servizio Summoner](../../src/main/java/com/safjnest/lol/service/SummonerService.java#L117) · [Servizio Profile](../../src/main/java/com/safjnest/lol/service/ProfileService.java#L307) · [Contratto documentato](../api/summoner/profile-refresh.md)

Redis resta utile anche quando saltiamo la cache HTTP. Il checkout applica attualmente un override di 60 secondi alla maggior parte delle cache dati con scadenza. Questo TTL governa quanto a lungo resta una copia in Redis e va considerato insieme al tempo di attesa dei job e all’eventuale TTL Cloudflare. [RedisKey](../../src/main/java/com/safjnest/database/redis/RedisKey.java#L67)

## Opzioni per aggiornare la risposta

| Meccanismo | Cosa succede al prossimo GET | Quando serve |
| --- | --- | --- |
| **TTL** | A scadenza, la prossima richiesta può rivalidare con l’origine. Finché la copia è fresca, Cloudflare può riusarla. | Per dati pubblici che accettano un breve ritardo. Il bot lascia che il TTL faccia il suo lavoro. |
| **ETag e `If-None-Match`** | Quando arriva una rivalidazione, l’origine può rispondere `304` se la rappresentazione è uguale, altrimenti `200` con i nuovi dati. | Per risparmiare il trasferimento del JSON invariato. Se il tag si calcola solo dopo aver costruito tutto il JSON, il risparmio è soprattutto di banda. |
| **Bypass esplicito dopo Update** | Il frontend richiede la risposta all’origine per quel caricamento. Gli URL standard eventualmente già cacheati conservano la loro copia. | Quando deve vedere il cambiamento subito solo chi ha premuto Update. Un F5 normale su un URL ancora cacheato può tornare alla copia vecchia. |
| **Purge per tag o prefisso** | Cloudflare rimuove le copie interessate. La prossima richiesta torna all’origine e può popolare la cache con i dati nuovi. | Quando serve visibilità immediata per tutti sullo stesso URL. Eseguire dopo la pubblicazione dei dati, includendo le varianti interessate. |
| **URL con versione** | Un nuovo URL usa una nuova chiave. I client che conoscono ancora il vecchio URL possono continuare a ricevere la vecchia copia. | Asset e snapshot identificati da una versione stabile. Per il profilo mutabile richiede di pubblicare e scoprire la revisione corrente. |

`ETag` non è un segnale con cui Spring avvisa Cloudflare in anticipo. Se Cloudflare ha ancora una copia fresca, può anche rispondere a una richiesta condizionale del browser usando quella. Un tag basato su una revisione interna è utile se cambia ogni volta che cambia la rappresentazione, incluse le componenti che possono aggiornarsi separatamente. [Rivalidazione Cloudflare](https://developers.cloudflare.com/cache/concepts/revalidation/) · [Richieste condizionali GitHub REST](https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api#use-conditional-requests-if-appropriate)

`POST /profile/{puuid}/refresh` e `GET /profile/{puuid}` hanno URL diversi. Lo standard HTTP richiede l’invalidazione dell’URI a cui è diretta una mutazione riuscita e tratta gli altri URI separatamente. Il `POST` attuale non offre una garanzia di invalidazione globale del profilo, delle sue varianti o degli URL come `/matches`. [RFC 9111, §4.4](https://www.rfc-editor.org/rfc/rfc9111.html#section-4.4)

Cloudflare offre purge per URL, prefisso e tag su tutti i piani. I limiti cambiano per piano e operazione. Questa è una correzione rispetto all’indicazione iniziale sul purge per tag. Se un giorno serve, un tag comune come `profile-euw1-<puuid>` può raggruppare le varianti pubbliche del profilo. Il bot dovrebbe inviare il purge quando il risultato è leggibile e considerare le letture vecchie ancora in volo. L’endpoint `invalidate_cache` è una rivalidazione anticipata con regole proprie per i dati scaduti. Il purge rimuove invece la copia. [Disponibilità e limiti](https://developers.cloudflare.com/cache/how-to/purge-cache/#availability-and-limits) · [Purge per tag](https://developers.cloudflare.com/cache/how-to/purge-cache/purge-by-tags/) · [Invalidazione Cloudflare](https://developers.cloudflare.com/cache/guides/invalidate-cache/)

## Come configurerei la CDN

- **Match.** Usare la regola già provata per il dettaglio, con TTL lungo sul `200` e bypass per gli stati temporanei. Il purge resta un’operazione per correzioni eccezionali. La regola esistente contiene un filtro `GET`, che Cloudflare documenta come possibile ostacolo al purge per singolo URL. [Limiti del purge per URL](https://developers.cloudflare.com/cache/how-to/purge-cache/purge-by-single-file/)
- **Aggregazioni pubbliche.** Rendere eleggibili solo i percorsi scelti. Impostare Cloudflare per seguire gli header dall’origine e mantenere i parametri funzionali nella chiave. Spring può inviare `Cache-Control: no-cache` ai client e `Cloudflare-CDN-Cache-Control: public, max-age=300` per 5 minuti all’edge, sulle sole risposte complete. Per le altre risposte usare `Cache-Control: no-store` e lasciare assente il TTL per l’edge. Usare **Browser TTL → Respect origin** ed evitare override applicati per status sulla stessa regola. [Precedenza degli header](https://developers.cloudflare.com/cache/concepts/cdn-cache-control/) · [TTL di edge e browser](https://developers.cloudflare.com/cache/how-to/edge-browser-cache-ttl/) · [Chiavi di cache](https://developers.cloudflare.com/cache/how-to/cache-keys/)
- **Profilo.** Tenere le letture interessate da Update fuori dalla cache HTTP condivisa, comprese le risposte di pagina o SSR che potrebbero riutilizzarle. Verificare che il refetch di Iodid raggiunga Beebot. Se in futuro si prova un TTL breve, il bypass esplicito dopo Update deve essere riconosciuto da una regola Cloudflare e da eventuali proxy intermedi. Per garantire lo stesso comportamento anche al normale F5 di qualsiasi visitatore, serve mantenere il bypass su quelle letture oppure collegare l’invalidazione alla pubblicazione del dato.

Per una prima verifica in canary, ripetere lo stesso GET e confrontare `CF-Cache-Status`, `Age` e tempi. Poi provare Update, F5 e una seconda sessione, distinguendo i dati già pubblicati dalla risposta con `metadata.refresh=true`. Su una risorsa con TTL misurare anche la prima richiesta dopo la scadenza. Verificare il contenuto restituito oltre agli header. [Stati della cache Cloudflare](https://developers.cloudflare.com/cache/concepts/cache-responses/)

## Esempi documentati

- **GitHub REST** espone validatori HTTP. Il client invia `If-None-Match` e può ricevere `304` se i dati sono invariati. È il modello utile per richieste ripetute al profilo quando serve verificare la freschezza. [GitHub](https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api#use-conditional-requests-if-appropriate)
- **Next.js** espone due comportamenti distinti a livello applicativo. `revalidateTag` può aggiornare in background mentre mostra una copia precedente. `updateTag` fa attendere la lettura successiva finché i dati freschi sono disponibili. Questa distinzione si applica anche a Iodid, ma la cache Next.js e quella Cloudflare sono autonome. [Next.js](https://nextjs.org/docs/app/getting-started/revalidating)
- **Riot Data Dragon** pubblica file di gioco con la patch nell’URL e una lista delle versioni. AWS raccomanda lo stesso approccio per file che cambiano. Funziona bene quando l’app sa già quale versione chiedere, come per gli asset e meno direttamente per un profilo vivo. [Riot](https://developer.riotgames.com/docs/lol#data-dragon) · [AWS CloudFront](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/UpdatingExistingObjects.html)

Questa analisi usa i checkout locali e le fonti pubbliche. Le prove di refresh, dei TTL proposti e della cache Iodid a runtime sono da eseguire in canary.
