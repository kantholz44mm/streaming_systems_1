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

In künftigen Aufgaben werden Interfaces benötigt. Diese werden im Ordner `interfaces` abgelegt. `models` wird alle Datenklassen und PODs beinhalten. `Consumer.java` und `Publisher.java` sind Helferklassen, welche eine Abstraktion von "Messages schreiben/lesen" mit Hilfe von JMS sind. In dieser Aufgabe wird ActiveMQ verwendet. Wird ein Publisher erstellt, kann dieser mit Hilfe der Methode `publish` eine einzelne Nachricht an den Message Broker senden. Wird ein Consumer verwendet, wird im Konstruktor ein Callback übergeben, welches als einziges Parameter eine empfangen Nachricht enthält. Zusätzlich kann jeweils die Topic und Startparameter wie der Hostname und der Port des Message Brokers übergeben werden. Dadurch muss nicht für jede Teilaufgabe die simple Handlung von "lies/schreib eine Nachricht" implementiert werden.

Die Model-klassen im Ordner `models` repräsentieren jeweils einen Datenpunkt nach einer Transformation. Da es insgesamt 3 Verarbeitungsschritte in der Aufgabenstellung gibt, gibt es ebenso 3 Datenmodelle. Für jedes der Datenmodelle werden die beiden Methoden `toJsonString` und `fromJsonString` implementiert. Dafür wird die Bibliothek `json_simple` genutzt. Prinzipiell wäre es sinnvoll, dafür ein Interface zu definieren. Jedoch erlaubt Java nicht, in einem Interface eine Methode als `static` zu deklarieren. Dies ist notwendig, da ansonsten die `fromJsonString` eine Instanz benötigt, um eine weitere aus einem JSON-Datum zu parsen. Dies ist semantisch unsinnvoll. Außerdem hätte einfach das Interface `Serializable` implementiert werden können. Dadurch wäre allerdings die einfach Ausgabe für Debuggingzwecke nicht mehr möglich gewesen. In späteren Aufgaben wird diese Methode dennoch verwendet. Für die Handhabung und Verarbeitung von 2D-Vektoren wird die Klasse `Vector2D` implementiert. Diese bietet die üblichen Methoden `distance`, `normalize`, `delta` usw.

Zur Lösung der Aufgabe wird eine zentrale `App.java` erstellt, die über Startparameter die auszuführende Teilaufgabe auswählt und startet. Es gibt folgende Startparameter:

- `--publisher`
- `--consumer_group`
- `--consumer_distance`
- `--consumer_summation`

Der Publisher liest die mitgelieferte Datei `Lidar-scans.json` ein und schreibt diese in das Topic `LIDAR_RAW`. Der Konsument `consumer_group` liest diese Topic aus, gruppiert die Messwerte und schreibt diese in das Topic `LIDAR_GROUPED`. `consumer_distance` und `consumer_summation` funktionieren analog. Beim Starten des Konsumenten `consumer_summation` wird die Ausgabe von `stdout` in eine Ausgabedatei gepiped. Der Publisher führt direkt nach dem Parsen der Datenpunkte die Filterung über die Qualitätsmetrik durch. "Falsche" Werte werden somit so früh wie möglich herausgefiltert und müssen nicht weiter verarbeitet werden. Dadurch entstehen keine Probleme bei der Gruppierung, da die Werte monoton in der Datei angeordnet sind.

2. Die Leistungsfähigkeit des Systems wird als gut eingeschätzt. Durch den Callback-Mechanismus des Consumers wird mit einer konfigurierbaren Frequenz gepollt. Angenommen, das Polling ist sinnvoll implementiert, werden dadurch kaum CPU-Ressourcen verwendet, solange keine Nachrichtenverarbeitung durchgeführt wird. Die Verbindungen zum Message Broker werden nur einmalig aufgebaut und offen gehalten, was ebenfalls die Performanz steigert. Die Serialisierung der Daten sowie die Netzwerkkommunikation sind das größte Bottleneck. Durch die Unterteilung der Transformationen in einzelne, unabhängige Instanzen können einzelne Teile davon skaliert werden. Diese Aufgabe benötigt einen Laptop mit >= 16GB Arbeitsspeicher. Auf einem MacBook M1 Pro mit 8 Kernen benötigt die gesamte Verarbeitungskette, inklusive Starten der JVM 10s. Damit wird ein Durchsatz von ~4429 Datensätzen/Sekunde erreicht, was als passabel eingestuft wird. Die Leistung wird tendentiell besser, wenn mehr Datenpunkte als Input genutzt werden, da dann der Overhead der JVM relativ geringer wird. Eine Überwachung der Kernauslastung mit `htop` zeigt, dass der Großteil der Kerne kaum ausgelastet sind, während andere zu ca.70% ausgelastet sind. Dies bestätigt die Annahme, dass das Netzwerk ein vorviegender Bottleneck ist.

3. Siehe 2.

4. Um die Korrektheit zu testen, werden die Ausgabewerte jeder Transformation manuell auf Plausibilität untersucht, indem alle Nachrichten auf der Konsole geloggt werden. Zudem werden die Endergebnisse der gesamten Kette mit den mitgelieferten Beispielwerten verglichen. Die Ergebnisse werden zusätzlich mit denen von anderen Teams verglichen, um eine zusätzliche, generelle Plausibilitätsprüfung zu haben.

5. Bereits hier könnten die Datenpunkte mit Python o.Ä. visualisiert werden (z.B. 2D-Scatterplot). Wir haben dies erst ab Aufgabe 3 realisiert und hier noch auf die Textausgabe mit Logs gesetzt.

6. ChatGPT wurde verwendet, um den Consumer und Publisher zu implementieren. Hierbei wurden die API-Aufrufe generiert, die das Senden/Pollen von Nachrichten zum/vom Message Broker durchführen. Auch hier wurde dies so gemacht, um zu vermeiden, die Dokumentation der jeweiligen Bibliotheken durchsuchen zu müssen. Ca. 10% des Codes der gesamten Aufgabe sind somit KI-generiert. Der Produktivitätsgewinn ist hoch, die Qualität wird als gut bewertet, da der Code bereits bei der ersten Verwendung funktioniert. Zudem werden "versteckte" Features wie das Zurücksetzen der Gruppen-ID direkt implementiert, was bei manueller Implementierung vermutlich Zeit zum "Entdecken" gebraucht hätte.

7. Es wurde `json_simple` für die Implementierung der (De-)Serialisierung verwendet. Ein externes Bash-Skript für das Starten/Stoppen aller Teilaufgaben wurde implementiert. Dadurch wird einfach Zeit eingespart, um Iterationen während der Entwicklung schneller zu testen. Das ganze Projekt wird mit Maven gebaut, was das Dependency-Management vereinfacht und die Ausführung auf verschiedenene Plattformen (Linux, MacOS) deutlich vereinfachte.

## Aufgabe 3 – Apache Kafka

1. Für diese Aufgabe wird die Implementierung von Aufgabe 2. wiederverwendet. Es werden lediglich neue Implementierungen der Klassen Consumer und Publisher angefertigt. Diese interagieren nun nicht mehr über JMS mit ActiveMQ sondern direkt mit einem Kafka-Server. Daher sind Inbetriebnahme und Debugging ebenfalls gleich. Die berechneten Werte sind binär identisch (wie zu erwarten ist, wenn die Implementierung der Transformationen gleich bleibt).

Für die Visualisierung wird ein Python-Skript mit Matplotlib erstellt, das die Datenpunkte jeder Gruppe in einem 2D-Scatterplot "von oben" zeigt. Die einzelnen Frames werden als GIF zusammengestellt und sehen wie folgt aus:

![Visualisierung der LIDAR-Datenpunkte](scripts/lidar_visualisation.gif)

Man könnte natürlich auch andere Tools wie Plotly oder Grafana verwenden, aber wir waren/sind mit der animierten Grafik zufrieden. Wir haben die Zusammenführung der einzelnen Teilaufgaben in eine Ausführungsinstanz nicht vorgenommen, da dabei die gute Skalierbarkeit verloren geht. Die Umsetzung mit der aktuellen Architektur ist sehr simpel, da einfach mehrere der Startparameter mitgegeben werden können.

2. Siehe Aufgabe 2.2. Die Leistungsfähigkeit ist erneut durch (De)Serialisierung/Netzwerk beschränkt. Bei der Messung des Durchsatzes kommen sehr ähnliche Werte wie bei der Umsetzung mit JMS zustande (+-10%).

3. Siehe 2. bzw. 2.2

4. Auch hier werden manuelle Tests jeder Transformation durchgeführt. Die Werte werden mit `diff` mit denen von Aufgabe 2. verglichen und sind identisch. Zudem wird durch die Visualisierung ein plausibles LIDAR-Umfeld ersichtlich, was auch zwischen Gruppen/Frames nahezu gleich bleibt. In der Visualisierung ist erkennbar, dass Sprünge zwischen okkludierten Objekten generiert werden, was eine natürliche Konsequenz des LIDAR-Arbeitsprinzips ist.

5. Siehe 1. und 4. Visualisierung mit Matplotlib.

6. ChatGPT wurde verwendet, um die Kafka-Implementierungen der Klassen `Consumer` und `Publisher` zu generieren. Dadurch wurde erneut Aufwand gespart, da keine Dokumentation gelesen werden musste. Erneut ist der KI-Code-Anteil recht gering (ca. 10%), die Businesslogik aus Aufgabe 2. wird übernommen und ist vollständig handgeschrieben. Auch hier hat die Implementierung beim ersten Versuch funktioniert und es wurde dadurch viel Arbeitszeit eingespart.

7. Wie in Aufgabe 2. wird `json_simple` für die (De-)Serialisierung der Datenmodelle und ein Bash-Skript für das Starten/Stoppen der Instanzen verwendet.

---

## Aufgabe 4 – CQRS & Event Sourcing

1. Die Architektur dieser Aufgabe hält sich sehr eng an die gezeigte Struktur aus der Vorlesung. Zunächst werden im Ordner `interfaces` die drei vorgegebenen Interfaces angelegt. Anschließend werden folgende Klassen angelegt, die jeweils eines der Interfaces implementieren:

- `VehicleCommandHandler` -> `VehicleCommands`
- `VehicleRepository` -> `Query`
- `models/VehicleInfo` -> `VehicleDTO`

Zusätzlich werden folgende Datenklassen erstellt:

- `models/commands/VehicleCommand`
- `models/commands/VehicleCommandCreate`
- `models/commands/VehicleCommandMove`
- `models/commands/VehicleCommandRemove`
- `models/Position`

Die Klasse `Position` wird erstellt, indem einfach die Klasse `Vector2D` aus vorherigen Aufgaben umbenannt wird. Zudem wird sie auf eine Implementierung als `record` umgestellt. Mehr dazu später. Die abstrakte Klasse `VehicleCommand` stellt den gemeinsamen Datentyp aller Commands dar. Sie beinhaltet den Fahrzeugnamen (Jeder Command referenziert ein Fahrzeug), sowie die beiden (abstrakten) Methoden:

```
public abstract void applyToQueryModel(<Q> queryModel);
public abstract void applyToDomainModel(<D> domainModel);
```

Die Klasse ist abstrakt, weil es keinen Sinn ergibt, ein allgemeines "VehicleCommand" zu erstellen. Daher werden die Unterklassen (siehe oben) erstellt. Diese beinhalten zusätzlich zum Fahrzeugnamen noch weitere notwendige Daten, die von der vorgegebenen API abgeleitet werden. Beispielsweise beinhaltet die Klasse `VehicleCommandCreate` noch das Datum `StartPosition`. Erneut implementiert jede Klasse die from/to JSON Methoden. Ein serialisierter `VehicleCommandCreate`-Befehl sieht z.B. so aus:

```
{
   "type": "create",
   "name": "VW Golf mit Allrad",
   "startPosition": {
      "x": 50.0,
      "y": 42.0 
   }
}

```

Die beiden abstrakten Methoden sind dazu da, um jedem Befehl selbst die Wahl zu lassen, welchen Einfluss seine Ausführung auf das Domänen-/Querymodell hat. Die Typen `<Q>` und `<D>` unterscheiden sich zwischen den einzelnen Versionen, die wir implementieren mussten. Bei Version 3 sind diese Typen beispielsweise `HashMap<String, VehicleDTO>` und `HashSet<String>`. Durch diese Architektur können sehr einfach neue Befehle hinzugefügt oder bestehende Befehle angepasst werden. Sie hat jedoch den Nachteil, dass, wenn der Modelltyp von entweder Domäne oder Query geändert wird, die beiden `apply...` Methoden aller Befehle angepasst werden müssen. Eine Alternative wäre es, die Modifikation direkt im Repository bzw. im CommandHandler vorzunehmen. Dann würden diese beiden Klassen allerdings wieder "dicker". Die Validierung vor der Befehlsausführung erfolgt zentralisiert im CommandHandler bzw. im Reposiroty. Die Klassen CommandHandler und VehicleRepository sind als Singleton implementiert, damit in den späteren Versionen die Interaktion zwischen beiden vereinfacht wird. Die tatsächliche Kommunikation mit dem Kafka-Server erfolgt erneut auf den in den Aufgaben 2 & 3 entwickelten Klassen `Consumer` und `Producer`.

Die Projektion des CQRS-Modells wird über die Methode `applyToQueryModel` abgebildet. Auf dem Server sind nur die Commands gespeichert. Das Repository bildet die Projektion, indem es alle Messages des Command-Topics abruft und sukzessive über diese Methode "faltet".

Nun zu den "Fragen am Rande":

1. Warum sollte `Position` die Schnittstelle `Comparable` implementieren?
Damit man Instanzen vergleichen kann. Das ist notwendig um beispielsweise eine Position als Schlüssel in einer HashMap verwenden zu können.

1. `Position` ist ein „reines“ Datenobjekt – würde das nicht für eine `record`-Umsetzung sprechen?
Ja.

1. Was spricht dagegen?
Wir haben nix gefunden und das Ganze als record implementiert. Funktioniert super. Ein potentieller Grund wäre, wenn die Daten in-Place mutiert werden müssten.

### Version 2:

Für die Version 2 des Systems wird das Domänenmodell aus der Write-Seite entfernt und der CommandHandler bekommt eine Referenz auf das Repository, um den bisher bekannten Zustand abzufragen. Beim Starten des Repositories zieht sich die Instanz einmalig alle bisherigen Nachrichten und hydriert damit das QueryModell. Weitere Messages werden auf diesen Zustand "addiert", indem erneut die Methode `applyToQueryModel` verwendet wird. (Im Callback des Consumers)

### Version 3:

1. Wird die Funktion `moveVehicle` des CommandHandlers aufgerufen, fragt dieser über das Repository ab, wie viele Bewegungen bereits für ein Fahrzeug bekannt sind. Wenn diese Zahl > 5 ist, wird anstelle eine `move`-Befehls einfach ein `remove`-Befehl an den MessageBroker geschickt.

2. Für diese Umsetzung muss die gesamte Historie der Position eines Fahrzeugs bekannt sein. Dazu wird das Interface `VehicleDTO` um folgende Funktion erweitert: `public List<Position> getPreviousPositions();`. (Das Erweitern der Schnittstelle ist nicht explizit verboten, es sind "nur Getter" erlaubt.). Anschließend wird die Implementierung `VehicleInfo` so modifiziert, dass sie anstatt einer einzelnen Position eine Liste mitführt. Die zuletzt eingefügte Position ist die aktuelle (für `getPosition`). Die Funktion `getNumberOfMoves` gibt nun einfach die Länge der Liste (-1) zurück. Entsprechend wird die Implementierung von `VehicleCommandMove` angepasst. Nun kann im Repository die gewünschte Abfrage `getPreviousPositions` hinzugefügt werden. Damit kann der CommandHandler vor dem Ausführen eines `move`-Befehls prüfen, ob die Bewegung "legal" ist und erneut bei Bedarf einen `remove`-Befehl absetzen.

3. Hierfür sucht der CommandHandler alle Fahrzeuge an der neuen Position des bewegten Fahrzeugs (mit kleinem Umkreis wegen Float-Präzisionsfehlern). Für jedes dieser Fahrzeuge wird ein `remove`-Befehl abgesetzt. Anschließend wird das bewegte Fahrzeug tatsächlich bewegt.



### Version 4 – (Optional)

Aus Zeitgründen nicht durchgeführt.

2. Die Leistungsfähigkeit wird als "okay" eingeschätzt. Insgesamt entsteht der Eindruck, das System ist sehr overengineered, was vermutlich der simplen Anwendung zugrunde liegt. Durch die Entkopplung von Lesen und Schreiben entsteht ein Zeitverzug, welcher messbar die Ausführung einzelner Queries (also bis das Ergebnis "da" ist, nicht die eigentliche Aufrufszeit) steigert. Die Startzeit des Systems steigt proportional mit der Anzahl gespeicherter Commands, da das Repository erst alle Nachrichten verarbeiten muss. Hier könnte ein Snapshot-System deutliche Vorteile bezüglich Performanz bringen.

3. Theoretisch ist das System gut skalierbar, da die Lese- und Schreibseiten unabhängig skaliert werden können. Beim Testen ist auffällig, dass es durch die Verteilung auf mehrere Knoten zu Race Conditions kommen kann. Dadurch ist es möglich, dass zwei Knoten einen leicht veralteten Stand haben, wenn das Repository noch nicht alle neuen Nachrichten verarbeitet hat. Somit können beide einen `create`-Befehl absetzen und das gleiche Fahrzeug würde zweimal erstellt. Dies wird zwar auf der Seite des Repository durch die Implementierung von `applyToQueryModel` wieder abgefangen, erzeugt aber fehlerhafte/redundante Logs. Ebenso wurde kein Load-Balancing o.Ä. genauer betrachtet, was allerdings in einem Produktivsystem die Last einzelner Knoten deutlich senken könnte.

4. Alle Versionen wurden bereits während der Implementierung getestet. Analog zu den vorherigen Aufgaben gibt es neue Startparameter für die zentrale `App.java`-Datei. Hierbei wurden ad-hoc Tests durchgeführt. Es wird geprüft, ob Dopplungen entstehen können und ob alle Validierungen korrekt funktionieren (z.B. ob ein Fahrzeug öfter als 5 mal bewegt wurde). Zudem wurden mit ChatGPT Unit-Tests generiert, die eben diesen Vorgang automatisieren. Die Tests wurden manuell überprüft, um die Sinnhaftigkeit und Korrektheit zu bestätigen.

5. Auch hier wird eine Visualisierung mit MatplotLib erstellt. Diese zeigt ein Beispielszenario mit erfundenen Daten, die sämtliche "Extremfälle" abbilden. Jeder "Frame" zeigt den Zustand, nachdem jedes Fahrzeug entweder bewegt, erstellt oder entfernt wurde.

![Animiertes GIF, das die Fahrzeugpositionen zeigt](scripts/vehicle_collision_animation.gif)

6. ChatGPT wurde verwendet, um das Konzept von CQRS besser zu verstehen. Die Erklärungen der KI sind allerdings mehr oder weniger nützlich, da sie teilweise wiedersprüchlich sind. Dadurch wurde eher keine Zeit eingespart. Der Code für die Visualisierung der Fahrzeugbewegungen wurde KI-generiert (Sinnvolle Daten mit Extremfällen, die Simulation/Auswertung erfolgt natürlich über das implementierte CQRS-System). Der Code für die Kommunikation mit Kafka ist nach wie vor KI-generiert (Consumer/Publisher). Diese Erfahrung zeigt (wie so oft) dass die KI noch erhebliche Probleme mit komplexen Problemstellungen hat, für einfache "Boilerplate"-Aufgaben gibt es dennoch Produktivitätsgewinne (z.B. Visualisierung)

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