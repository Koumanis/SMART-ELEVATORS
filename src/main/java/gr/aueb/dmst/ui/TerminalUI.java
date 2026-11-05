package gr.aueb.dmst.ui;

import java.io.File;

public class TerminalUI {

    private static final String DASHBOARD_PATH = "src/main/resources/web/index.html";
    private static final String SERVER_URL = "http://localhost:8080/";

    public static void main(String[] args) {
        printBanner();

        System.out.println("🚀 Εκκίνηση του Smart Elevators Web Dashboard...");
        System.out.println("🔗 Web Console URL: " + SERVER_URL);
        System.out.println("📂 Local Path: " + new File(DASHBOARD_PATH).getAbsolutePath());
        System.out.println("\nΑνοίγει αυτόματα στον browser σας...");

        launchBrowser();

        System.out.println("\n[Συμβουλή] Κρατήστε το παράθυρο του RestServer ανοιχτό για να λαμβάνει τηλεμετρία.");
    }

    private static void printBanner() {
        System.out.println("=======================================================");
        System.out.println("       🏢 SKYLINE TOWER — UI LAUNCHER SERVICE          ");
        System.out.println("=======================================================");
    }

    private static void launchBrowser() {
        try {
            String os = System.getProperty("os.name").toLowerCase();

            if (os.contains("linux")) {
                // 1. Βρίσκουμε την απόλυτη διαδρομή στα Windows μέσω του εργαλείου wslpath
                Process pathProcess = new ProcessBuilder("wslpath", "-w", new File(DASHBOARD_PATH).getAbsolutePath()).start();
                String winPath = new String(pathProcess.getInputStream().readAllBytes()).trim();

                // 2. Εκτελούμε το start των Windows με τη σωστή διαδρομή
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "start", "", winPath);
                pb.start();
            } else if (os.contains("win")) {
                ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "start", "", new File(DASHBOARD_PATH).getAbsolutePath());
                pb.start();
            } else {
                ProcessBuilder pb = new ProcessBuilder("open", DASHBOARD_PATH);
                pb.start();
            }
        } catch (Exception e) {
            System.err.println("⚠️ Δεν ήταν δυνατή η αυτόματη εκκίνηση του browser: " + e.getMessage());
            System.out.println("👉 Μπορείτε να ανοίξετε χειροκίνητα το αρχείο: " + DASHBOARD_PATH);
        }
    }
}