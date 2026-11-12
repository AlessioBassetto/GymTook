# Sport – Diario di allenamento

App Android nativa per la palestra: registra serie e carichi in un tocco, segue i progressi e il
piano alimentare. Funziona senza connessione, senza account e senza pubblicità: i dati restano sul
telefono.

- **Allenamento**: logger con tastierino dedicato, timer di recupero, regolazioni dei macchinari
- **Schede**: builder con calendario settimanale, superserie, modelli pronti
- **Esercizi**: oltre 700 esercizi con muscoli coinvolti ed esecuzione, più quelli personalizzati
- **Progressi**: 1RM stimato, volume, record personali, peso corporeo
- **Dieta**: piano con pasti, alimenti e macro
- **Backup** su file, chilogrammi o libbre, RPE o RIR

## Installazione

Scarica il file `.apk` dall'ultima versione nella pagina
[Releases](https://github.com/AlessioBassetto/GymTook/releases/latest) e aprilo sul telefono
(Android 8.0 o successivo). Android chiede di autorizzare l'installazione da questa origine.

Da quel momento l'app si aggiorna da sola: all'avvio controlla se c'è una nuova release e, solo
dopo la tua conferma, la scarica e apre l'installer di sistema.

## Compilare

Servono Android Studio (con il JDK che include) e l'SDK Android.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :core:model:test :tools:catalog-importer:test
```

Kotlin, Jetpack Compose, Room, Hilt, Navigation, Material 3. Moduli: `app`, `core/model`,
`tools/catalog-importer`, `baselineprofile`.

## Documentazione

- [Aggiornamento in-app e pubblicazione delle release](docs/aggiornamenti.md)
- [Rilascio e firma](docs/rilascio.md)
- [Flussi e schermate](docs/ux-flussi.md)
- [Scheda Play Store](docs/play-store.md)

Il catalogo degli esercizi deriva da [free-exercise-db](https://github.com/yuhonas/free-exercise-db).
