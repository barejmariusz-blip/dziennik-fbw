# Dziennik FBW

Aplikacja na Androida do prowadzenia planu Szostek Balance FBW (3/4/5 dni, priorytet 1/2) z cyklami wersji HD / VC / HC / VD.

## Jak pobrać APK
1. Wejdź w **Releases → latest** w tym repozytorium.
2. Pobierz `dziennik-fbw.apk` i zainstaluj (zezwól na instalację z nieznanego źródła).

Każda zmiana w gałęzi `main` buduje nową wersję automatycznie (zakładka **Actions**).
Nowa wersja instaluje się jako aktualizacja – dane zostają.

## Kod
- `app/src/main/assets/index.html` – cała aplikacja (HTML/JS), dane w pamięci telefonu.
- `app/src/main/java/.../MainActivity.java` – okno Androida, zapis plików kopii do Pobranych.
