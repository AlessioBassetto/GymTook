# Scheda Play Store

Testi e risposte pronte per la Play Console. Le parti tra «» vanno completate con i tuoi dati.

## Scheda dell'app

**Nome** (max 30): Sport – Diario di allenamento

**Descrizione breve** (max 80):
Registra serie e carichi in un tocco, segui i progressi e la dieta. Offline.

**Descrizione completa:**

> Il diario di allenamento pensato per la palestra: pulsanti grandi, tastierino numerico dedicato e
> zero distrazioni tra una serie e l'altra.
>
> **Allenamento**
> • Serie, carichi, ripetizioni e RPE o RIR con un tocco: i valori della volta scorsa sono già pronti
> • Timer di recupero automatico, anche a schermo spento
> • Regolazioni dei macchinari salvate per ogni esercizio (sedile, schienale, fermi)
> • Macchina occupata? Sostituzione al volo con le alternative più simili
>
> **Schede**
> • Builder con calendario settimanale, superserie e trascinamento
> • Modelli pronti (Push Pull Legs, Upper/Lower, Full body), varianti e settimana di scarico
>
> **Esercizi**
> • Oltre 700 esercizi con muscoli coinvolti, esecuzione, punti chiave ed errori comuni
> • Esercizi personalizzati con il tuo video in loop
>
> **Progressi**
> • Grafici di 1RM stimato, carico massimo, volume e ripetizioni per esercizio
> • Record personali e storico degli allenamenti
> • Peso corporeo con media mobile a 7 giorni
>
> **Dieta**
> • Piano alimentare con pasti, alimenti e macro; spunta dei pasti della giornata
>
> **I tuoi dati restano tuoi**
> Nessun account, nessuna pubblicità. Tutto è salvato sul telefono e funziona senza connessione;
> il backup è un file che scegli tu dove salvare. Chilogrammi o libbre.

**Categoria:** Salute e fitness · **Tag:** allenamento, palestra, bodybuilding

**Elementi grafici:**
- Icona 512 × 512 PNG (dall'icona adattiva in `app/src/main/res/mipmap-anydpi-v26`)
- Grafica in primo piano 1024 × 500
- Screenshot telefono (min. 2, consigliati 6–8, 9:16): usa `docs/screenshots/` — oggi (02), logger con
  tastierino (05), recupero (06), libreria (03), builder (13), progressi esercizio (20), peso
  corporeo (18), editor della dieta (23), impostazioni (22).

## Sicurezza dei dati

| Domanda | Risposta |
|---|---|
| L'app raccoglie o condivide dati utente? | **No** |
| Dati crittografati in transito | Non applicabile (nessun dato utente trasmesso) |
| Eliminazione dei dati | Disinstallando l'app o cancellandone i dati da Impostazioni di Android |

Motivazione: allenamenti, peso, dieta, video e impostazioni restano nell'archivio privato dell'app.
La connessione serve solo a scaricare immagini e video degli esercizi dal server dei media (richieste
anonime, senza dati dell'utente). Il backup è un file creato dall'utente con il selettore di sistema.

## Classificazione dei contenuti

Questionario IARC: nessuna violenza, sesso, linguaggio, droghe, gioco d'azzardo, interazione tra
utenti, condivisione della posizione o acquisti → **PEGI 3 / Tutti**.

## Pubblico e permessi

- Pubblico di destinazione: 16+ (allenamento con i pesi), non rivolta ai bambini.
- `SCHEDULE_EXACT_ALARM` (dichiarazione richiesta): *"Il timer di recupero avvisa allo scadere
  esatto del tempo impostato tra le serie, anche a schermo spento; il permesso è facoltativo e
  richiesto dall'app solo durante un recupero."*
- `POST_NOTIFICATIONS`: notifica del recupero in corso e del recupero terminato.
- **Niente auto-aggiornamento nelle build per lo Store.** Google Play vieta alle app di aggiornarsi
  fuori dallo Store e limita il permesso `REQUEST_INSTALL_PACKAGES`. Il bundle da caricare va
  compilato con `-Psport.update.repository=` (proprietà vuota, vedi `docs/aggiornamenti.md`): in quel
  caso l'aggiornamento da GitHub e il relativo permesso non sono nell'app.

## Informativa sulla privacy

Il Play Store richiede un URL pubblico. Testo pronto da pubblicare (es. GitHub Pages o Google Sites):

> **Informativa sulla privacy – Sport**
>
> Sport non raccoglie, non trasmette e non vende dati personali. Allenamenti, misure, piani
> alimentari, video ed impostazioni sono salvati solo sul dispositivo. L'app non richiede account e
> non contiene pubblicità né strumenti di analisi.
>
> L'app si collega a Internet solo per scaricare immagini e video dimostrativi degli esercizi da
> «indirizzo del server dei media»; le richieste non contengono dati dell'utente.
>
> Il backup è un file che l'utente crea e conserva dove preferisce; l'app non lo invia altrove.
> Il backup automatico di Android, se attivo sul dispositivo, può includere i dati dell'app nel
> proprio account Google secondo le impostazioni dell'utente (i video personali ne sono esclusi).
>
> Per domande: «indirizzo email di contatto».
