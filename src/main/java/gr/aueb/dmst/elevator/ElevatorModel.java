package gr.aueb.dmst.elevator;

//Κλάση ElevatorModel που αναπαριστά ένα ανελκυστήρα με βασικές λειτουργίες.
public class ElevatorModel {
    private final String id;   //Όνομα/κωδικός του ασανσέρ
    private int currentFloor;  //Τρέχων όροφος του ανελκυστήρα
    private String direction; // "UP", "DOWN", "IDLE"
    private boolean doorsOpen; // Κατάσταση θυρών (ανοιχτές/κλειστές)
    private boolean moving;  // Κατάσταση κίνησης

    // Default constructor
    public ElevatorModel(String id) {
        this(id, 0);
    }

    // Constructor με ορισμό αρχικού ορόφου
    public ElevatorModel(String id, int initialFloor) {
        this.id = id;
        this.currentFloor = initialFloor;
        // Αρχικοποίηση της κατάστασης του ανελκυστήρα
        this.direction = "IDLE";
        this.doorsOpen = false;
        this.moving = false;
    }
    // Getters και Setters για τα πεδία της κλάσης
    public String getId() {
        return id;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }
    
    public void setCurrentFloor(int currentFloor) {
        this.currentFloor = currentFloor;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public boolean areDoorsOpen() {
        return doorsOpen;
    }

    public void setDoorsOpen(boolean doorsOpen) {
        this.doorsOpen = doorsOpen;
    }

    public boolean isMoving() {
        return moving;
    }

    public void setMoving(boolean moving) {
        this.moving = moving;
    }

    // Μέθοδος για να εκτελέσει το ασανσέρ μια διαδρομή
    // Ξεκινά ένα νέο νήμα για να χειριστεί τη διαδρομή του ανελκυστήρα
    public void processTrip(int pickupFloor, int targetFloor) {
        this.moving = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Μετάβαση στον όροφο παραλαβής
                    if (currentFloor != pickupFloor) {
                        moveToFloor(pickupFloor);
                    }

                // Άνοιγμα θυρών στον όροφο επιβίβασης
                doorsOpen = true;
                direction = "IDLE";
                Thread.sleep(1000); // 1 δευτερόλεπτο αναμονή για επιβίβαση
                doorsOpen = false;

                // 2. Μετάβαση στον όροφο προορισμού
                if (currentFloor != targetFloor) {
                    moveToFloor(targetFloor);
                }

                // Άνοιγμα θυρών στον τελικό όροφο
                doorsOpen = true;
                direction = "IDLE";
                Thread.sleep(1000);
                doorsOpen = false;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                //Σε κάθε περίπτωση στο τέλος, το ασανσέρ σταματάει και οι πόρτες κλείνουν 
                direction = "IDLE";
                moving = false;
            }
        }
        }).start(); // Εκκίνηση του νήματος
    }
    //  Μέθοδος που χειρίζεται την κίνηση του ανελκυστήρα προς τον επιθυμητό όροφο
    private void moveToFloor(int target) throws InterruptedException {
        while (this.currentFloor != target) {
            if (this.currentFloor < target) {
                this.direction = "UP";
                this.currentFloor++;
            } else {
                this.direction = "DOWN";
                this.currentFloor--;
            }
            // 45ms ανά όροφο ώστε μια διαδρομή 100 ορόφων να διαρκεί περίπου 4.5 δευτερόλεπτα
            Thread.sleep(45);
        }
    }
}
