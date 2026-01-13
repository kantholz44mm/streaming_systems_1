# Fragen – Streaming Systems Praktikum (WS 2025/26)

## Allgemeine Fragen zur Ausarbeitung
Diese Fragen sollen **für jede Aufgabe** in der schriftlichen Ausarbeitung beantwortet werden:

1. Welche Lösungs- und Umsetzungsstrategie wurde gewählt?
2. Wie schätzen Sie die Leistungsfähigkeit der Lösung ein?
   - Wurden neben der Basisfunktionalität zusätzliche Funktionen realisiert?
3. Welche nicht-funktionalen Anforderungen (z.B. Skalierbarkeit, Durchsatz) wurden untersucht?
   - Mit welchem Ergebnis?
4. Wie haben Sie sich von der Korrektheit und Vollständigkeit der Lösung überzeugt?
   - Gibt es eine systematische Teststrategie?
5. Wie können die berechneten Ergebnisse geeignet dargestellt werden?
   - Gibt es naheliegende Visualisierungsmöglichkeiten?
6. Bei welchen Aufgaben haben Sie ChatGPT oder vergleichbare KI-Systeme eingesetzt?
   - Wie hoch ist der Anteil maschinell generierten Codes?
   - Wie schätzen Sie die Qualität ein?
   - Wie aufwändig war die Anpassung und Fehlerkorrektur?
   - Wie bewerten Sie den Produktivitätsgewinn?
7. Welche zusätzlichen Frameworks und Bibliotheken wurden eingesetzt?
   - Warum?

---

## Aufgabe 1 – Installation (Apache ActiveMQ & Kafka)

Die Installation erfolgte containerbasiert mit Docker Compose. Für ActiveMQ Artemis wurde das offizielle Docker-Image verwendet und der Broker über vordefinierte Zugangsdaten konfiguriert. Die Web-Konsole ermöglichte eine einfache Überprüfung der Installation, indem eine Queue angelegt sowie Testnachrichten erfolgreich gesendet und konsumiert wurden.

Apache Kafka wurde ebenfalls über ein offizielles Docker-Image im KRaft-Modus betrieben. Hierfür war eine explizite Konfiguration der benötigten Umgebungsvariablen erforderlich, um den Broker lokal erreichbar zu machen. Die Korrektheit der Installation wurde mithilfe der mitgelieferten Kommandozeilenwerkzeuge überprüft, indem ein Topic erstellt und Nachrichten zwischen einem Producer und einem Consumer ausgetauscht wurden.

Durch diese Tests konnte bestätigt werden, dass beide Systeme korrekt installiert sind und grundlegende Messaging-Funktionalitäten bereitstellen. Damit ist eine stabile Basis für die nachfolgenden Praktikumsaufgaben geschaffen.

---

## Aufgabe 2 – JMS & LiDAR-Datenverarbeitung

Hinweis: Von der Abstandsberechnung sollen alle Messungen, bei denen die Qualit¨at
kleiner einem konfigurierbaren Wert (z.B. 15) sind, ausgeschlossen werden? ist das geprüft

### Verarbeitung & Datenstrukturen
- Welche Datenstrukturen eignen sich zur Repräsentation der Messpunkte?
- Welche Datenstrukturen eignen sich zur Verarbeitung der Scans?
- Wie können Berechnungen (Punkte, Abstände etc.) ausgelagert und wiederverwendet werden?

### Korrektheit
- Wie können Sie die Korrektheit Ihrer Lösung überprüfen?

---

## Aufgabe 3 – Apache Kafka
Hinweis: Sie können das Hinzufügen der Scan-Zahl, die Abstandsberechnung, die Berechnung der Gesamtdistanz eines Scans sowie das Erstellen der Ausgabedatei in einer Konsumenten-Anwendung umsetzen?

1. Wie unterscheiden sich die berechneten Werte von Aufgabe 2 (JMS) und Aufgabe 3 (Kafka)?
2. Wie lassen sich die Datenpunkte eines Scans sinnvoll visualisieren?
3. Welche Tools eignen sich für die Visualisierung (z.B. Plotly, Grafana)?

---

## Aufgabe 4 – CQRS & Event Sourcing

### Fragen am Rande (explizit gestellt)
1. Warum sollte `Position` die Schnittstelle `Comparable` implementieren?
2. `Position` ist ein „reines“ Datenobjekt – würde das nicht für eine `record`-Umsetzung sprechen?
3. Was spricht dagegen?

### Architektur & Design
4. Wie sieht ein geeignetes Domänenmodell zur Validierung der Commands aus?
5. Wie können Commands abgelehnt und Fehler an den Aufrufer kommuniziert werden?
6. Wie werden Commands auf Events abgebildet?
7. Wie wird das Domänenmodell nach Annahme eines Commands aktualisiert?
8. Wie wird das Query Model aufgebaut und gepflegt?
9. Welche Datenstrukturen eignen sich für das Query Model?
10. Wie kann eine Projektion zur Aktualisierung des Query Models umgesetzt werden?
11. Wie kann die strikte Trennung zwischen Domänenmodell und Query Model sichergestellt werden?
12. Wie können Korrektheit und Vollständigkeit des Systems getestet werden?

### Version 2 – Dynamischer Wiederaufbau
13. Wie kann das Domänenmodell aus den im Event Store gespeicherten Ereignissen rekonstruiert werden?
14. Wie können Aggregate oder globale Informationen (z.B. vergebene Namen) geladen werden?
15. Warum wird die Map aus Version 1 auf der Write Side überflüssig?

### Version 3 – Erweiterungen
16. Wie kann das automatische Entfernen eines Fahrzeugs nach N Bewegungen umgesetzt werden?
17. Wie kann erkannt werden, ob ein Fahrzeug eine Position erneut betritt?
18. Wie kann geprüft werden, ob sich bereits ein anderes Fahrzeug auf der Zielposition befindet?

#### Diskussionsfragen zu Lösungsvarianten
19. Welche Vor- und Nachteile hat:
    - Nutzung der Query-Schnittstelle?
    - Anpassung des Algorithmus zum Modellaufbau?
    - Einführung eines zusätzlichen Aggregats (Positionsaggregat)?
    - Verwendung von Snapshots?
20. Welche Variante wurde umgesetzt – und warum?

### Version 4 – (Optional)

---

## Aufgabe 5 – Apache Beam & Datenanalyse
1. Wie müssen Datensätze modelliert werden (z.B. `SpeedEvent`)?
2. Wie wird die Ereigniszeit korrekt verarbeitet?
3. Welche Transforms sind notwendig (Filter, GroupByKey, Combine, Window)?
4. Wie werden Zeitfenster (Batch Window) umgesetzt?
5. Wie wird die Durchschnittsgeschwindigkeit berechnet und ausgegeben?
6. Was muss geändert werden, um ein Sliding Window (10s Länge, 5s Slide) zu verwenden?
7. Welche Auswirkungen hat dies auf die Ergebnisse?

---

## Aufgabe 6 – CEP mit Esper / EPL
1. Gibt es Unterschiede zu Aufgabe 5 (Apache Beam)?
2. Was könnten Gründe für diese Unterschiede sein?

---

## Aufgabe 7 – Complex Event Processing (Erweiterung)
1. Wie kann die Korrektheit der Lösung überprüft werden?
2. Wie müsste die Apache-Beam-Lösung aus Aufgabe 5 erweitert werden,wie sähe ein mögliches Umsetzungskonzept aus?

---

## Aufgabe 8 – Read-Process-Write (optional)


---

## Aufgabe 9 – Vergleichende Analyse (optional)

### Vergleichskriterien
1. Repräsentation von Ereignissen: Wie und mit welchen Datenstrukturen werden Ereignisse repräsentiert? Wie flexibel schätzen Sie die Datenstrukturen ein? Werden Vererbungsstrukturen unterst¨utzt? Welche Metainformationen werden mitgeführt?

2. Wie werden Ereignisse / Nachrichten erzeugt und wie können diese dem Broker übermittelt werden? Welche Schritte sind hierzu erforderlich? Können Ereignisse gebündelt übertragen werden?

3. Wie kann eine Konsumentenanwendung Ereignisse / Nachrichten von dem Broker entgegennehmen? Welche Stategien werden hierzu angeboten?

4. Werden Ereignisse / Nachrichten dauerhaft gespeichert?

5. Welche Auslieferungs- und Verarbeitungsgarantien werden unterstützt?

6. Welche Maßnahmen müssen ergriffen werden, damit Ereignisse / Nachrichten gegen
einen unberechtigten Zugriff gesichert werden können?

7. Welche Programmiermodelle werden angeboten, um eintreffende Nachrichten / Ereignisse zu verarbeiten? Werden zustandsbehaftete Transformationen wie etwa Aggregationsfunktionen unterstützt und können Nachrichten / Ereignisse zeitlich gruppiert werden? Ist eine Auswertung nach Ereigniszeit möglich?

8. Welche Skalierungsmöglichkeiten werden vom System unterstützt, um mitwachsen zu können hinsichtlich Datenvolumen, Datenfrequenz sowie Anzahl von Produzenten und Konsumenten?

9. Gibt es Konzepte hinsichtlich einer Ausfallsicherheit des Systems? Wenn ja, wie ist die grundsätzliche Arbeitsweise.

10. Typsicherheit: Wie typsicher ist das Programmiermodell? Wie können Fehler bei der Interpretation einer Nachricht erkannt werden? Z.B. erwartet ein Konsument einen Ereignistyp A, erhält jedoch eine Instanz vom Typ B.

11. In welchen Programmiersprachen können die Produzenten- und Konsumentenanwendungen erstellt werden?

### Reflexion
16. Was sind die Stärken und Schwächen der jeweiligen Systeme?
17. Wie schätzen Sie das zukünftige Potenzial der Technologien ein?