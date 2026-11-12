# Aggiornamento in-app dalle GitHub Releases

L'app può aggiornarsi da sola scaricando l'APK allegato all'ultima release del repository GitHub.
Resta offline-first: senza rete, o se GitHub non risponde, si avvia come sempre e non mostra nulla.

## Come funziona

1. **All'avvio**, se c'è rete, l'app chiede a GitHub l'ultima release pubblicata
   (`GET https://api.github.com/repos/PROPRIETARIO/REPOSITORY/releases/latest`, timeout di pochi
   secondi) e confronta il tag con la propria versione. Offline, timeout, errori e limite di
   richieste non producono messaggi: si riprova al prossimo avvio.
2. **Se c'è una versione più recente** compare "Aggiornamento disponibile" con versione, dimensione
   e note di rilascio. **Più tardi** chiude il dialogo senza scaricare nulla; l'app non lo ripropone
   prima di 12 ore (e nel frattempo non interroga GitHub).
3. **Aggiorna ora** scarica l'APK nella cache privata dell'app con barra di avanzamento e
   **Annulla**. Il file viene accettato solo se è completo, ha lo stesso identificativo dell'app e
   un `versionCode` maggiore; altrimenti viene cancellato.
4. A download finito si apre l'**installer di Android**, che chiede conferma. La prima volta
   Android chiede anche di autorizzare l'app ("Installa app sconosciute" → *Consenti da questa
   origine*) e poi prosegue da solo. Su alcuni telefoni Play Protect propone una scansione: basta
   scegliere "Installa senza scansione" (o far eseguire la scansione).
5. In **Impostazioni → Controlla aggiornamenti** il controllo si può ripetere a mano in qualsiasi
   momento, con l'esito sempre visibile.

I dati dell'utente non vengono toccati: l'aggiornamento sostituisce solo l'app.

## Attivarlo (una tantum)

Il repository **pubblico** che ospita le release è indicato in `gradle.properties`:

```properties
sport.update.repository=AlessioBassetto/GymTook
```

Senza questa riga la funzione non esiste nell'app: nessuna chiamata a GitHub, nessun pulsante,
nessun permesso `REQUEST_INSTALL_PACKAGES` nel manifest.

> **Play Store.** Le norme di Google Play vietano alle app di aggiornarsi fuori dallo Store. Il
> bundle destinato al Play Store va compilato con la proprietà vuota
> (`./gradlew :app:bundleRelease -Psport.update.repository=`); gli APK distribuiti da GitHub con
> la riga qui sopra. Sono due canali distinti: non mescolarli sullo stesso telefono.

## Pubblicare una versione

1. **Stessa chiave di firma, sempre.** Android installa un aggiornamento solo se è firmato con la
   stessa chiave della versione installata. Crea la chiave di rilascio una volta con
   `powershell -ExecutionPolicy Bypass -File tools\crea-chiave-firma.ps1` (chiede la password e
   scrive `keystore.properties`; dettagli in `docs/rilascio.md`) e usala per tutte le release.
   Gli APK in `dist/` fino alla 1.0.1 sono firmati con la chiave di debug di questo PC: chi li ha
   installati deve disinstallarli prima di passare a un APK firmato con la chiave di rilascio
   (conviene esportare un backup da Impostazioni).
2. **Aumenta la versione** in `gradle.properties`:
   ```properties
   sport.versionCode=3
   sport.versionName=1.0.2
   ```
   `versionCode` deve crescere a ogni release; `versionName` è quello confrontato con il tag.
3. **Compila l'APK firmato**: `./gradlew :app:assembleRelease` →
   `app/build/outputs/apk/release/app-release.apk`.
   Finché non esiste `keystore.properties` la build release non è firmata: in quel caso usa
   `./gradlew :app:assembleBenchmarkRelease` (`app/build/outputs/apk/benchmarkRelease/`), la stessa
   build ottimizzata firmata con la chiave di debug di questo PC (`.android\debug.keystore` nella
   cartella utente). È la chiave della release 1.0.1: conservane una copia, perché senza quel file
   gli aggiornamenti successivi non si installano sopra quella versione.
4. **Crea la release su GitHub** (Releases → *Draft a new release*):
   - **Tag**: la versione con la `v` davanti, es. `v1.0.2` (va bene anche `1.0.2`);
   - **Descrizione**: le novità, in Markdown: è il testo che l'utente legge nel dialogo;
   - **Allegato**: il file `.apk` (uno solo; il nome è libero purché finisca in `.apk`);
   - lascia deselezionato *Set as a pre-release* e pubblica (non bozza): l'app guarda solo la
     release marcata **Latest**.

   Da terminale, con la GitHub CLI:
   ```bash
   gh release create v1.0.2 --repo AlessioBassetto/GymTook app/build/outputs/apk/release/app-release.apk --title "Sport 1.0.2" --notes "- Novità uno"
   ```

L'app riconosce la release se: il tag è una versione più alta di quella installata, c'è un
allegato `.apk`, la release non è bozza né pre-release. Le release senza APK vengono ignorate.

## Limiti da conoscere

- Il repository deve essere **pubblico**: l'app non contiene credenziali.
- GitHub limita le richieste anonime a 60 l'ora per indirizzo IP. Con un controllo ogni 12 ore per
  dispositivo non è un problema; se il limite scatta, l'app semplicemente riprova più tardi.
- Il controllo invia a GitHub solo una richiesta anonima (nessun dato dell'utente).
- Finché il repository non ha nessuna release pubblicata GitHub risponde 404: all'avvio non compare
  nulla, mentre il controllo manuale dice "Controllo non riuscito, riprova più tardi".

## Provare senza pubblicare

Le build di debug accettano un server locale al posto di GitHub (solo `localhost` / `10.0.2.2`):

```bash
./gradlew :app:assembleDebug -Psport.update.repository=test/sport -Psport.update.apiBaseUrl=http://localhost:8787
```

Servi su quella porta `/repos/test/sport/releases/latest` (JSON con `tag_name`, `body`, `assets`)
e l'APK di una versione successiva, poi collega l'emulatore con `adb reverse tcp:8787 tcp:8787`.
È il percorso con cui la funzione è stata verificata: avvio offline, proposta, "Più tardi",
download, installer di sistema, app aggiornata.

## Dove sta nel codice

| Parte | File |
|---|---|
| Chiamata a GitHub, confronto versioni, download, intent di installazione | `core/update/GitHubUpdateManager.kt` |
| Versioni e note di rilascio in testo semplice | `core/update/AppVersion.kt` |
| Regia: controllo all'avvio, consenso, intervallo di 12 ore | `core/update/UpdateController.kt` |
| Dialoghi Compose | `feature/update/UpdateDialogs.kt` |
| Permesso di installazione (solo se attivo) | `app/src/selfupdate/AndroidManifest.xml` |
| Cartella condivisa con l'installer | `res/xml/file_paths.xml` (`cache/updates`) |
