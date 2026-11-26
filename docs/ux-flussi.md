# Struttura schermate e flussi di navigazione

## Principi "zero friction"

1. **Un pollice, un tocco.** Ogni azione frequente in palestra (spuntare una serie, correggere il peso, avviare il recupero) è raggiungibile con un tocco su un'area di almeno 56dp; le azioni principali sono alte 64dp.
2. **Pre-compilato, non vuoto.** Peso, ripetizioni e regolazione macchinario arrivano dall'ultima volta: nel caso tipico l'utente conferma, non digita.
3. **Mai la tastiera di sistema** per i numeri: tastierino dell'app con tasti da 64dp, ±2,5 kg / ±1 rip., la prima cifra sostituisce il valore pre-compilato.
4. **Leggibile a un braccio di distanza:** tema scuro, cifre tabulari grandi, un solo colore d'accento (lime) riservato alle azioni e ai completamenti.
5. **Offline sempre:** ogni schermata legge dal database locale; la rete serve solo per le immagini non ancora in cache.
6. **Conferma tattile** su ogni azione importante, così si può loggare senza guardare lo schermo a lungo.

Obiettivo misurabile: **un principiante logga la prima serie in meno di 5 secondi** dal tocco su "Inizia allenamento" (pre-compilazione + un tocco sulla spunta).

## Mappa delle schermate

```mermaid
flowchart TD
    subgraph Bar[Barra inferiore]
        T[Oggi]
        S[Schede]
        E[Esercizi]
        P[Progressi]
        D[Dieta]
    end

    T -->|Inizia allenamento| L[Workout Logger<br/>a tutto schermo]
    T -->|tocco esercizio| ED[Scheda esercizio]
    T -->|nessuna scheda| S
    L -->|tastierino| K[Tastierino numerico]
    L -->|Sostituisci| AP[Selettore esercizi<br/>alternative]
    L -->|Regolazione| MS[Regolazione macchinario]
    L -->|Termina| WS[Riepilogo allenamento]
    WS --> P

    S -->|Usa modello| T
    S -->|tocco scheda / Nuova scheda| RB[Builder scheda]
    RB -->|aggiungi / sostituisci esercizio| EP[Selettore esercizi]
    RB --> CAL[Calendario settimanale]
    RB -->|Duplica o crea variante| RB
    ED -->|Aggiungi a una scheda| RB

    E --> ED
    E -->|Nuovo esercizio| EE[Editor esercizio<br/>personalizzato]
    EE --> ED
    ED -->|Modifica| EE
    ED -->|Alternative| ED
    AP -->|Crea «testo cercato»| EE
    ED -->|Aggiungi a scheda| RB

    P --> CH[Grafico esercizio<br/>1RM, carico max, volume, ripetizioni]
    P --> BW[Peso corporeo<br/>media mobile 7 gg]
    P --> SH[Dettaglio allenamento]
    P -->|Registra peso| WE[Foglio pesata<br/>tastierino]
    BW --> WE
    CH --> SH
    SH --> ED
    ED -->|Progressi| CH

    D --> DP[Editor piano alimentare]
    D --> DL[Piani alimentari]
    DL --> DP
    T -->|ingranaggio| ST[Impostazioni<br/>kg/lb, RPE/RIR, backup]
```

| Sezione | Schermata | Contenuto | Fase |
|---|---|---|---|
| Oggi | Oggi | Allenamento previsto oggi dalla scheda attiva, riposo con prossimo allenamento, oppure invito a scegliere una scheda; "Inizia" / "Riprendi allenamento", "Allenamento libero" nei giorni senza allenamento | 3 ✅ 4 ✅ |
| Oggi | Workout Logger | Card esercizio: regolazione macchinario, righe serie (peso, reps, RPE, spunta), tastierino, timer di recupero, sostituzione al volo | 4 ✅ |
| Oggi | Selettore esercizi | Alternative ordinate per somiglianza (sostituzione) o usati di recente (aggiunta), ricerca | 4 ✅ |
| Oggi | Riepilogo | Durata, serie, volume, calorie bruciate (stima), record di carico, miglior serie per esercizio | 4 ✅ calorie ✅ |
| Schede | Elenco | Schede con calendario settimanale, scheda attiva, varianti, modelli PPL / Upper-Lower / Full body, "Nuova scheda" | 3 ✅ 6 ✅ |
| Schede | Builder | Calendario della settimana, giorni (aggiungi, rinomina, duplica, sposta, elimina), esercizi riordinabili trascinando, obiettivi serie/ripetizioni/recupero, superserie, spostamento tra giorni, duplicazione e settimana di scarico | 6 ✅ |
| Esercizi | Libreria | Ricerca italiano/inglese senza accenti, chip Preferiti e gruppo muscolare, pannello filtri (attrezzatura, tipo di esercizio, muscolo specifico, muscoli secondari, solo i miei), "Nuovo esercizio" | 3 ✅ 5 ✅ |
| Esercizi | Scheda esercizio | Video loop (personale o di catalogo, rallentatore 0,5×) o fotogrammi animati, mappa muscolare, le tue regolazioni salvate, esecuzione, punti chiave, errori comuni, alternative, preferiti | 3 ✅ 5 ✅ |
| Esercizi | Editor esercizio | Nome, attrezzatura, cosa registrare, muscoli principali (il primo è il target) e secondari, movimento, un lato alla volta, recupero, regolazioni da annotare, note | 5 ✅ |
| Progressi | Panoramica | Peso corporeo (ultima pesata, media 7 giorni, variazione settimanale, mini-grafico 90 giorni, "Registra peso"), andamento del 1RM stimato per esercizio, record personali, calorie della settimana e degli ultimi 30 giorni, storico allenamenti con le kcal di ciascuno | 3 ✅ 7 ✅ calorie ✅ |
| Progressi | Peso corporeo | Profilo corporeo (altezza, anno di nascita, sesso biologico), periodo 1 mese–Tutto, grafico pesate + media mobile 7 giorni, statistiche, elenco pesate (modifica, elimina con Annulla) | 7 ✅ calorie ✅ |
| Progressi | Esercizio | 1RM stimato / carico massimo / volume / ripetizioni per periodo, ultima e migliore, variazione, record di sempre, sessioni | 7 ✅ |
| Progressi | Dettaglio allenamento | Data e orari, durata, serie, volume, calorie con il peso usato per la stima, serie per esercizio (R, D, C, RPE/RIR), "Elimina allenamento" | 7 ✅ |
| Dieta | Oggi | Pasti del piano attivo con spunta, calorie e macro rispetto ai target; "Crea un piano" se manca, scorciatoie a editor e piani | 3 ✅ 8 ✅ |
| Dieta | Piani alimentari | Elenco con piano attivo e kcal obiettivo, "Nuovo piano" | 8 ✅ |
| Dieta | Editor piano | Obiettivi giornalieri a confronto con il totale del piano, pasti (tipo, nome, orario, sposta, elimina), alimenti con quantità e macro (kcal calcolate dai macro), note, attiva / duplica / elimina | 8 ✅ |
| Oggi | Impostazioni | Unità di peso kg/lb, scala dello sforzo RPE/RIR, esporta e ripristina backup, versione | 8 ✅ |

## Regole di navigazione

- **Cinque sezioni nella barra inferiore**, visibili solo al primo livello. Cambiare sezione conserva lo stato (scroll, testo cercato, filtro) e non duplica le schermate.
- **Dettagli sopra la sezione** (scheda esercizio, builder): barra inferiore nascosta, freccia indietro in alto a sinistra, gesto indietro di sistema.
- **Workout Logger a tutto schermo:** nessuna barra. Freccia o gesto indietro **riducono** il logger senza chiedere nulla: la sessione resta "in corso" (anche chiudendo l'app) e si riprende con un tocco dal banner sopra la barra inferiore, da "Riprendi allenamento" in Oggi o dalla notifica del recupero. Solo **Termina** e **Scarta** chiedono conferma. Con il tastierino aperto, indietro chiude il tastierino.
- **Route tipizzate:** gli argomenti (es. `ExerciseDetailRoute(exerciseId)`) sono verificati in compilazione.

## Flussi principali

### 1. Primo avvio → primo allenamento (≤ 4 tocchi)
1. Oggi: "Nessuna scheda attiva" → **Vai alle schede**.
2. Schede: modello "Push Pull Legs" → **Usa questo modello** (scheda creata, attivata, calendario lun/mer/ven).
3. Oggi: allenamento del giorno → **Inizia allenamento** (la prima volta Android chiede il permesso per le notifiche del recupero; l'allenamento parte comunque).
4. Logger: prima serie pre-compilata → **spunta**. Parte il timer di recupero.

### 2. Loggare una serie (1 tocco nel caso tipico)
- Valori uguali alla volta scorsa → tocco sulla spunta.
- Peso diverso → tocco sul campo peso → tastierino (prima cifra sostituisce) oppure ±2,5 → **Avanti** sposta il fuoco sulle ripetizioni, poi sul peso della serie successiva → spunta.
- Spunta su una riga lasciata vuota = "come prima": i valori mancanti vengono copiati dalla serie precedente di oggi (60 kg sulla 1ª → 60 kg sulla 2ª) o, per la prima serie, dallo storico.
- Ogni cifra è salvata subito nel database: chiudere l'app a metà serie non perde nulla.

### 3. Regolazione macchinario
- Card espansa di default sui macchinari, valori dell'ultima volta già compilati (es. *Schienale 3 · Rullo tibie 5*).
- Modifica di un valore → salvata sul profilo e sullo storico della sessione.
- Altra palestra → "Salva come nuovo profilo"; in seguito si sceglie il profilo dal menu.
- **Fuori allenamento ("a freddo")**: scheda esercizio → sezione **I miei settaggi / Note macchinario**, sempre presente. Matita = modifica di voci, note e nome del profilo; **Aggiungi settaggi** / **Aggiungi profilo** per crearne uno; **Elimina profilo** nell'editor (lo storico conserva i valori usati in ogni sessione). Non serve avviare un allenamento.
- Il profilo salvato per ultimo è quello marcato "usato dal logger": al prossimo allenamento voci e note compaiono già nella card. Se un allenamento è in corso, la card si aggiorna subito.

### 4. Macchina occupata → sostituzione al volo
- Menu esercizio → **Sostituisci esercizio** → alternative ordinate per somiglianza: muscoli coinvolti, meccanica, metriche registrabili, nome, esercizi base e preferiti. Per lo stacco rumeno: stacco rumeno in deficit, stacco a gambe tese con bilanciere / manubri / al multipower; girate e salti finiscono in fondo.
- Le serie già fatte restano sull'esercizio originale; quelle mancanti passano al nuovo ("Sostituito al volo"), con la sua regolazione.

### 5. Timer di recupero
- La spunta avvia il recupero dell'esercizio (es. 3:00 per lo squat) con il nome del prossimo esercizio da fare; ±15 s e **Salta** nel pannello. Nessun recupero dopo l'ultima serie.
- Fuori dall'app o a schermo spento: notifica con conto alla rovescia disegnato dal sistema (nessun servizio in primo piano) e, alla fine, notifica "Recupero finito" con vibrazione, lanciata da un allarme. Con il logger aperto l'avviso è solo una vibrazione.
- L'avviso a schermo spento è puntuale solo con il permesso **Sveglie e promemoria** (negato di default da Android 14): senza, il sistema può ritardarlo di minuti. Il pannello del recupero lo propone (**Consenti** apre la schermata di sistema, **Non ora** lo nasconde per sempre); concesso il permesso, l'allarme in corso diventa subito esatto.
- Il recupero sopravvive alla chiusura dell'app; uno già scaduto non viene ripristinato.

### 6. Libreria: trovare l'esercizio giusto
- Chip sempre a portata di pollice: **Filtri**, **Preferiti**, **Tutti** e i gruppi muscolari.
- **Filtri** apre un pannello con attrezzatura (più scelte = "oppure"), tipo di esercizio (forza, olimpici, pliometria, cardio), muscolo specifico (petto alto, deltoide posteriore…), "Includi i muscoli secondari" e "Solo i miei esercizi". Categorie diverse si combinano ("Bilanciere" e "Femorali"). Il pulsante in fondo dice già quanti esercizi mostrerà; "Azzera" toglie solo i filtri del pannello.
- Un muscolo specifico prende il posto del gruppo (più preciso); scegliere un altro gruppo toglie i muscoli degli altri gruppi. I filtri sopravvivono alla rotazione e alla chiusura dell'app.

### 7. Esercizio personalizzato
- Libreria → **Nuovo esercizio**, oppure nel logger: ricerca senza risultato → **Crea «testo cercato»** (nome già compilato).
- Obbligatori solo il nome e un muscolo principale; il resto ha valori sensati (scegliendo "Corpo libero" o "Cardio" l'app propone cosa registrare). Se nella libreria c'è già un esercizio con lo stesso nome compare un avviso, senza bloccare.
- Dal logger l'esercizio salvato entra subito nella sessione; dalla libreria si apre la sua scheda. Uscire con modifiche non salvate chiede conferma.
- Scheda → menu **⋮** → Modifica / Elimina. Mai usato: eliminato con il suo video. Già in schede o storico: solo nascosto dalla libreria, lo storico resta.

### 8. Video personale e alternative
- Scheda esercizio (anche di catalogo) → **Aggiungi il tuo video** → *Registra un video* (app fotocamera, nessun permesso) o *Scegli dalla galleria* (selettore di sistema, nessun permesso). Il file è copiato nella memoria privata dell'app (massimo 200 MB), escluso dal backup cloud e portato nel trasferimento a un nuovo telefono.
- Il video gira in loop senza audio, si ferma a schermo spento; tocco = pausa, **0,5×** = rallentatore. Se il decoder hardware del telefono non lo legge si riprova con quello software; se resta illeggibile un avviso sostituisce il video e restano i fotogrammi.
- Con un video personale e media di catalogo, due chip scelgono tra "Il mio video" e "Catalogo".
- In fondo alla scheda, **Alternative**: le 5 più simili (stessi muscoli, stesso tipo di movimento, metriche compatibili); un tocco apre la loro scheda.

### 9. Costruire o modificare una scheda
- Schede → **Nuova scheda** (nome, poi si apre il builder con "Giorno A"), oppure tocco su una scheda esistente. La prima scheda creata diventa attiva da sola; le altre si attivano con "Imposta come attiva".
- **Settimana:** tocco su un giorno (L…D) → scelta del giorno di scheda o "Riposo". Lo stesso giorno di scheda può ripetersi (Giorno A lunedì e giovedì).
- **Giorni:** chip per passare da un giorno all'altro, "+ Giorno" per aggiungerne uno; menu ⋮ del giorno per rinominare, duplicare, spostare prima/dopo, eliminare (i giorni della settimana che lo usavano diventano riposo).
- **Esercizi:** "Aggiungi esercizio" apre il selettore (con "Crea «…»" se non c'è). La maniglia a sinistra si trascina per riordinare; per TalkBack ci sono le azioni "Sposta su / giù". Tocco sulla riga = obiettivi (serie, ripetizioni da–a, recupero con "Usa il predefinito"). Menu ⋮: scheda esercizio, sostituisci, superserie con il successivo / separa, sposta in un altro giorno, rimuovi.
- **Superserie:** una barra ambra collega gli esercizi consecutivi del gruppo. Trascinare un esercizio fuori dal gruppo lo separa; spostarlo in un altro giorno pure. Nel logger tra un esercizio e l'altro della superserie non parte il recupero: si recupera solo a fine giro, poi si riparte dal primo.
- **Duplica o crea variante:** copia completa collegata all'originale ("Variante di «…»"); con "Settimana di scarico" le serie scendono del 40% circa (4 → 2, 3 → 2, 5 → 3) e il nome diventa «… · Scarico».
- Dalla scheda di un esercizio, **Aggiungi a una scheda** lo mette in coda al giorno scelto con obiettivi predefiniti (3 × 8–12).

### 10. Pesarsi (2 tocchi nel caso tipico)
- Progressi → **Registra peso**: il foglio si apre su "Oggi" già compilato con l'ultima pesata (di solito cambia solo il decimale) → cifre o ±0,1 → **Salva peso**.
- I chip degli ultimi 7 giorni recuperano una pesata dimenticata; un pallino segna i giorni già registrati (salvando si sostituisce, con avviso). Un solo valore al giorno.
- L'errore "tra 20 e 400 kg" compare solo premendo Salva: mentre si scrive "8" per arrivare a "80" non c'è nessun allarme.
- La **media mobile a 7 giorni** usa i giorni di calendario (non le ultime 7 pesate) e parte già "calda" con la settimana precedente al periodo mostrato: è il dato da seguire, le oscillazioni di acqua e sale restano nei punti grigi.

### 11. Leggere i progressi di un esercizio
- Panoramica → riga dell'esercizio o del record, oppure scheda esercizio → icona **Progressi** (esercizi a ripetizioni e tapis roulant in salita, vedi flusso 19).
- Chip della grandezza (1RM stimato con Epley fino a 12 ripetizioni, carico massimo, volume, ripetizioni) e del periodo. A corpo libero contano ripetizioni e zavorra; negli assistiti solo le ripetizioni (il peso è un aiuto, quindi non entra in record né volume).
- Record di sempre: la data è la **prima volta** che il valore è stato raggiunto; tocco = l'allenamento in cui è successo.

### 12. Correggere lo storico
Record, volumi e grafici non sono salvati a parte: si calcolano dalle serie nel database, quindi ogni correzione li aggiorna da sola.
- **Allenamento sbagliato**: Storico → cestino sulla riga, oppure allenamento → menu ⋮ → **Elimina allenamento** → conferma.
- **Serie sbagliata** (es. 1000 kg al posto di 100): allenamento → tocco sulla serie → **Correggi serie** (stessi campi del logger, nell'unità scelta) oppure **Elimina serie** con conferma. Eliminata l'ultima serie di un esercizio, l'esercizio esce dall'allenamento; rimasto vuoto, l'allenamento esce dallo storico.
- **Record anomalo**: Progressi dell'esercizio → cestino sul record → conferma. Per carico massimo e 1RM si elimina la serie che lo ha stabilito, per il volume tutte le serie dell'esercizio in quell'allenamento; compare subito il record successivo.
- **Azzerare un solo esercizio**: Progressi dell'esercizio → menu ⋮ → **Azzera storico esercizio**, oppure scheda esercizio → **Gestione dati**. Spariscono le sue serie da tutti gli allenamenti conclusi; schede, settaggi, altri esercizi e allenamento in corso restano.
- Pesata sbagliata: tocco sulla riga per correggerla, cestino per eliminarla (**Annulla** nella barra in basso).

### 13. Dieta: diario del giorno a grammi
- **Dieta** è il diario del giorno (frecce per i giorni precedenti/successivi, **Oggi** per tornare): totali di calorie, proteine, carboidrati e grassi, poi i cinque pasti **Colazione, Spuntino, Pranzo, Merenda, Cena** con le calorie di ciascuno. Con un piano attivo i totali si confrontano con i suoi obiettivi e i pasti del piano compaiono nel pasto giusto, da spuntare come prima (contano nei totali); senza piano **Imposta** ne crea uno.
- **Aggiungi a Pranzo** apre il **catalogo dei piatti**: 298 piatti italiani con i valori per 100 g (`assets/food/italian_dishes_catalog.json`: colazione e dolci fit, primi e cereali, carne, pesce, vegetariani/legumi/uova, contorni, frutta/snack/bevande), ricerca istantanea senza accenti né maiuscole ("ragu" trova "Pasta al ragù"), sinonimi ("nutella"), filtri per categoria e **I miei**. In alto il totale del pasto e del giorno, aggiornato a ogni aggiunta: si possono aggiungere più piatti di fila.
- **Grammi**: tocco sul piatto → pannello con la porzione tipica, tastierino dell'app, −10 / +10 / +50 g e porzioni 50, 100, 150, 200 g; calorie e macro si ricalcolano mentre si scrive (Calorie = kcal per 100 g × grammi / 100). I chip dei pasti spostano il piatto in un altro pasto; **Aggiungi** lo mette nel diario.
- **Correggere**: tocco sul piatto nel diario → stesso pannello con **Salva** ed **Elimina dal diario**; il cestino sulla riga elimina con un tocco (**Annulla** nella barra in basso). Le calorie non sono salvate: si calcolano dai grammi, quindi cambiano subito.
- **Piatti e ricette personali** (pulsante **+** o in fondo ai risultati): un piatto con i valori per 100 g (senza calorie si calcolano dai macro, 4/4/9) oppure una **ricetta** fatta di ingredienti del catalogo con i loro grammi e, facoltativo, il peso del piatto finito; i valori per 100 g si calcolano da soli. Si modificano o eliminano dall'icona a matita in **I miei**; il diario già scritto non cambia.
- **Open Food Facts** (con rete, solo su richiesta): se il piatto non c'è, **Cerca «…» su Open Food Facts** interroga il database aperto dei prodotti confezionati (si invia solo il testo cercato). Il prodotto scelto viene salvato e resta disponibile offline in **I miei**; i dati sono dei volontari (licenza ODbL) e conviene confrontarli con l'etichetta. Il servizio limita le ricerche (10 al minuto) e a volte risponde "occupato": l'app riprova due volte con attese crescenti, poi mostra "Ricerca online non riuscita".
- Diario, piatti, ricette e prodotti salvati entrano nel backup; il catalogo resta nell'app.

| Diario | Grammi e calcolo live | Open Food Facts | Ricetta |
|---|---|---|---|
| ![Diario del giorno](screenshots/47-dieta-diario.png) | ![Pannello grammi](screenshots/48-dieta-grammi.png) | ![Prodotti online](screenshots/49-dieta-online.png) | ![Ricetta dagli ingredienti](screenshots/50-dieta-ricetta.png) |

### 14. Dieta: costruire il piano
- Dieta → **Imposta** (senza piano) o l'elenco dei piani → nome già proposto → si apre l'editor con colazione, spuntino, pranzo, merenda e cena già pronti (orari 7:30–20:00). Il primo piano diventa attivo.
- **Obiettivi giornalieri**: tocco sulla card → calorie e grammi di proteine, carboidrati e grassi; sotto, il totale dei pasti del piano per vedere subito cosa manca.
- Pasto → **Aggiungi alimento**: nome, quantità con unità (chip g, ml, pz, cucchiaio), calorie e macro. Con i soli macro le calorie si calcolano (4/4/9 kcal per grammo) e "Usa" le copia nel campo. Numeri con virgola o punto.
- Menu ⋮ del pasto: modifica (tipo, nome, orario "7:30", "730" o "13"), sposta su/giù, elimina con conferma. **Aggiungi pasto** per pre/post workout.
- Menu ⋮ del piano: imposta come attivo, duplica (es. variante per i giorni di riposo), elimina (se era attivo si attiva il più recente rimasto). Le icone in Dieta aprono l'editor del piano attivo e l'elenco dei piani.

### 15. Impostazioni e backup
- Oggi → **ingranaggio** in alto a destra.
- **Unità di peso**: chilogrammi o libbre. Il database resta in kg: logger, grafici, record, riepilogo e peso corporeo convertono solo la presentazione (in libbre il tastierino sale di 5 lb, il peso corporeo di 0,2 lb).
- **Sforzo percepito**: RPE o RIR; la colonna si attiva dal menu ⋮ dell'esercizio nel logger.
- **Esporta backup**: il selettore di sistema propone `sport-backup-AAAA-MM-GG.json` (schede, allenamenti, regolazioni, peso, dieta, esercizi personalizzati, preferiti, impostazioni; non i video). Nessun permesso di archiviazione.
- **Ripristina da backup**: si sceglie il file, l'app mostra data e contenuto e chiede conferma prima di sostituire i dati. Il ripristino è una transazione unica: un file incoerente non modifica nulla.

### 16. Aggiornamento in-app (solo build distribuite da GitHub)
- All'avvio, con la rete disponibile, l'app controlla in silenzio l'ultima release su GitHub. Offline o in caso di errore non compare nulla e l'app funziona come sempre.
- Versione nuova → dialogo **Aggiornamento disponibile** con versione, dimensione e note di rilascio. **Più tardi** chiude senza scaricare (non viene riproposto prima di 12 ore); **Aggiorna ora** scarica l'APK con barra di avanzamento e **Annulla**.
- A download finito si apre l'installer di Android, che chiede conferma (la prima volta anche l'autorizzazione "Installa app sconosciute"). Chiudendolo resta **Installa / Più tardi**.
- Impostazioni → **Controlla aggiornamenti** ripete il controllo a mano e dice sempre l'esito ("Hai già la versione più recente", "Nessuna connessione"…).
- Dettagli, pubblicazione delle release e limiti: `docs/aggiornamenti.md`.

| Proposta | Download | Conferma di Android |
|---|---|---|
| ![Aggiornamento disponibile](screenshots/27-aggiornamento-disponibile.png) | ![Download in corso](screenshots/28-aggiornamento-download.png) | ![Installer di sistema](screenshots/29-aggiornamento-installer.png) |

### 17. Allenamento avviato per errore
- **Termina** senza nessuna serie completata con dati validi → "Nessuna serie da salvare": la sessione viene scartata e non entra nello storico né nelle statistiche (vale anche dopo mezz'ora: conta il contenuto, non il tempo).
- **Termina** entro un minuto con almeno una serie valida → "Vuoi davvero salvare questa sessione o annullarla?": **Salva**, **Scarta** o **Continua**.
- Alla chiusura vengono eliminate anche le serie spuntate senza dati (né ripetizioni, né durata, né distanza) e gli esercizi rimasti vuoti.
- La regola è applicata dal database (`WorkoutSessionDao.finishSession`), non solo dall'interfaccia; le sessioni annullate vengono rimosse del tutto all'avvio dell'allenamento successivo.

| Settaggi nella scheda esercizio | Editor "a freddo" | Sessione di meno di un minuto | Correzione di una serie |
|---|---|---|---|
| ![Settaggi salvati](screenshots/30-settaggi-a-freddo.png) | ![Editor dei settaggi](screenshots/31-settaggi-editor.png) | ![Salvare o scartare](screenshots/32-sessione-breve.png) | ![Correggi serie](screenshots/33-correggi-serie.png) |

### 18. Calorie bruciate
- **Profilo corporeo**: Progressi → card del peso → **Storico del peso** (prima di ogni pesata il pulsante si chiama **Profilo corporeo**) → card **Profilo corporeo** → **Aggiungi**/**Modifica** → altezza in cm, anno di nascita, sesso biologico. Tutti facoltativi: se un dato manca si usa un valore medio (30 anni, media fra uomo e donna; senza altezza la convenzione standard del MET).
- **Fine allenamento**: il riepilogo mostra le **kcal** accanto a durata, serie e volume. Senza nessuna pesata compare "Registra il tuo peso per stimare le calorie bruciate"; senza altezza un invito a completare il profilo.
- **Storico**: kcal su ogni riga, totale della settimana in corso e degli ultimi 30 giorni sopra l'elenco; nel dettaglio dell'allenamento anche il peso corporeo usato.
- **Coerenza**: le calorie sono salvate in ogni allenamento ma si ricalcolano da sole quando si corregge o si elimina una serie, si azzera un esercizio, si registra/modifica/elimina una pesata o si cambia il profilo; un allenamento eliminato esce dai totali. Il peso usato è quello del giorno dell'allenamento (l'ultima pesata fino a quel giorno, altrimenti la prima disponibile).
- **Come si stima** (`core/data/calories/WorkoutCalorieCalculator.kt`): metabolismo a riposo con Mifflin-St Jeor (peso, altezza, età, sesso); MET diversi per serie e recupero secondo il tipo di esercizio (multiarticolari delle gambe > multiarticolari del busto > isolamento > piccoli distretti; esplosivi, isometrie e cardio a parte); lavoro meccanico = massa × g × corsa del movimento (scalata sull'altezza) × ripetizioni, con rendimento muscolare del 20% e +30% per la fase eccentrica; a corpo libero con 0 kg la massa è una quota del peso corporeo (trazioni e dip 100%, piegamenti 65%, squat 50%); RPE/RIR ±5% per punto; cardio dalla velocità (ACSM per camminata e corsa, Compendium per la bici, potenza al vogatore). Un allenamento dimenticato aperto conta solo per una durata plausibile. L'EPOC (consumo nelle ore successive) non è incluso.
- **Aggiornamento dalla 1.0.2**: la migrazione Room v3 → v4 aggiunge la colonna delle calorie e la tabella del profilo senza toccare i dati; al primo avvio gli allenamenti già salvati ricevono la stima con le pesate esistenti.

| Profilo corporeo | Riepilogo con le kcal | Storico e totali | Dettaglio dopo una correzione |
|---|---|---|---|
| ![Profilo corporeo](screenshots/34-profilo-corporeo.png) | ![Riepilogo](screenshots/35-riepilogo-kcal.png) | ![Storico](screenshots/36-storico-kcal.png) | ![Dettaglio](screenshots/37-dettaglio-kcal.png) |

### 19. Tapis roulant: pendenza, velocità, tempo e calorie
- Esercizi con registrazione **Tapis roulant: pendenza, velocità e tempo**: "Camminata in salita sul tapis roulant" del catalogo e gli esercizi personalizzati che scelgono questo tipo. La corsa sul tapis del catalogo resta a distanza e tempo.
- **Regolazione dedicata** (scheda esercizio → **I miei settaggi**, oppure **Regolazione macchinario** nel logger): due voci fisse, **Pendenza (%)** con −0,5/+0,5 e **Velocità (km/h)** con −0,5, −0,1, +0,1, +0,5; si può anche scrivere il valore (virgola o punto, 0–40% e 0–30 km/h). Le altre voci e le note restano libere.
- **Ultima combinazione**: spuntata una serie, la sua pendenza e la sua velocità diventano la regolazione salvata e valgono anche per le serie ancora da fare. Il prossimo allenamento parte da lì (la regolazione scritta "a freddo" nella scheda esercizio conta allo stesso modo); il tempo parte da quello della volta scorsa.
- **Logger**: colonne **Pend. %**, **km/h** e **Tempo** (senza la colonna "Prec.", per lasciare spazio al tempo). Pendenza ±0,5, velocità ±0,5 dal tastierino (i decimi si scrivono); tempo come sul display di un microonde (2-5-3-0 = 25:30), ±1:00. **Avvia cronometro** sul campo Tempo conta dal tempo già scritto, resta acceso chiudendo il tastierino e si ferma con **Ferma cronometro**, spuntando la serie o terminando l'allenamento.
- **Statistiche solo calorie** (scheda esercizio → icona Progressi): calorie bruciate nel periodo, ultima sessione, media a sessione, numero di sessioni; grafico con le calorie di ogni sessione (barre, asse sinistro) e il totale cumulativo (linea, asse destro); elenco delle sessioni con il cumulativo e l'icona della sessione più intensa. Contano solo il tempo sul tapis e il peso corporeo del giorno; senza pesate la pagina chiede di registrarne una.
- **Calorie** (`WorkoutCalorieCalculator`): equazioni ACSM con la velocità registrata (km/h × 1000 / 60 = m/min), la pendenza G = %/100 e la durata esatta: camminata (fino a 100 m/min) VO₂ = 3,5 + 0,1·v + 1,8·v·G, corsa (da 134 m/min) 3,5 + 0,2·v + 0,9·v·G, in mezzo un raccordo lineare; kcal nette = (VO₂/3,5 − 1) × 0,0175 × kg × minuti, più il metabolismo a riposo. Le serie senza velocità usano la distanza se c'era, altrimenti 5 km/h.
- **Correzione dallo storico**: campi Inclinazione (%), Velocità (km/h) e Tempo (mm:ss; valgono anche "25,30", "25.30" o i soli minuti).
- **Aggiornamento dalla 1.0.4**: la migrazione Room v5 → v6 aggiunge la colonna della velocità; le serie già registrate restano intatte.

| Regolazione dedicata | Logger | Statistiche solo calorie |
|---|---|---|
| ![Pendenza e velocità](screenshots/44-tapis-regolazione.png) | ![Pendenza, km/h e tempo](screenshots/45-tapis-logger.png) | ![Calorie per sessione e cumulativo](screenshots/46-tapis-calorie.png) |

### 20. Catalogo in italiano, anteprime e video
- Tutti i 733 esercizi hanno nome, alias, esecuzione passo passo, punti chiave ed errori comuni in italiano (70 curati a mano, 663 tradotti dal dataset); la ricerca trova sia il nome italiano sia quello inglese.
- **Anteprime** nelle liste e nel selettore: immagini con cache su disco (Coil); senza rete e senza copia in cache compare la mappa anatomica con il muscolo principale evidenziato.
- **Scheda esercizio**: video loop silenzioso (Media3/ExoPlayer) da file locale o cache, GIF/WebP animate con Coil, altrimenti i fotogrammi in dissolvenza. Offline senza copia del video: fotogrammi o mappa anatomica con il badge discreto **Video disponibile online**; fotogrammi mai scaricati: mappa anatomica. Mai schermate d'errore o riquadri vuoti.
- Le build release senza `-Psport.mediaBaseUrl.release` non scaricano media remoti: mostrano sempre il ripiego anatomico.

| Anteprime online | Offline, mai scaricate | Scheda offline |
|---|---|---|
| ![Anteprime](screenshots/41-anteprime-online.png) | ![Mappe anatomiche](screenshots/42-anteprime-offline.png) | ![Mappa al posto dei fotogrammi](screenshots/43-scheda-offline.png) |

## Stati di ogni schermata

Ogni schermata gestisce esplicitamente **caricamento**, **vuoto** (con spiegazione e, quando possibile, l'azione che lo risolve) e **contenuto**. Non esistono schermate bianche né errori di rete bloccanti: senza connessione mancano al massimo le immagini non ancora scaricate, sostituite da un'icona neutra o dalla mappa muscolare.

## Schermate (emulatore Android 17, Fase 3)

| Primo avvio | Allenamento di oggi | Libreria | Scheda esercizio |
|---|---|---|---|
| ![Oggi, primo avvio](screenshots/01-oggi-primo-avvio.png) | ![Oggi, Push](screenshots/02-oggi-allenamento.png) | ![Ricerca esercizi](screenshots/03-esercizi-ricerca.png) | ![Dettaglio esercizio](screenshots/04-scheda-esercizio.png) |

## Schermate del logger (emulatore Android 17, Fase 4)

| Tastierino | Recupero e permesso sveglie | Sostituzione al volo | Riepilogo |
|---|---|---|---|
| ![Tastierino sulle ripetizioni](screenshots/05-logger-tastierino.png) | ![Pannello del recupero](screenshots/06-logger-recupero.png) | ![Alternative allo stacco rumeno](screenshots/07-sostituzione.png) | ![Riepilogo allenamento](screenshots/08-riepilogo.png) |

## Schermate della libreria (emulatore Android 17, Fase 5)

| Pannello filtri | Editor esercizio | Video personale | Alternative |
|---|---|---|---|
| ![Filtri: forza, femorali](screenshots/09-filtri.png) | ![Muscoli: target con la stella](screenshots/10-editor-esercizio.png) | ![Video verticale in loop a 0,5×](screenshots/11-video-personale.png) | ![Alternative allo stacco rumeno](screenshots/12-alternative.png) |

## Schermate del builder (emulatore Android 17, Fase 6)

| Builder e settimana | Obiettivi | Variante di scarico | Superserie nel logger |
|---|---|---|---|
| ![Builder con superserie e calendario](screenshots/13-builder.png) | ![Pannello obiettivi](screenshots/14-obiettivi.png) | ![Duplica come settimana di scarico](screenshots/15-variante-scarico.png) | ![Recupero a fine giro della superserie](screenshots/16-logger-superserie.png) |

## Schermate dei progressi (emulatore Android 17, Fase 7)

| Registra peso | Peso corporeo | Panoramica | Esercizio | Dettaglio allenamento |
|---|---|---|---|---|
| ![Foglio pesata pre-compilato](screenshots/17-registra-peso.png) | ![Pesate e media a 7 giorni](screenshots/18-peso-corporeo.png) | ![Esercizi e record](screenshots/19-progressi-panoramica.png) | ![1RM stimato dello squat](screenshots/20-progressi-esercizio.png) | ![Serie di un allenamento](screenshots/21-dettaglio-allenamento.png) |

## Schermate di dieta, impostazioni e backup (emulatore Android 17, build release con R8, Fase 8)

| Impostazioni | Editor del piano | Alimento | Logger in libbre con RIR | Ripristino |
|---|---|---|---|---|
| ![Unità, sforzo, backup](screenshots/22-impostazioni.png) | ![Obiettivi e totale del piano](screenshots/23-editor-dieta.png) | ![Calorie dai macro](screenshots/24-alimento.png) | ![Tastierino a passi di 5 lb](screenshots/25-logger-libbre-rir.png) | ![Conferma con il contenuto del backup](screenshots/26-ripristino-backup.png) |

Nota per i test JVM: in Robolectric un campo di testo dentro una finestra di dialogo impedisce a Compose di arrivare a riposo. I dialoghi con campi (`FormDialog`) si possono mostrare in linea con `LocalInlineDialogs`, solo nei test.
