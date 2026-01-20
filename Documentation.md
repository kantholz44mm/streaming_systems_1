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

1. Für die Installation wurde eine zentrale `docker-compose.yml` angelegt. Damit können mehrere Container verwaltet, gestartet und gestoppt werden. Diese zentrale Datei wird im Laufe der Aufgaben erweitert, je nach Anforderungen. Die finale Datei beinhaltet 3 Container:

- Apache Kafka
- Apache AvtiveMQ
- Redis

Es wurden jeweils die aktuellsten offiziellen Container-Images der jeweiligen Tools als Basis verwendet. Zusätzlich wird in der `docker-compose.yml` ein virtuelles Netzwerk erstellt, damit alle Container untereinander sowie über definierte Ports mit dem Hostssystem kommunizieren können. Für die Datenspeicherung können persistente Volumes des Hostsystems eingehängt werden, falls notwendig.

2. Die Leistungsfähigkeit wird als gut befunden, Docker im allgemeinen bietet weniger Overhead als eine vollwertige virtuelle Maschine, aber mehr als eine native Installation. Auf einem Laptop mit 8 GB Arbeitsspeicher ist die Speichergröße nicht ausreichend, um alle Aufgaben zu hosten. 16 GB sind dagegen ausreichend. Das Starten und Stoppen der Container ist sehr performant und braucht ca. 3 s. 

4. Um die korrekte Installation zu prüfen, werden simple Aufrufe der jeweiligen CLI-Tools sowie die Kommunikation der Container untereinander getestet. Die Logs der Container werden überprüft und etwaige Fehler werden behoben. Sobald alle Logs "sauber" sind, ist die Installation und Inbetriebnahme abgeschlossen.

5. Logs

6. Für die Erstellung der Konfiguration wurde ChatGPT verwendet, da die genaue Syntax und Parameter der Container nicht genau bekannt sind. Die Qualität wird als gut befunden, die Container sind direkt beim ersten Aufruf erfolgreich verfügbar und einzelne Parameter können in weiteren Prompts hinzugefügt werden (z.B Portfreischaltungen). Es ist keine manuelle Modifizierung der `docker-compose.yml` notwendig. Dadurch fällt das Durchsuchen der Dokumentation jedes Containers weg und der Produktivitätsgewinn ist hoch.

7. /

---

## Aufgabe 2 – JMS & LiDAR-Datenverarbeitung

1. Die Beispiele der Vorlesung sowie die mitgelieferten Code-Snippets sind in Java verfasst. Auch Apache Kafka ist Java-nativ. Daher wird Java für die Implementierung *aller*  Aufgaben gewählt. Der Code der Anwendung wird wie folgt aufgeteilt:

```
src
├── App.java
├── Consumer.java
├── Publisher.java
├── interfaces
└── models
    ├── LidarDataGrouped.java
    ├── LidarData.java
    └── LidarDistance.java
```

In künftigen Aufgaben werden Interfaces benötigt. Diese werden im Ordner `interfaces` abgelegt. `models` wird alle Datenklassen und PODs beinhalten. `Consumer.java` und `Publisher.java` sind Helferklassen, welche eine Abstraktion von "Messages schreiben/lesen" sind. In dieser Aufgabe wird ActiveMQ verwendet. Wird ein Publisher erstellt, kann dieser mit Hilfe der Methode `publish` eine einzelne Nachricht an den Message Broker senden. Wird ein Consumer verwendet, wird im Konstruktor ein Callback übergeben, welches als einziges Parameter eine empfangen Nachricht enthält. Zusätzlich kann jeweils die Topic und Startparameter wie der Hostname und der Port des Message Brokers übergeben werden. Dadurch muss nicht für jede Teilaufgabe die simple Handlung von "lies/schreib eine Nachricht" implementiert werden.

Die Model-klassen im Ordner `models` repräsentieren jeweils einen Datenpunkt nach einer Transformation. Da es insgesamt 3 Verarbeitungsschritte in der Aufgabenstellung gibt, gibt es ebenso 3 Datenmodelle. Für jedes der Datenmodelle werden die beiden Methoden `toJsonString` und `fromJsonString` implementiert. Dafür wird die Bibliothek `json_simple` genutzt. Prinzipiell wäre es sinnvoll, dafür ein Interface zu definieren. Jedoch erlaubt Java nicht, in einem Interface eine Methode als `static` zu deklarieren. Dies ist notwendig, da ansonsten die `fromJsonString` eine Instanz benötigt, um eine weitere aus einem JSON-Datum zu parsen. Dies ist semantisch unsinnvoll. Außerdem hätte einfach das Interface `Serializable` implementiert werden können. Dadurch wäre allerdings die einfach Ausgabe für Debuggingzwecke nicht mehr möglich gewesen. In späteren Aufgaben wird diese Methode dennoch verwendet.

Zur Lösung der Aufgabe wird eine zentrale `App.java` erstellt, die über Startparameter die auszuführende Teilaufgabe auswählt und startet. Es gibt folgende Startparameter:

- `--publisher`
- `--consumer_group`
- `--consumer_distance`
- `--consumer_summation`

Der Publisher liest die mitgelieferte Datei `Lidar-scans.json` ein und schreibt diese in das Topic `LIDAR_RAW`. Der Konsument `consumer_group` liest diese Topic aus, gruppiert die Messwerte und schreibt diese in das Topic `LIDAR_GROUPED`. `consumer_distance` und `consumer_summation` funktionieren analog. Beim Starten des Konsumenten `consumer_summation` wird die Ausgabe von `stdout` in eine Ausgabedatei gepiped. Der Publisher führt direkt nach dem Parsen der Datenpunkte die Filterung über die Qualitätsmetrik durch. "Falsche" Werte werden somit so früh wie möglich herausgefiltert und müssen nicht weiter verarbeitet werden. Dadurch entstehen keine Probleme bei der Gruppierung, da die Werte monoton in der Datei angeordnet sind.

2. Die Leistungsfähigkeit des Systems wird als gut eingeschätzt. Durch den Callback-Mechanismus des Consumers wird mit einer konfigurierbaren Frequenz gepollt. Angenommen, das Polling ist sinnvoll implementiert, werden dadurch kaum CPU-Ressourcen verwendet, solange keine Nachrichtenverarbeitung durchgeführt wird. Die Verbindungen zum Message Broker werden nur einmalig aufgebaut und offen gehalten, was ebenfalls die Performanz steigert. Die Serialisierung der Daten sowie die Netzwerkkommunikation sind das größte Bottleneck. Durch die Unterteilung der Transformationen in einzelne, unabhängige Instanzen können einzelne Teile davon skaliert werden. Diese Aufgabe benötigt einen Laptop mit >= 16GB Arbeitsspeicher. Auf einem MacBook M1 Pro mit 8 Kernen benötigt die gesamte Verarbeitungskette, inklusive Starten der JVM 10s. Damit wird ein Durchsatz von ~4429 Datensätzen/Sekunde erreicht, was als passabel eingestuft wird. Die Leistung wird tendentiell besser, wenn mehr Datenpunkte als Input genutzt werden, da dann der Overhead der JVM relativ geringer wird.

3. Siehe 2.

4. Um die Korrektheit zu testen, werden die Ausgabewerte jeder Transformation manuell auf Plausibilität untersucht, indem alle Nachrichten auf der Konsole geloggt werden. Zudem werden die Endergebnisse der gesamten Kette mit den mitgelieferten Beispielwerten verglichen. Die Ergebnisse werden zusätzlich mit denen von anderen Teams verglichen, um eine zusätzliche, generelle Plausibilitätsprüfung zu haben.

5. Bereits hier könnten die Datenpunkte mit Python o.Ä. visualisiert werden (z.B. 2D-Scatterplot). Wir haben dies erst ab Aufgabe 3 realisiert und hier noch auf die Textausgabe mit Logs gesetzt.

6. ChatGPT wurde verwendet, um den Consumer und Publisher zu implementieren. Hierbei wurden die API-Aufrufe generiert, die das Senden/Pollen von Nachrichten zum/vom Message Broker durchführen. Auch hier wurde dies so gemacht, um zu vermeiden, die Dokumentation der jeweiligen Bibliotheken durchsuchen zu müssen. Ca. 10% des Codes der gesamten Aufgabe sind somit KI-generiert. Der Produktivitätsgewinn ist hoch, die Qualität wird als gut bewertet, da der Code bereits bei der ersten Verwendung funktioniert. Zudem werden "versteckte" Features wie das Zurücksetzen der Gruppen-ID direkt implementiert, was bei manueller Implementierung vermutlich Zeit zum "Entdecken" gebraucht hätte.

7. Es wurde `json_simple` für die Implementierung der (De-)Serialisierung verwendet. Ein externes Bash-Skript für das Starten/Stoppen aller Teilaufgaben wurde implementiert. Dadurch wird einfach Zeit eingespart, um Iterationen während der Entwicklung schneller zu testen. Das ganze Projekt wird mit Maven gebaut, was das Dependency-Management vereinfacht und die Ausführung auf verschiedenene Plattformen (Linux, MacOS) deutlich vereinfachte.

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