# FinanceMaster – pravidlá práce

## Rozsah
- Implementujeme iba Phase 1 opísanú v `docs/PHASE-1.md`.
- Súkromná Android aplikácia pre jeden telefón; všetko funguje offline.
- Nepridávať cloud, účty, autentifikáciu, Supabase, Firebase ani položky účteniek.
- Komunikácia, dokumentácia a vysvetlenia sú primárne v slovenčine. Identifikátory a Android/Kotlin konvencie sú v angličtine. Komentáre vysvetľujú dôvod, nie zjavný kód.

## Ochrana dát
- Najprv trvalo uložiť fotografiu do súkromného úložiska, potom vložiť Room záznam `CAPTURED`, až potom spustiť OCR.
- OCR ani parser nesmú vymazať fotografiu alebo záznam pri chybe.
- Stavy: `CAPTURED`, `PROCESSING`, `PROCESSED`, `OCR_FAILED`.
- Po prerušení procesu sa rozpracované záznamy obnovia pri ďalšom spustení.
- Neisté extrahované hodnoty sú `null`; nevymýšľať údaje.
- Žiadna povinná ručná forma po odfotení. Žiadne deštruktívne databázové migrácie.
- Fotografie, OCR text a osobné údaje nepatria do logov ani Git repozitára.

## Technológie a jednoduchosť
Kotlin, Compose, Material 3, CameraX, bundled ML Kit Text Recognition, Room, Coroutines, ViewModel a Navigation Compose. OCR za `ReceiptOcrService`, extrakcia za `ReceiptParser`. Uprednostniť malý počet závislostí, ručné zostavenie závislostí pred DI frameworkom a zrozumiteľné vrstvy.

## Overovanie
- Po väčších krokoch `./gradlew :app:assembleDebug`.
- Jednotkové testy: `./gradlew :app:testDebugUnitTest`; statické kontroly: `./gradlew :app:lintDebug`.
- Android integračné testy: `./gradlew :app:connectedDebugAndroidTest` iba na testovacom zariadení/emulátore bez reálnych účteniek; runner môže po teste odinštalovať APK a vymazať dáta. Bežný telefón overovať manuálne.
- Fyzický telefón je potrebný na finálne overenie CameraX a OCR podľa checklistu v špecifikácii.
- Rozlišovať vykonané, úspešné, zlyhané a nevykonané overenia. Nevydávať zostavenie APK za dôkaz funkčnosti fotoaparátu.
- Po každom veľkom kroku stručne uviesť: čo vzniklo, ako funguje, kde je kód a čo nasleduje.
- Použiť existujúci checkout v izolovanom cloud prostredí; nevytvárať Git worktree bez explicitnej žiadosti.
