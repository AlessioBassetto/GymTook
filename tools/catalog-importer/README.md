# catalog-importer

Genera il catalogo esercizi dell'app (`app/src/main/assets/catalog/exercises.json`) e il manifest
dei media da pubblicare sulla CDN. È un tool JVM: nulla di questo modulo finisce nell'APK.

## Fonti e licenze

| Fonte | Uso | Licenza | Note |
|---|---|---|---|
| [free-exercise-db](https://github.com/yuhonas/free-exercise-db) (commit fissato + SHA-256) | ~660 esercizi: tassonomia, istruzioni EN, 2 fotogrammi ciascuno | The Unlicense (pubblico dominio), come la fonte a monte wrkout/exercises.json | Uso commerciale libero, nessuna attribuzione obbligatoria |
| `data/curated_it.json` | 70 esercizi scritti in italiano (istruzioni, punti chiave, errori, regolazioni) | Proprietario | Prevalgono sull'import; ereditano le immagini tramite `curated_links.json` |
| `data/names_it.json` | Nomi italiani di tutti gli esercizi importati | Proprietario | Revisionato a mano |
| `data/translations_it.json` | Istruzioni/punti chiave/errori in italiano | Generato con Claude, proprietario | `reviewed: false` finché un esperto non lo rivede |

Fonti valutate e scartate:
- **Scraping** di siti di fitness: violazione di copyright e termini d'uso, escluso.
- **API commerciali con GIF** (es. ExerciseDB): ottima copertura visiva ma licenza a pagamento,
  limiti sul caching offline e dipendenza da un fornitore. Valutabili in futuro solo con licenza scritta.
- **wger**: contenuti CC-BY-SA 4.0. Utilizzabili, ma con attribuzione e share-alike sui dati derivati.
  Lo schema media supporta già `license` e `attribution` per integrarli.

Video loop dedicati: produzione propria (o licenza) per gli esercizi più usati, caricati come
`VIDEO_LOOP` nel catalogo. Fino ad allora l'app anima i due fotogrammi in dissolvenza.

## Tipo di movimento (catalogo v3)

Ogni esercizio ha una `category`: `STRENGTH` (default, omessa nel JSON), `OLYMPIC`, `PLYOMETRIC`,
`CARDIO`. Serve al filtro "Tipo di esercizio" e soprattutto alle alternative della sostituzione al
volo (uno stacco rumeno non va sostituito con una girata). La categoria del dataset non basta:
"olympic weightlifting" contiene accessori lenti (stacco rumeno in deficit, squat overhead) e il
power clean è "strength". Regola applicata da `TaxonomyMapper.categoryOf`:

- `cardio` → CARDIO, `plyometrics` → PLYOMETRIC;
- girate, strappi e slanci riconosciuti dal nome (`clean`, `snatch`, `jerk`, non "clean grip") →
  OLYMPIC, tranne gli stacchi di girata/strappo (tirate lente) → STRENGTH;
- tutto il resto, powerlifting compreso → STRENGTH.

Gli esercizi curati dichiarano `category` solo se non è forza; altrimenti la ereditano dalla voce
collegata del dataset o, per il cardio senza collegamento, da attrezzo e muscoli.

## Comandi

```bash
# 1. Genera catalogo + manifest media (incrementare la versione a ogni rilascio)
./gradlew :tools:catalog-importer:run --args="build --version 3"

# 2. (Opzionale, a pagamento) Traduce in italiano istruzioni, punti chiave ed errori con Claude.
#    Richiede ANTHROPIC_API_KEY. Riprende da dove si era interrotto; poi rieseguire `build`.
./gradlew :tools:catalog-importer:run --args="translate --limit 40"

# 3. Copia tutti i media in una cartella pronta da caricare sulla CDN di produzione
./gradlew :tools:catalog-importer:run --args="mirror-media --out D:/cdn/sport-media"
```

Costo indicativo di `translate` per tutti i ~660 esercizi con `claude-opus-5-5` (lotti da 8):
circa 10–15 USD. Il prompt di sistema è in cache; le richieste hanno il fallback lato server attivo.

## Media in produzione

L'app compone `MEDIA_BASE_URL + path`. In debug punta al mirror GitHub del dataset (commit fissato);
in release va configurata la propria CDN:

```bash
./gradlew :app:assembleRelease -Psport.mediaBaseUrl.release=https://cdn.tuodominio.it/media/
```
