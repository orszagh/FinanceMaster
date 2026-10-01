# FinanceMaster – APK na stiahnutie

Táto vetva obsahuje inštalačný debug APK verzie 0.1.0. Zdrojový kód a špecifikácia sú vo vetve [main](https://github.com/orszagh/FinanceMaster/tree/main).

## Stiahnutie do telefónu

[Stiahnuť FinanceMaster-v0.1.0-debug.apk](https://github.com/orszagh/FinanceMaster/raw/refs/heads/apk-download/FinanceMaster-v0.1.0-debug.apk)

Ak priame stiahnutie nefunguje, otvorte súbor APK v tejto vetve a zvoľte **Download raw file**. Pri súkromnom repozitári musíte byť prihlásený do GitHub účtu s prístupom. Android môže vyžadovať povolenie inštalácie z prehliadača.

Vyžaduje Android 8.0 alebo novší. APK je podpísaný vývojovým debug kľúčom a má približne 54 MB. Aplikácia pracuje úplne offline, fotografie a databázu uchováva vo svojom súkromnom úložisku.

Overenie: zostavenie APK, 16 JVM testov, 2 Android testy na offline emulátore a lint bez problémov. Fyzický telefón ešte nebol overený; manuálny checklist je v docs/PHASE-1.md vo vetve main.

Súbor .sha256 obsahuje kontrolný súčet APK. GitHub Release sa v tomto prostredí nepodarilo vytvoriť pre blokovaný prístup ku GitHub API, preto je APK uložený v samostatnej vetve.
