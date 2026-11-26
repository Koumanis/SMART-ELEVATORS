#!/usr/bin/env bash
set -e

echo "1/3 Μεταγλώττιση του project..."
mvn -q compile

echo "2/3 Εκκίνηση του RestServer στο παρασκήνιο..."
mkdir -p target
mvn -q exec:java -Dexec.mainClass="gr.aueb.dmst.central.RestServer" > target/server.log 2>&1 &
SERVER_PID=$!

# Κλείνει αυτόματα τον server μόλις τελειώσει το EvaluationSystem (ή με Ctrl+C)
trap "kill $SERVER_PID 2>/dev/null || true" EXIT

# Περιμένει μέχρι να ανοίξει το port 8080 (έως 15 δευτερόλεπτα)
for i in {1..30}; do
    if curl -s http://localhost:8080/api/status > /dev/null 2>&1; then
        break
    fi
    sleep 0.5
done

echo "3/3 Εκτέλεση του EvaluationSystem..."
mvn -q exec:java -Dexec.mainClass="gr.aueb.dmst.evaluation.EvaluationSystem"