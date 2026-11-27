# Echo Wear

Port **standalone** di [Echo](https://github.com/brahmkshatriya/echo) per **Wear OS**, pensato per
il **Samsung Galaxy Watch 6 Classic** (Wear OS 6 / One UI Watch 8) ma compatibile con qualsiasi
orologio Wear OS 3+ (API 30+).

L'app non richiede un'app companion sul telefono: estensioni, streaming e riproduzione girano
interamente sull'orologio.

> Questo progetto riusa il codice del player, del sistema di estensioni e il modulo `common` di
> Echo. L'interfaccia è stata riscritta da zero con **Wear Compose Material**. Licenza: GPL-3.0.

## Funzionalità

- **Estensioni Echo** (stesso formato `.eapk`/APK del telefono): installazione da link o codice di
  una lista di estensioni, aggiornamento da GitHub Releases, disinstallazione, abilitazione e
  selezione dell'estensione musicale attiva.
- **Home, Ricerca e Libreria** dell'estensione selezionata, con tab, scorrimento infinito e
  navigazione nelle sezioni ("Mostra tutto", categorie).
- **Pagina dettaglio** di album, artisti, playlist, radio e brani: riproduci, casuale, radio,
  mi piace / salva / segui, lista brani, sezioni correlate.
- **Player** con copertina, anello di avanzamento, play/pausa, precedente/successivo, mi piace,
  casuale, ripetizione, coda, seek e **volume tramite corona rotante**.
- **Coda** con brano corrente evidenziato, salto a un brano, rimozione e radio automatica.
- **Account**: login tramite moduli (`LoginClient.CustomInput`) con tastiera/voce del sistema;
  gestione utenti multipli. Il login via WebView non è disponibile su Wear OS.
- **Impostazioni** dell'app (qualità stream, reti non a consumo, radio automatica, salta
  silenzi, audio offload, cache) e **impostazioni per estensione**.
- **Estensione Offline** integrata per i file audio presenti sull'orologio.
- Controlli multimediali di sistema (notifica / media controls) tramite Media3 `MediaLibraryService`.
- Interfaccia in **inglese** e **italiano**.

## Struttura del repository

```
├── app/      # app Wear OS (Compose, Media3, Koin, Coil)
├── common/   # API delle estensioni Echo (copiata invariata dal progetto originale)
└── gradle/   # wrapper e version catalog
```

Pacchetto dell'app: `dev.brahmkshatriya.echo.wear` (`.nightly` / `.debug` nelle build di sviluppo).

## Compilazione

Requisiti: JDK 17, Android SDK (compileSdk 36).

```bash
./gradlew :app:assembleDebug     # APK di debug
./gradlew :app:assembleNightly   # APK "nightly" (firmato con la chiave di debug)
```

Gli APK vengono generati in `app/build/outputs/apk/<buildType>/`.

### GitHub Actions

Il workflow [`wear-build.yml`](.github/workflows/wear-build.yml) compila gli APK a ogni push e:

- li pubblica come artifact **`echo-wear-apk`** (`echo-wear-debug.apk`, `echo-wear-nightly.apk`,
  `SHA256SUMS.txt`) nella pagina del run di GitHub Actions;
- aggiorna la pre-release **`nightly`** nella sezione *Releases* del repository, da cui gli APK si
  possono scaricare direttamente (anche senza login GitHub). Il tag `nightly` punta sempre
  all'ultima build riuscita.

Se la compilazione fallisce, il workflow riassume gli errori nel *job summary* e in un commento sul
commit, e carica il log completo come artifact `build-log`.

## Installazione sul Galaxy Watch 6 Classic

1. Sull'orologio: *Impostazioni → Info orologio → Software* e tocca più volte **Versione software**
   per attivare le opzioni sviluppatore; poi abilita **Debug ADB** e **Debug wireless**.
2. Collega orologio e computer alla stessa rete Wi‑Fi e associa il dispositivo:
   ```bash
   adb pair <ip-orologio>:<porta-pairing>   # codice mostrato dall'orologio
   adb connect <ip-orologio>:5555
   ```
3. Installa l'APK scaricato dalla release `nightly` (o dagli artifact di GitHub Actions):
   ```bash
   adb install -r echo-wear-nightly.apk
   ```
4. Avvia **Echo** dalla lista delle app, apri *Estensioni → Aggiungi estensione* e inserisci il
   link (o il codice breve) di una lista di estensioni.

## Note e limitazioni

- Le estensioni che richiedono il **login via WebView** non possono autenticarsi sull'orologio.
- Le estensioni vengono scaricate e installate come **file** nella cartella privata dell'app
  (nessuna installazione di pacchetti di sistema).
- Nessun download offline dei brani: viene usata solo la cache di riproduzione.
- L'inserimento di testo (ricerca, link, login) usa il **Remote Input** di Wear OS
  (tastiera, dettatura vocale o scrittura a mano).

## Crediti

- [Echo](https://github.com/brahmkshatriya/echo) di **brahmkshatriya** — player, API delle
  estensioni e codice di riproduzione.
- Icone: Material Symbols.
