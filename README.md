# FinanceMaster

Súkromná Android aplikácia na účtenky: **otvoriť → odfotiť → uložené**. Iba Phase 1, bez cloudových služieb, účtov alebo synchronizácie. Fotografie a SQLite databáza zostávajú v súkromnom úložisku telefónu. OCR funguje offline vďaka modelu pribalenému do APK.

Podrobný rozsah, dátové pravidlá a checklist: [docs/PHASE-1.md](docs/PHASE-1.md). Pokyny pre prácu na projekte: [AGENTS.md](AGENTS.md).

## Stiahnutie APK

[Stiahnuť FinanceMaster v0.1.0 – debug APK](https://github.com/orszagh/FinanceMaster/raw/refs/heads/apk-download/FinanceMaster-v0.1.0-debug.apk), približne 54 MB, Android 8.0 alebo novší.

APK a jeho SHA-256 kontrolný súčet sú v samostatnej vetve [apk-download](https://github.com/orszagh/FinanceMaster/tree/apk-download). Ak priame stiahnutie nefunguje, otvor súbor APK v tejto vetve a zvoľ **Download raw file**. Pri súkromnom repozitári sa prihlás do svojho GitHub účtu. Na telefóne otvor stiahnutý súbor a podľa potreby povoľ inštaláciu z prehliadača.

GitHub Release sa nepodarilo vytvoriť pre blokovaný prístup ku GitHub API z cloud prostredia; APK je preto dostupný cez túto vetvu.

## Technológie
- **Kotlin + Coroutines:** jazyk aplikácie a asynchrónne IO bez blokovania UI.
- **Jetpack Compose + Material 3:** deklaratívne obrazovky a štandardné Android komponenty.
- **CameraX:** náhľad a snímanie so správou životného cyklu kamery.
- **ML Kit Text Recognition (bundled):** lokálne rozpoznávanie latinského textu bez sťahovania modelu pri prvom použití.
- **Room / SQLite:** trvalé záznamy účteniek a pozorovanie zmien cez Flow.
- **ViewModel + Navigation Compose:** stav obrazoviek a navigácia.

OCR je oddelené rozhraním `ReceiptOcrService`, parsovanie rozhraním `ReceiptParser`. Najprv sa uloží obrázok a záznam; chyba OCR ich nesmie odstrániť. Neisté extrahované údaje sú null.

## Vývoj
Vyžaduje JDK 17 alebo 21, Android SDK, platformu API 35 a Build Tools 35.0.0. Gradle 8.11.1 je pripnutý vo wrapperi aj s SHA-256 kontrolou. Android Gradle Plugin 8.9.2 a Kotlin 2.1.20 sú definované v koreňovom `build.gradle.kts`. Minimálny Android na telefóne je API 26 (Android 8.0). Projekt otvor v Android Studio alebo nastav `ANDROID_HOME`/lokálny ignorovaný `local.properties`.

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
# Používaj iba testovací emulátor/zariadenie bez reálnych účteniek:
./gradlew :app:connectedDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug APK je určené na súkromné testovanie. Finálne overenie na telefóne vrátane offline prvého spustenia a zachovania dát je v špecifikácii. Odinštalovanie alebo vymazanie dát aplikácie odstráni účtenky; export/zálohy nie sú súčasťou Phase 1.

Android test runner môže po integračných testoch odinštalovať cieľové APK a tým vymazať jeho dáta. `connectedDebugAndroidTest` preto nespúšťaj na bežnom telefóne/profile s uloženými účtenkami. Na vlastnom telefóne používaj manuálny checklist; `adb install -r` aktualizuje aplikáciu pri zachovaní dát.

## Kde nájsť kód
Všetok aplikačný Kotlin kód je v `app/src/main/java/sk/orszagh/financemaster/`:

| Cesta | Úloha |
|---|---|
| `FinanceMasterApplication.kt` | Ručné zostavenie závislostí, Room a obnovenie rozpracovaných záznamov |
| `data/` | Entity, DAO, databáza, súkromné obrázky a repository koordinujúce tok |
| `ocr/` | Rozhranie OCR a lokálne ML Kit rozpoznávanie |
| `parser/` | Rozhranie parsera a konzervatívna extrakcia piatich polí |
| `ui/` | ViewModel, navigácia, Home, History, Camera a Detail |
| `app/src/test/` | Parser, bezpečnosť toku a perzistencia skutočnej Room databázy cez Robolectric |
| `app/src/androidTest/` | Skutočné ML Kit OCR nad generovanou fotografiou, Room a opätovné otvorenie databázy |

Room `Flow` je pozorovateľný tok: pri zmene databázy sa obrazovky automaticky prekreslia. `ViewModel` drží stav aj pri otočení telefónu. OCR beží v aplikačnom `CoroutineScope`, aby pokračovalo pri prechode z kamery do histórie. Po force-stop sa obnoví pri ďalšom spustení. `SupervisorJob` oddelí zlyhanie jedného spracovania od ostatných operácií.

Fotografie sa ukladajú ako `filesDir/receipts/<UUID>.jpg`. Do databázy ide relatívna cesta, aby nebola závislá od konkrétnej systémovej cesty aplikácie. Dočasný `.pending` súbor sa synchronizuje a až potom premenuje; OCR číta iba finálne fotografie s existujúcim záznamom. Zlyhanie OCR zachová fotografiu, záznam aj už uložený raw text. Neisté hodnoty sa neodhadujú. Merchant v tomto základnom parseri vyžaduje explicitnú značku `Merchant:`, `Obchodník:` alebo `Predajca:`; pri bežnom neznačenom názve môže zostať null.

`ExifInterface` slúži na správnu orientáciu náhľadu podľa metadát fotografie bez prepisovania originálu. Robolectric je len testovacia závislosť: umožní overiť Room/Android API na JVM bez telefónu. Aplikácia nepoužíva INTERNET permission a zálohy vrátane prenosu dát na iné zariadenie sú vypnuté.

Room `Entity` predstavuje riadok v databáze a `DAO` definuje SQL operácie. **KSP** počas zostavenia generuje implementáciu DAO a kontroluje dotazy; schéma verzie 1 je v `app/schemas/`. **Navigation Compose** mení obrazovky a spravuje návrat späť. Android `Application` inicializuje databázu raz pre celý proces; preto OCR nezávisí od životnosti konkrétnej obrazovky.

## Pripravené cloud prostredie
V tomto prostredí sú JDK, SDK a cache mimo checkoutu. Aktivácia nastaví nástroje a aktuálny platformový proxy bez kopírovania prihlasovacích údajov:

```bash
source /workspace/finance-cloud/activate.sh
cd /workspace/FinanceMaster
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# S kompletnou cache sa dá zostaviť aj bez siete:
./gradlew --offline :app:assembleDebug
```

Opakovateľný inštalačný skript a pokyny na štart emulátora sú uložené v návrhu konfigurácie cloud prostredia. Živé procesy emulátora sa pri obnovení snapshotu nemusia zachovať. Na vlastnom počítači tieto cloud cesty netreba; stačí Android Studio, JDK a SDK uvedené vyššie.

## Výsledky overenia (1. 10. 2026)
- Debug APK sa úspešne zostavilo; jeho podpis bol overený nástrojom `apksigner`.
- 16 JVM testov prešlo: konzervatívny parser, poradie uloženia pred OCR, zlyhanie OCR/DB zápisu, opakovanie, obnova a Room perzistencia. Žiadne zlyhania ani preskočené testy.
- 2 Android testy prešli spolu na AOSP emulátore API 30 s vypnutým Wi-Fi aj mobilnými dátami: CameraX → súkromný JPEG → Room → History → Detail a bundled ML Kit OCR → polia/raw text → otvorenie databázy znova s nezmenenou fotografiou.
- Presný inštalačný skript bol vykonaný a prešiel; zostavenie z cache prešlo aj s `--offline`.
- Finálna kontrola `lintDebug` prešla bez chýb a bez upozornení.
- **Fyzický telefón ešte nebol overený.** Finálny checklist zahŕňa vlastnú účtenku, režim lietadlo pri prvom spustení, force-stop/otvorenie aplikácie a zachovanie dát. Úplný Definition of Done potvrdí toto manuálne overenie.
