## Was du jetzt tun musst

1. Öffne PR #3 **MAL compliance and exclusive provider readiness**.
2. Vergleiche den angezeigten PR-Head mit dem exakten Head in der finalen PR-Beschreibung. Merge nicht, wenn beide Werte abweichen oder der zugehörige Workflow nicht erfolgreich ist.
3. Prüfe den in der finalen PR-Beschreibung genannten Workflow-Run, Job, Testzähler, Artefaktnamen, Archivdigest, APK-Größe und APK-SHA-256.
4. Merge PR #3 ausschließlich mit **Create a merge commit**. Kein Squash, kein Rebase und kein Auto-Merge.
5. Prüfe danach den neuen `main`-CI-Lauf. Bei einem roten oder abgebrochenen Lauf nicht mit dem MyAnimeList-Antrag fortfahren.
6. Öffne das MyAnimeList-Entwicklerformular und kopiere die Werte aus `docs/mal-compliance/MAL_APPLICATION_GUIDE.md` exakt.
7. Ergänze nur deine echte Identität, eine von dir kontrollierte Kontakt-E-Mail und weitere tatsächlich verlangte persönliche Angaben.
8. Sende den Antrag ab. Eine technische Vorbereitung garantiert keine Genehmigung.
9. Falls das Portal einen Client Secret anzeigt: nicht kopieren, nicht speichern und niemals in App, GitHub, Chat, Issue, Actions oder CI eintragen. Kizomi benötigt nur den öffentlichen Client Identifier.
10. Trage den öffentlichen Client Identifier lokal als Gradle-Property oder Umgebungsvariable ein:
    - Stable: `MAL_CLIENT_ID_STABLE=<öffentlicher Client Identifier>`
    - Preview: `MAL_CLIENT_ID_PREVIEW=<öffentlicher Preview-Identifier>`
    - Debug: `MAL_CLIENT_ID_DEBUG=<öffentlicher Debug-Identifier>`
11. Für einen lokalen Testbuild aus dem Repository-Stamm:
    - `./gradlew clean assembleStableDebug -PMAL_CLIENT_ID_DEBUG=<öffentlicher Debug-Identifier>` für Debug;
    - für Stable die Build-Umgebung mit `MAL_CLIENT_ID_STABLE` konfigurieren und `./gradlew assembleStableDebug` ausführen.
12. Verwende in GitHub Actions ausschließlich Repository-/Environment-Variablen mit den Namen `MAL_CLIENT_ID_STABLE`, `MAL_CLIENT_ID_PREVIEW` und `MAL_CLIENT_ID_DEBUG`. Diese Identifier sind öffentlich; trotzdem keine Secrets oder Provider-Tokens dort speichern. Prüfe vor der Einrichtung, ob der Release-Workflow diese Variablen explizit liest.
13. Installiere den kontrollierten Build auf einem Testgerät. Starte frisch und bestätige: zwei gleichwertige Providerbuttons, keine Vorauswahl und kein Providertraffic vor Auswahl.
14. Wähle MyAnimeList, öffne Privacy, Terms, Data Deletion und MAL-Bedingungen, bestätige die nicht vorausgewählte Checkbox und führe den Browser-OAuth aus.
15. Prüfe Login-Abbruch und falschen/alten Callback: Zustand bleibt `UNCONFIGURED`, keine halben Credentials und keine Providerdaten.
16. Prüfe Refresh: lasse einen Access Token ablaufen oder verwende eine kontrollierte Testbedingung; genau ein koordinierter Refresh darf erfolgen, danach muss die Anfrage funktionieren. Wiederholte 401 müssen zur Neuanmeldung führen.
17. Prüfe Read: Suche, Detailseite und eigene Anime-/Manga-Liste müssen aus MyAnimeList stammen.
18. Prüfe Write/read-back mit einem ungefährlichen Testeintrag: ändere Status oder Fortschritt, warte auf Bestätigung, lade den Eintrag neu und vergleiche den von MyAnimeList gelesenen Zustand. Stelle den ursprünglichen Wert anschließend wieder her.
19. Prüfe **Disconnect and delete all local provider data**, beende die App vollständig und starte neu. Onboarding muss erscheinen; ein neuer MAL-Login muss erneut Einwilligung und Browserautorisierung verlangen.
20. Prüfe mit einem lokalen VPN/Proxy oder Android-Netzwerkinspektor im MAL-Modus: keine Anfrage an `anilist.co` oder dessen GraphQL-Endpunkt; erlaubt sind nur die dokumentierten MAL-Ziele und von der API gelieferte Bildhosts. Entferne keine Zertifikatsschutzmaßnahme in einem Produktionsbuild.
21. Prüfe den destruktiven Wechsel MAL → AniList und AniList → MAL. Es darf keine Datenkopie, kein Fallback und niemals zwei aktive Sessions geben.
22. Informiere MyAnimeList bei größeren Releases, wenn Endpoints, Redirects, Datenverarbeitung, Monetarisierung oder der Anwendungszweck wesentlich geändert werden oder wenn die Providerbedingungen/Kommunikation dies verlangen.
