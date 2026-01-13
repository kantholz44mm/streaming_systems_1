mvn exec:java -Dexec.mainClass="com.krassedudes.streaming_systems.App"  -Dexec.args="--consumer_group" &
mvn exec:java -Dexec.mainClass="com.krassedudes.streaming_systems.App"  -Dexec.args="--consumer_distance" &
mvn exec:java -Dexec.mainClass="com.krassedudes.streaming_systems.App"  -Dexec.args="--consumer_summation" &
mvn exec:java -Dexec.mainClass="com.krassedudes.streaming_systems.App"  -Dexec.args="--publisher" &