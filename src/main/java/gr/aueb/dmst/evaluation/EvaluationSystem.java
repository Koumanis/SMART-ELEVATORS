package gr.aueb.dmst.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Σύστημα αυτόματης αξιολόγησης (benchmarking) για τους ανελκυστήρες.
 * Λειτουργεί ως εξωτερικός client που στέλνει μαζικές κλήσεις στον REST Server,
 * παρακολουθεί τον στόλο μέχρι να εξυπηρετηθούν όλοι οι επιβάτες (IDLE)
 * και καταγράφει χρόνους απόκρισης, συνολικό χρόνο αποσυμφόρησης και κατανομή φόρτου.
 */
public class EvaluationSystem {

    // Προεπιλεγμένες ρυθμίσεις προσομοίωσης
    private static final String DEFAULT_SERVER_URL = "http://localhost:8080/api";
    private static final int DEFAULT_CALLS_PER_SCENARIO = 10;
    private static final int DEFAULT_MAX_FLOOR = 199;
    private static final int DEFAULT_DELAY_MS = 200;
    private static final String DEFAULT_MODE = "SUITE";

    // Ρυθμίσεις παρακολούθησης (polling) μέχρι να ολοκληρώσουν όλοι οι ανελκυστήρες
    private static final long POLL_INTERVAL_MS = 500;
    private static final long MAX_WAIT_TIMEOUT_MS = 90_000;

    private final String serverUrl;
    private final int numCalls;
    private final int maxFloor;
    private final int delayMs;
    private final String mode;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final List csvRecords;

    public EvaluationSystem(String serverUrl, int numCalls, int maxFloor, int delayMs, String mode) {
        this.serverUrl = serverUrl;
        this.numCalls = numCalls;
        this.maxFloor = maxFloor;
        this.delayMs = delayMs;
        this.mode = mode.toUpperCase();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.mapper = new ObjectMapper();
        this.csvRecords = new ArrayList();
    }

    /**
     * Κύρια ροή εκτέλεσης:
     * 1. Ελέγχει τη σύνδεση με τον server και καταγράφει πού βρίσκεται αρχικά κάθε ασανσέρ.
     * 2. Τρέχει τα επιλεγμένα σενάρια, περιμένοντας σε κάθε ένα να αδειάσουν όλοι οι ανελκυστήρες.
     * 3. Τυπώνει τον συγκεντρωτικό πίνακα και εξάγει το αρχείο CSV.
     */
    public void execute() {
        printHeader();

        // Ανάκτηση αρχικής εικόνας του στόλου πριν ξεκινήσουν οι κλήσεις
        List initialFleet = fetchElevators();
        if (initialFleet == null) {
            System.err.println("[ΣΦΑΛΜΑ] Αδυναμία σύνδεσης στον REST Server (" + serverUrl + ").");
            System.err.println("Βεβαιωθείτε ότι τρέχει πρώτα ο RestServer.");
            return;
        }

        // Αποθηκεύουμε τον αρχικό όροφο κάθε ανελκυστήρα για να υπολογίσουμε στο τέλος τη μετατόπισή του
        Map initialFloors = new LinkedHashMap();
        System.out.println("Σύνδεση επιτυχής! Αρχική κατάσταση στόλου (" + initialFleet.size() + " ανελκυστήρες):");
        for (Object obj : initialFleet) {
            Map elev = (Map) obj;
            String id = String.valueOf(elev.get("id"));
            int floor = toInt(elev.get("currentFloor"));
            initialFloors.put(id, floor);
            System.out.printf("   • Μονάδα %-2s | Αρχικός Όροφος: %3d | Κατεύθυνση: %s%n",
                    id, floor, elev.get("direction"));
        }

        // Αν το mode είναι SUITE, τρέχουμε διαδοχικά και τα 3 σενάρια αιχμής
        List scenariosToRun = new ArrayList();
        if ("SUITE".equals(mode)) {
            scenariosToRun.add("MORNING_RUSH");
            scenariosToRun.add("INTERFLOOR");
            scenariosToRun.add("EVENING_RUSH");
        } else {
            scenariosToRun.add(mode);
        }

        List results = new ArrayList();
        for (Object obj : scenariosToRun) {
            String scenario = (String) obj;
            results.add(runScenario(scenario));
        }

        // Ανάκτηση τελικής θέσης στόλου (εφόσον έχουν ολοκληρωθεί όλες οι κλήσεις)
        List finalFleet = fetchElevators();

        printExecutiveSummary(results, initialFloors, finalFleet);
        exportCsvReport();
    }

    /**
     * Εκτελεί ένα σενάριο: στέλνει τις κλήσεις, μετράει το API latency κάθε αιτήματος
     * και στη συνέχεια περιμένει μέχρι όλοι οι ανελκυστήρες να επιστρέψουν σε IDLE,
     * υπολογίζοντας τον συνολικό χρόνο αποσυμφόρησης (Clearance Time).
     */
    private ScenarioResult runScenario(String scenario) {
        System.out.println("\n-------------------------------------------------------------------------");
        System.out.printf("ΕΚΤΕΛΕΣΗ ΣΕΝΑΡΙΟΥ: %-14s (%d κλήσεις | Όροφοι 0-%d | Delay %dms)%n",
                scenario, numCalls, maxFloor, delayMs);
        System.out.println("-------------------------------------------------------------------------");

        Random random = new Random();
        int successCount = 0;
        int failCount = 0;
        long totalLatencyMs = 0;
        long minLatencyMs = Long.MAX_VALUE;
        long maxLatencyMs = 0;
        int totalDistanceFloors = 0;

        // Εκκίνηση χρονομέτρου για ολόκληρο το σενάριο
        long scenarioStartNs = System.nanoTime();

        for (int i = 1; i <= numCalls; i++) {
            int[] pair = generateTrip(scenario, random);
            int source = pair[0];
            int dest = pair[1];
            int distance = Math.abs(dest - source);
            totalDistanceFloors += distance;

            // Χρονομέτρηση του HTTP POST αιτήματος στον Dispatcher
            long reqStartNs = System.nanoTime();
            DispatchOutcome outcome = sendCall(source, dest);
            long latencyMs = (System.nanoTime() - reqStartNs) / 1_000_000;

            if (outcome.success) {
                successCount++;
                totalLatencyMs += latencyMs;
                if (latencyMs < minLatencyMs) minLatencyMs = latencyMs;
                if (latencyMs > maxLatencyMs) maxLatencyMs = latencyMs;

                String assignedInfo = outcome.assignedElevator != null
                        ? " -> Ανελκυστήρας " + outcome.assignedElevator
                        : "";
                System.out.printf("  [%02d/%02d] Όροφος %3d -> %3d (Απόσταση: %3d όρ.) | 200 OK (%2d ms)%s%n",
                        i, numCalls, source, dest, distance, latencyMs, assignedInfo);
            } else {
                failCount++;
                System.out.printf("  [%02d/%02d] Όροφος %3d -> %3d | ΑΠΟΤΥΧΙΑ%n", i, numCalls, source, dest);
            }

            // Καταγραφή της μέτρησης για την εξαγωγή του CSV
            csvRecords.add(new String[]{
                    scenario,
                    String.valueOf(i),
                    String.valueOf(source),
                    String.valueOf(dest),
                    String.valueOf(distance),
                    outcome.success ? "SUCCESS" : "FAILED",
                    String.valueOf(latencyMs),
                    outcome.assignedElevator != null ? outcome.assignedElevator : "N/A"
            });

            if (i < numCalls && delayMs > 0) {
                sleepQuietly(delayMs);
            }
        }

        // Χρόνος που χρειάστηκε μόνο για να σταλούν όλες οι κλήσεις στον server
        long dispatchDurationMs = (System.nanoTime() - scenarioStartNs) / 1_000_000;
        if (minLatencyMs == Long.MAX_VALUE) minLatencyMs = 0;

        // Αναμονή μέχρι να εξυπηρετηθούν όλες οι κλήσεις και να γίνουν όλοι οι ανελκυστήρες IDLE
        WaitOutcome waitOutcome = waitForFleetIdle(scenarioStartNs);

        return new ScenarioResult(
                scenario, numCalls, successCount, failCount,
                totalDistanceFloors, totalLatencyMs, minLatencyMs, maxLatencyMs,
                dispatchDurationMs, waitOutcome.totalCompletionMs, waitOutcome.timedOut
        );
    }

    /**
     * Ρωτάει περιοδικά τον server (κάθε 500ms) για την κατάσταση των ανελκυστήρων.
     * Σταματάει όταν όλοι οι ανελκυστήρες έχουν direction="IDLE" και moving=false,
     * επιστρέφοντας τον συνολικό χρόνο που πέρασε από την αρχή του σεναρίου.
     */
    private WaitOutcome waitForFleetIdle(long scenarioStartNs) {
        System.out.println("  >> Όλες οι κλήσεις εστάλησαν. Αναμονή ολοκλήρωσης διαδρομών στόλου...");
        sleepQuietly(300);

        int pollCount = 0;
        while (true) {
            long elapsedMs = (System.nanoTime() - scenarioStartNs) / 1_000_000;
            if (elapsedMs >= MAX_WAIT_TIMEOUT_MS) {
                System.out.printf("  [!] Όριο αναμονής (%d δευτ.) — κάποιοι ανελκυστήρες κινούνται ακόμα.%n",
                        MAX_WAIT_TIMEOUT_MS / 1000);
                return new WaitOutcome(elapsedMs, true);
            }

            List fleet = fetchElevators();
            if (fleet != null && !fleet.isEmpty()) {
                boolean allIdle = true;
                StringBuilder statusLine = new StringBuilder();

                for (int i = 0; i < fleet.size(); i++) {
                    Map elev = (Map) fleet.get(i);
                    String id = String.valueOf(elev.get("id"));
                    int floor = toInt(elev.get("currentFloor"));
                    String dir = String.valueOf(elev.get("direction"));
                    boolean moving = Boolean.TRUE.equals(elev.get("moving"));

                    // Αν έστω και ένας ανελκυστήρας κινείται ή δεν είναι IDLE, συνεχίζουμε την αναμονή
                    if (!"IDLE".equalsIgnoreCase(dir) || moving) {
                        allIdle = false;
                    }

                    if (i > 0) statusLine.append(" | ");
                    statusLine.append(String.format("%s: όρ.%3d (%-4s)", id, floor, dir));
                }

                if (allIdle) {
                    long totalCompletionMs = (System.nanoTime() - scenarioStartNs) / 1_000_000;
                    System.out.printf("  [OK] Όλος ο στόλος επέστρεψε σε IDLE! Συνολικός χρόνος: %.2f δευτ.%n",
                            totalCompletionMs / 1000.0);
                    return new WaitOutcome(totalCompletionMs, false);
                }

                // Εμφανίζουμε ζωντανή πρόοδο κάθε 2 δευτερόλεπτα (4 έλεγχοι * 500ms)
                pollCount++;
                if (pollCount % 4 == 0) {
                    System.out.printf("     [%5.1fs] %s%n", elapsedMs / 1000.0, statusLine.toString());
                }
            }

            sleepQuietly(POLL_INTERVAL_MS);
        }
    }

    /**
     * Δημιουργεί μια τυχαία διαδρομή επιβάτη ανάλογα με το σενάριο αιχμής:
     * - MORNING_RUSH: 80% πιθανότητα αναχώρησης από το ισόγειο (0) προς πάνω ορόφους.
     * - EVENING_RUSH: 80% πιθανότητα επιστροφής από πάνω ορόφους προς το ισόγειο (0).
     * - INTERFLOOR:   Μετακινήσεις μεταξύ ενδιάμεσων ορόφων.
     */
    private int[] generateTrip(String scenario, Random random) {
        if (maxFloor <= 0) {
            return new int[]{0, 1};
        }
        int source;
        int dest;

        switch (scenario) {
            case "MORNING_RUSH":
                source = (random.nextDouble() < 0.80) ? 0 : random.nextInt(maxFloor + 1);
                dest = random.nextInt(maxFloor) + 1;
                break;
            case "EVENING_RUSH":
                source = random.nextInt(maxFloor) + 1;
                dest = (random.nextDouble() < 0.80) ? 0 : random.nextInt(maxFloor + 1);
                break;
            case "INTERFLOOR":
                source = random.nextInt(maxFloor) + 1;
                dest = random.nextInt(maxFloor) + 1;
                break;
            case "RANDOM":
            default:
                source = random.nextInt(maxFloor + 1);
                dest = random.nextInt(maxFloor + 1);
                break;
        }

        while (dest == source) {
            dest = random.nextInt(maxFloor + 1);
        }
        return new int[]{source, dest};
    }

    // Ζητάει από το GET /api/elevators την τρέχουσα κατάσταση όλων των ανελκυστήρων
    private List fetchElevators() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/elevators"))
                    .GET()
                    .build();
            HttpResponse response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return mapper.readValue(String.valueOf(response.body()), List.class);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    // Στέλνει μια νέα κλήση επιβάτη σε μορφή JSON στο POST /api/calls
    private DispatchOutcome sendCall(int sourceFloor, int destinationFloor) {
        try {
            String payload = String.format("{\"sourceFloor\":%d,\"destinationFloor\":%d}",
                    sourceFloor, destinationFloor);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(serverUrl + "/calls"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean ok = response.statusCode() >= 200 && response.statusCode() < 300;
            String assigned = null;
            String bodyText = response.body() != null ? String.valueOf(response.body()) : "";

            if (ok && !bodyText.isBlank()) {
                try {
                    Map bodyMap = mapper.readValue(bodyText, Map.class);
                    Object elevObj = bodyMap.get("assignedElevatorId");
                    if (elevObj == null) {
                        elevObj = bodyMap.get("elevatorId");
                    }
                    if (elevObj == null) {
                        elevObj = bodyMap.get("id");
                    }
                    if (elevObj != null) {
                        assigned = String.valueOf(elevObj);
                    }
                } catch (Exception ignored) {
                }
            }
            return new DispatchOutcome(ok, assigned);
        } catch (Exception e) {
            return new DispatchOutcome(false, null);
        }
    }

    private void printHeader() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        System.out.println("=========================================================================");
        System.out.println("            SMART ELEVATORS — PERFORMANCE EVALUATION SYSTEM              ");
        System.out.println("=========================================================================");
        System.out.println("Ημερομηνία: " + timestamp + " | Endpoint: " + serverUrl + " | Mode: " + mode);
    }

    // Τυπώνει τον συγκεντρωτικό πίνακα συμπεριλαμβάνοντας τον συνολικό χρόνο ολοκλήρωσης ανά σενάριο
    private void printExecutiveSummary(List results, Map initialFloors, List finalFleet) {
        System.out.println("\n==================================================================================");
        System.out.println("                       ΣΥΓΚΕΝΤΡΩΤΙΚΑ ΑΠΟΤΕΛΕΣΜΑΤΑ ΑΞΙΟΛΟΓΗΣΗΣ                      ");
        System.out.println("==================================================================================");
        System.out.printf("%-14s | %-8s | %-10s | %-11s | %-12s | %-13s%n",
                "Σενάριο", "Επιτυχία", "Μ.Ο. Απόστ.", "Avg Latency", "Throughput", "Χρόνος Ολοκλ.");
        System.out.println("----------------------------------------------------------------------------------");

        int grandTotalCalls = 0;
        int grandTotalSuccess = 0;
        long grandTotalLatency = 0;
        int grandTotalFloors = 0;
        long grandTotalCompletionMs = 0;

        for (Object obj : results) {
            ScenarioResult r = (ScenarioResult) obj;
            double successPct = r.totalCalls > 0 ? (r.successCount * 100.0 / r.totalCalls) : 0.0;
            double avgDist = r.totalCalls > 0 ? (double) r.totalDistanceFloors / r.totalCalls : 0.0;
            double avgLat = r.successCount > 0 ? (double) r.totalLatencyMs / r.successCount : 0.0;
            double throughput = r.dispatchDurationMs > 0 ? (r.successCount * 1000.0) / r.dispatchDurationMs : 0.0;
            double completionSec = r.totalCompletionMs / 1000.0;
            String timeoutMark = r.timedOut ? "*" : "";

            System.out.printf("%-14s | %5.1f%%   | %6.1f όρ. | %8.2f ms | %6.2f req/s | %7.2f s%s%n",
                    r.scenario, successPct, avgDist, avgLat, throughput, completionSec, timeoutMark);

            grandTotalCalls += r.totalCalls;
            grandTotalSuccess += r.successCount;
            grandTotalLatency += r.totalLatencyMs;
            grandTotalFloors += r.totalDistanceFloors;
            grandTotalCompletionMs += r.totalCompletionMs;
        }

        System.out.println("----------------------------------------------------------------------------------");
        double overallSuccess = grandTotalCalls > 0 ? (grandTotalSuccess * 100.0 / grandTotalCalls) : 0.0;
        double overallAvgDist = grandTotalCalls > 0 ? (double) grandTotalFloors / grandTotalCalls : 0.0;
        double overallAvgLat = grandTotalSuccess > 0 ? (double) grandTotalLatency / grandTotalSuccess : 0.0;
        double grandTotalSec = grandTotalCompletionMs / 1000.0;

        System.out.printf("ΣΥΝΟΛΟ (%2d κλ.) | %5.1f%%   | %6.1f όρ. | %8.2f ms | -            | %7.2f s%n",
                grandTotalCalls, overallSuccess, overallAvgDist, overallAvgLat, grandTotalSec);

        if (finalFleet != null) {
            System.out.println("\nΤελική Κατάσταση & Μετατόπιση Στόλου Ανελκυστήρων:");
            for (Object obj : finalFleet) {
                Map elev = (Map) obj;
                String id = String.valueOf(elev.get("id"));
                int finalFloor = toInt(elev.get("currentFloor"));
                int startFloor = initialFloors.containsKey(id) ? ((Integer) initialFloors.get(id)).intValue() : 0;
                int netDisplacement = Math.abs(finalFloor - startFloor);

                System.out.printf("   • Μονάδα %-2s | Όροφος: %3d -> %3d (Μετατόπιση: %3d όρ.) | Κατεύθυνση: %-4s | Κίνηση: %s%n",
                        id, startFloor, finalFloor, netDisplacement, elev.get("direction"), elev.get("moving"));
            }
        }
        System.out.println("==================================================================================");
    }

    // Αποθηκεύει αναλυτικά κάθε κλήση σε αρχείο CSV (target/evaluation-report.csv)
    private void exportCsvReport() {
        try {
            File targetDir = new File("target");
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            File csvFile = new File(targetDir, "evaluation-report.csv");
            try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {
                writer.println("Scenario,CallIndex,SourceFloor,DestinationFloor,TripDistance,Status,ApiLatencyMs,AssignedElevator");
                for (Object obj : csvRecords) {
                    String[] row = (String[]) obj;
                    writer.println(String.join(",", row));
                }
            }
            System.out.println("Αναλυτικό αρχείο μετρήσεων εξήχθη στο: " + csvFile.getPath());
        } catch (Exception e) {
            System.err.println("Προειδοποίηση: Αδυναμία εγγραφής CSV (" + e.getMessage() + ")");
        }
    }

    private int toInt(Object val) {
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Exception e) {
            return 0;
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Βοηθητική κλάση με τα συγκεντρωτικά στατιστικά κάθε σεναρίου
    private static class ScenarioResult {
        final String scenario;
        final int totalCalls;
        final int successCount;
        final int failCount;
        final int totalDistanceFloors;
        final long totalLatencyMs;
        final long minLatencyMs;
        final long maxLatencyMs;
        final long dispatchDurationMs;
        final long totalCompletionMs;
        final boolean timedOut;

        ScenarioResult(String scenario, int totalCalls, int successCount, int failCount,
                       int totalDistanceFloors, long totalLatencyMs, long minLatencyMs,
                       long maxLatencyMs, long dispatchDurationMs, long totalCompletionMs,
                       boolean timedOut) {
            this.scenario = scenario;
            this.totalCalls = totalCalls;
            this.successCount = successCount;
            this.failCount = failCount;
            this.totalDistanceFloors = totalDistanceFloors;
            this.totalLatencyMs = totalLatencyMs;
            this.minLatencyMs = minLatencyMs;
            this.maxLatencyMs = maxLatencyMs;
            this.dispatchDurationMs = dispatchDurationMs;
            this.totalCompletionMs = totalCompletionMs;
            this.timedOut = timedOut;
        }
    }

    // Βοηθητική κλάση για το αποτέλεσμα αναμονής του στόλου
    private static class WaitOutcome {
        final long totalCompletionMs;
        final boolean timedOut;

        WaitOutcome(long totalCompletionMs, boolean timedOut) {
            this.totalCompletionMs = totalCompletionMs;
            this.timedOut = timedOut;
        }
    }

    // Βοηθητική κλάση για την απάντηση του server σε μία μεμονωμένη κλήση
    private static class DispatchOutcome {
        final boolean success;
        final String assignedElevator;

        DispatchOutcome(boolean success, String assignedElevator) {
            this.success = success;
            this.assignedElevator = assignedElevator;
        }
    }

    public static void main(String[] args) {
        int numCalls = DEFAULT_CALLS_PER_SCENARIO;
        int maxFloor = DEFAULT_MAX_FLOOR;
        int delayMs = DEFAULT_DELAY_MS;
        String mode = DEFAULT_MODE;
        String serverUrl = DEFAULT_SERVER_URL;

        try {
            if (args.length >= 1) numCalls = Integer.parseInt(args[0]);
            if (args.length >= 2) maxFloor = Integer.parseInt(args[1]);
            if (args.length >= 3) delayMs = Integer.parseInt(args[2]);
            if (args.length >= 4) mode = args[3];
            if (args.length >= 5) serverUrl = args[4];
        } catch (NumberFormatException e) {
            System.err.println("Μη έγκυρες αριθμητικές παράμετροι. Χρήση προεπιλεγμένων τιμών.");
        }

        new EvaluationSystem(serverUrl, numCalls, maxFloor, delayMs, mode).execute();
    }
}