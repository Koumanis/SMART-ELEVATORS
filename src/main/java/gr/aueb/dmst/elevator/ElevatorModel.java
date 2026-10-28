package gr.aueb.dmst.elevator;

public class ElevatorModel {
    private final String id;
    private int currentFloor;
    private String direction; // "UP", "DOWN", "IDLE"
    private boolean doorsOpen;

    public ElevatorModel(String id) {
        this.id = id;
        this.currentFloor = 0; // Ισόγειο ως αρχικός όροφος
        this.direction = "IDLE";
        this.doorsOpen = false;
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
}