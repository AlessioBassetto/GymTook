# Rilascio

Checklist per pubblicare una versione sul Play Store. Tutti i comandi vanno lanciati dalla radice del
progetto con `JAVA_HOME` sulla JBR di Android Studio.

## Una tantum

### 1. Identificativo dell'app

`applicationId` predefinito: **`it.allenamento.sport`** (in `app/build.gradle.kts`). Per usarne uno
proprio (consigliato: il dominio rovesciato di un sito che controlli) aggiungi a `gradle.properties`:

```properties
sport.applicationId=it.tuodominio.sport
```

Dopo la prima pubblicazione l'identificativo **non si può più cambiare**. Il package del codice
(`com.example.sport`, il `namespace`) non è visibile agli utenti e non serve rinominarlo.
La build di debug aggiunge `.debug`: debug e release convivono sullo stesso telefono.

### 2. Chiave di caricamento

```powershell
powershell -ExecutionPolicy Bypass -File tools\crea-chiave-firma.ps1
```

Lo script chiede una password, crea la chiave fuori dal progetto (`sport-chiave-firma.jks` nella
cartella utente) e scrive `keystore.properties` (già escluso da git). In alternativa, a mano:
`keytool -genkeypair -v -keystore upload-key.jks -alias upload -keyalg RSA -keysize 4096 -validity 9125`,
poi copia `keystore.properties.example` in `keystore.properties` e compila percorso e password.
Conserva il file `.jks` e la password fuori dal progetto, con un backup: senza la chiave di
caricamento serve una procedura di ripristino con Google, e gli APK distribuiti da GitHub non si
possono più aggiornare.
Nella Play Console attiva **Play App Signing** (la chiave di firma finale la custodisce Google).

### 3. Media degli esercizi

In sviluppo immagini e video arrivano dal mirror pubblico di free-exercise-db. Per la produzione
caricali sulla tua CDN (vedi `tools/catalog-importer/README.md`) e indica l'indirizzo:

```properties
sport.mediaBaseUrl.release=https://cdn.tuodominio.it/sport/
```

Senza questa proprietà la release funziona comunque, offline, con la mappa muscolare al posto delle
immagini remote.

## A ogni versione

1. **Numero di versione** in `gradle.properties` (il `versionCode` deve crescere sempre):
   ```properties
   sport.versionCode=2
   sport.versionName=1.1.0
   ```
2. **Test**: `./gradlew :app:testDebugUnitTest :core:model:test :tools:catalog-importer:test`
3. **Baseline profile**: l'app include `app/src/main/baseline-prof.txt` (tutto il codice dell'app,
   scritto a mano). Per affinarlo con il percorso reale di avvio collega un **telefono reale**
   Android 9+ (o un emulatore *con finestra* API 34–36) e lancia
   `./gradlew :app:generateReleaseBaselineProfile`: il risultato finisce in
   `app/src/release/generated/baselineProfiles/` e va tenuto nel progetto. Sull'emulatore Android 17
   senza finestra la generazione non riesce (la libreria non legge i frame di avvio e non riceve il
   profilo salvato).
4. **Bundle firmato**: `./gradlew :app:bundleRelease -Psport.update.repository=` → `app/build/outputs/bundle/release/app-release.aab`.
5. **Prova della build ottimizzata** (R8 attivo): `./gradlew :app:installBenchmarkRelease` installa la
   variante minificata firmata con la chiave di debug; percorri i flussi principali (allenamento,
   progressi, dieta, backup) prima di caricare il bundle.
6. Play Console → Test interno → carica l'`.aab`, poi promuovi a produzione.

**Distribuzione da GitHub invece che dal Play Store:** l'app può aggiornarsi da sola dalle GitHub
Releases. Configurazione, firma e pubblicazione delle release sono in `docs/aggiornamenti.md`; il
bundle per il Play Store va invece compilato con `-Psport.update.repository=` (proprietà vuota).

## Ottimizzazione

- **R8** (`isMinifyEnabled` e `isShrinkResources` nella release): rimuove codice e risorse inutilizzati e offusca. Room,
  Hilt, kotlinx.serialization, Navigation, Media3 e DataStore portano le loro regole; il progetto non
  ne richiede di proprie.
- **Baseline profile**: il codice dell'app (`baseline-prof.txt`) e quello delle librerie vengono
  compilati in anticipo all'installazione; il generatore `:baselineprofile` percorre avvio, libreria
  esercizi, progressi e dieta per un profilo su misura. `profileinstaller` lo installa anche nelle
  installazioni fuori dallo Store.
