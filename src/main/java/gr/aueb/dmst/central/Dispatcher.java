package gr.aueb.dmst.central;

import gr.aueb.dmst.elevator.ElevatorModel;

import java.util.List;

public class Dispatcher {
    private final List<ElevatorModel> elevators;

    public Dispatcher(List<ElevatorModel> elevators) {
        this.elevators = elevators;
    }

    /**
     * Υπολογίζει το καταλληλότερο ασανσέρ με βάση την απόσταση,
     * την τρέχουσα κίνηση και την κατεύθυνση.
     */
    public ElevatorModel assignElevator(CallRequest call) {
        if (elevators == null || elevators.isEmpty()) {
            throw new IllegalStateException("Δεν υπάρχουν διαθέσιμοι ανελκυστήρες.");
        }

        ElevatorModel bestElevator = null;
        int bestScore = Integer.MAX_VALUE;

        for (ElevatorModel elevator : elevators) {
            int score = calculateSuitabilityScore(elevator, call);
            if (score < bestScore) {
                bestScore = score;
                bestElevator = elevator;
            }
        }

        return bestElevator;
    }

    /**
     * Όσο μικρότερο είναι το σκορ (κόστος), τόσο πιο κατάλληλο είναι το ασανσέρ.
     */
    private int calculateSuitabilityScore(ElevatorModel elevator, CallRequest call) {
        int currentFloor = elevator.getCurrentFloor();
        int pickupFloor = call.getSourceFloor();
        int baseDistance = Math.abs(currentFloor - pickupFloor);

        // Αν είναι αδρανές (IDLE), το κόστος είναι απλά η απόσταση
        if (!elevator.isMoving() || "IDLE".equalsIgnoreCase(elevator.getDirection())) {
            return baseDistance;
        }

        // Αν κινείται ήδη, ελέγχουμε αν η κλήση είναι καθ' οδόν στην ίδια κατεύθυνση
        boolean requestedUp = call.getDestinationFloor() > pickupFloor;
        boolean elevatorUp = "UP".equalsIgnoreCase(elevator.getDirection());

        // Περίπτωση: Ανεβαίνει και η κλήση είναι ψηλότερα με κατεύθυνση προς τα πάνω
        if (elevatorUp && requestedUp && currentFloor <= pickupFloor) {
            return baseDistance + 10; // Μικρή επιβάρυνση για τη στάση
        }

        // Περίπτωση: Κατεβαίνει και η κλήση είναι χαμηλότερα με κατεύθυνση προς τα κάτω
        if (!elevatorUp && !requestedUp && currentFloor >= pickupFloor) {
            return baseDistance + 10;
        }

        // Σε αντίθετη κατεύθυνση, επιβάλλεται ισχυρό πέναλτι για να προτιμηθεί άλλο ασανσέρ
        return baseDistance + 120;
    }
}