package com.example.app.model;

/** Management reference and data points from the project plan. */
public final class Reference {

    /** Staatsbosbeheer reference: 500 deer plus 600 cattle and horses combined. */
    public static final int DEER = 500;
    public static final int CATTLE_AND_HORSES = 600;
    public static final int TOTAL = DEER + CATTLE_AND_HORSES;

    /** The plan does not split the 600; an even split is used as the default starting point. */
    public static final PopulationState REFERENCE_START = new PopulationState(500, 300, 300, 0);

    /** Teaching-data counts on 1 January 2025: 770 deer, 380 cattle, 420 horses. */
    public static final PopulationState COUNTS_2025 = new PopulationState(770, 380, 420, 0);

    private Reference() {
    }
}
