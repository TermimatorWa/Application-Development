package com.example.app.model;

/** Low / central / high value of an uncertain assumption (project plan 5.2, subquestion 4). */
public enum AssumptionLevel {
    LOW("Low"),
    CENTRAL("Central"),
    HIGH("High");

    private final String label;

    AssumptionLevel(String label) {
        this.label = label;
    }

    public <T> T pick(T low, T central, T high) {
        return switch (this) {
            case LOW -> low;
            case CENTRAL -> central;
            case HIGH -> high;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
