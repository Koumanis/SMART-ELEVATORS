package gr.aueb.dmst.elevator;

public class ElevatorModel {
    private final String id;
    private int currentFloor;
    private String direction; // "UP", "DOWN", "IDLE"
    private boolean doorsOpen;
    private boolean moving;

    // Default constructor για συμβατότητα
    public ElevatorModel(String id) {
        this(id, 0);
    }

    // Constructor με ορισμό αρχικού ορόφου
    public ElevatorModel(String id, int initialFloor) {
        this.id = id;
        this.currentFloor = initialFloor;
        this.direction = "IDLE";
        this.doorsOpen = false;
        this.moving = false;
    }

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

    // Ασύγχρονη προσομοίωση κίνησης σε ξεχωριστό Thread
    public synchronized void processTrip(int pickupFloor, int targetFloor) {
        this.moving = true;

        new Thread(() -> {
            try {
                // 1. Μετάβαση στον όροφο παραλαβής
                if (this.currentFloor != pickupFloor) {
                    moveToFloor(pickupFloor);
                }

                // Άνοιγμα θυρών στον όροφο επιβίβασης
                this.doorsOpen = true;
                this.direction = "IDLE";
                Thread.sleep(1000); // 1 δευτερόλεπτο αναμονή για επιβίβαση
                this.doorsOpen = false;

                // 2. Μετάβαση στον όροφο προορισμού
                if (this.currentFloor != targetFloor) {
                    moveToFloor(targetFloor);
                }

                // Άνοιγμα θυρών στον τελικό όροφο
                this.doorsOpen = true;
                this.direction = "IDLE";
                Thread.sleep(1000);
                this.doorsOpen = false;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                this.direction = "IDLE";
                this.moving = false;
            }
        }).start();
    }

    private void moveToFloor(int target) throws InterruptedException {
        while (this.currentFloor != target) {
            if (this.currentFloor < target) {
                this.direction = "UP";
                this.currentFloor++;
            } else {
                this.direction = "DOWN";
                this.currentFloor--;
            }
            // 45ms ανά όροφο ώστε μια διαδρομή 100 ορόφων να διαρκεί ~4.5 δευτερόλεπτα
            Thread.sleep(45);
        }
    }
}