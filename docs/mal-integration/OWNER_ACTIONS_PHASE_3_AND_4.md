# Besitzeraktionen für MAL Phase 3 und Phase 4

Diese Anleitung beschreibt nur die noch notwendigen menschlichen Schritte. Sie enthält keine geheimen Werte und verlangt keine manuelle Bearbeitung der App-Datenbank.

## 1. Was jetzt getan werden muss

### Phase 3: MAL-Anmeldung real freischalten und prüfen

1. Öffne das MAL Developer Portal.
2. Registriere für die App genau diese drei Redirect-URIs:

   - `anisyncplus-debug://oauth/mal/callback`
   - `anisyncplus-preview://oauth/mal/callback`
   - `anisyncplus://oauth/mal/callback`

3. Erfasse die drei öffentlichen Client-IDs, die MAL für die registrierten App-Varianten ausgibt.
4. Stelle die öffentlichen Client-IDs beim Build unter diesen Namen bereit:

   - `MAL_CLIENT_ID_DEBUG`
   - `MAL_CLIENT_ID_PREVIEW`
   - `MAL_CLIENT_ID_STABLE`

5. Verwende für die erste Prüfung ein entbehrliches MAL-Testkonto, nicht das wichtigste persönliche Konto.
6. Baue und installiere mindestens die Debug-Variante mit `MAL_CLIENT_ID_DEBUG`.
7. Führe auf einem echten Android-Gerät die Prüfungen aus dem Abschnitt „Gerätetest Phase 3“ durch.

Ein Client Secret wird nicht benötigt. Speichere oder übermittle kein Client Secret.

### Phase 4: bestehende Installation aktualisieren und Daten prüfen

Für die Implementierung selbst ist normalerweise keine manuelle Vorarbeit notwendig. Sobald ein geprüftes Phase-4-APK bereitsteht:

1. Erstelle vorher nur ein normales App-/Geräte-Backup nach deinem üblichen Verfahren, sofern du eines verwendest.
2. Deinstalliere die vorhandene App nicht.
3. Lösche keine App-Daten.
4. Installiere das neue APK als Update über die bestehende Installation.
5. Führe die Prüfungen aus dem Abschnitt „Gerätetest Phase 4“ durch.

## 2. Was optional ist

- Die Preview- und Stable-Client-ID können nach dem erfolgreichen Debug-Test separat geprüft werden.
- Ein zweites Testgerät oder ein Emulator kann zusätzlich verwendet werden, ersetzt aber nicht den ersten echten Gerätetest für den Browser-Callback.
- Vor dem Phase-4-Update können einige bekannte Anime- und Manga-Einträge notiert werden, damit die Bibliothek nach dem Update leichter verglichen werden kann.

## 3. Was noch nicht getan werden darf

- Keine Phase-4-PR mergen, bevor Review und gewünschter Gerätetest abgeschlossen sind.
- Keine MAL-Library, MAL-Suche, MAL-Details, Routing-Einstellungen, MAL-Listenwrites, Dual-Sync- oder Compare-and-sync-Funktion als Teil dieser Prüfung erwarten oder freischalten.
- Keine Datenbankdatei manuell öffnen oder bearbeiten.
- Keine AniList-, MAL- oder lokalen Medien-IDs manuell ändern.
- Keine alten App-Daten löschen, um einen Fehler scheinbar zu beheben.
- Keine vorhandene Zuordnung durch Titelähnlichkeit oder Vermutung manuell in der Datenbank erzwingen.
- Kein Client Secret in Build-Dateien, Umgebungsvariablen, GitHub Issues, PRs oder Nachrichten eintragen.

## 4. Welche Werte benötigt werden

Benötigt werden ausschließlich die öffentlichen MAL Client-IDs:

| App-Variante | Eingabename | Registrierte Redirect-URI |
|---|---|---|
| Debug | `MAL_CLIENT_ID_DEBUG` | `anisyncplus-debug://oauth/mal/callback` |
| Preview | `MAL_CLIENT_ID_PREVIEW` | `anisyncplus-preview://oauth/mal/callback` |
| Stable | `MAL_CLIENT_ID_STABLE` | `anisyncplus://oauth/mal/callback` |

Nicht benötigt werden:

- Client Secret;
- Access Token;
- Refresh Token;
- Authorization Code;
- PKCE-Verifier;
- OAuth-State.

## 5. Wo die Werte eingetragen werden

Die öffentlichen Client-IDs werden beim Build als Gradle-Property oder Umgebungsvariable mit den oben genannten Namen bereitgestellt.

Beispiel für eine lokale, nicht eingecheckte Gradle-Konfiguration:

```properties
MAL_CLIENT_ID_DEBUG=<öffentliche Debug-Client-ID>
MAL_CLIENT_ID_PREVIEW=<öffentliche Preview-Client-ID>
MAL_CLIENT_ID_STABLE=<öffentliche Stable-Client-ID>
```

Die tatsächlichen Werte dürfen nicht in einen Commit aufgenommen werden, wenn die lokale Build-Umgebung sie getrennt verwalten soll. Auch wenn Client-IDs öffentlich sind, sollen sie nicht unnötig in Issues oder Chatnachrichten kopiert werden.

## 6. Gerätetest Phase 3

Führe diese Schritte mit der Debug-App und einem entbehrlichen MAL-Testkonto aus:

1. Öffne Einstellungen → MyAnimeList.
2. Starte „Mit MyAnimeList verbinden“.
3. Prüfe, dass sich der Systembrowser öffnet und keine WebView innerhalb der App erscheint.
4. Melde dich beim Testkonto an und bestätige den Zugriff.
5. Prüfe, dass der registrierte Callback zur App zurückkehrt.
6. Prüfe, dass die App einen verbundenen Zustand zeigt und keine Codes oder Token anzeigt.
7. Beende die App vollständig und starte sie erneut.
8. Prüfe, dass das Konto nach dem Neustart weiterhin verbunden ist.
9. Verwende die App lange genug oder eine kontrollierte Testkonfiguration, um einen Token-Refresh auszulösen; prüfe, dass keine erneute Anmeldung verlangt wird, solange der Refresh gültig ist.
10. Melde das MAL-Konto in der App lokal ab.
11. Prüfe, dass der Zustand „nicht verbunden“ oder „erneute Anmeldung erforderlich“ korrekt erscheint.
12. Führe einen erneuten Login durch und prüfe den vollständigen Ablauf nochmals.

Bei einem Fehler notiere nur:

- App-Variante und Versions-/Commitangabe;
- Gerätemodell und Android-Version;
- den sichtbaren, bereinigten Fehlertext;
- den Schritt, an dem der Fehler auftrat;
- ob der Browser geöffnet wurde und ob die App zurückkam.

## 7. Gerätetest Phase 4

Installiere das Phase-4-APK über eine bestehende Installation, die bereits reale Bibliotheksdaten enthält.

1. Öffne direkt nach dem Update die Bibliothek.
2. Prüfe, dass vorhandene Anime-Einträge weiterhin vorhanden sind.
3. Prüfe, dass vorhandene Manga-Einträge weiterhin vorhanden sind.
4. Öffne mehrere bekannte Anime-Details.
5. Öffne mehrere bekannte Manga-Details.
6. Prüfe bei den Beispielen Fortschritt und Listenstatus.
7. Ändere bei einem Testeintrag Fortschritt oder Status und prüfe das bestehende AniList-Verhalten.
8. Starte die App neu und prüfe die Einträge nochmals.
9. Achte besonders auf:

   - fehlende Bibliothekseinträge;
   - doppelte Einträge;
   - vertauschte Anime-/Manga-Zuordnungen;
   - verlorenen Fortschritt;
   - verlorenen Status;
   - nicht mehr öffnende Details;
   - unerwartete Neuanmeldung bei AniList oder MAL.

Melde konkrete Medienbeispiele mit sichtbarem Titel und Medientyp. Teile keine private Kontoinformation und keine Zugangsdaten.

## 8. Welche Ergebnisse zurückgemeldet werden sollen

Für Phase 3:

- welche Variante getestet wurde;
- ob Browserstart funktioniert hat;
- ob der Callback zur App zurückkehrte;
- ob die Verbindung einen App-Neustart überstand;
- ob Refresh funktionierte;
- ob Logout funktionierte;
- ob der erneute Login funktionierte;
- Gerät und Android-Version;
- bereinigter Fehlertext, falls vorhanden.

Für Phase 4:

- ob das APK als Update installiert werden konnte;
- ob Anime und Manga vollständig sichtbar blieben;
- ob Fortschritt und Status erhalten blieben;
- ob Duplikate oder fehlende Einträge auftraten;
- konkrete betroffene Titel und Medientypen;
- Gerät und Android-Version.

## 9. Daten, die niemals geteilt werden dürfen

Teile niemals:

- Access Token;
- Refresh Token;
- Authorization Code;
- PKCE-Verifier;
- OAuth-State;
- Authorization-Header;
- Browser-Cookies;
- MAL-Passwort;
- AniList-Zugangsdaten;
- vollständige verschlüsselte Vault- oder Session-Dateien;
- Client Secret;
- vollständige private App-Datenbank oder private Account-Daten.

Ein Screenshot oder Log muss vor dem Teilen auf solche Werte geprüft und bereinigt werden.
