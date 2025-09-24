package net.s6103;

import java.io.Serializable;

public class ValidFacilities implements Serializable {
    static final String[] facilities = {
            "Tennis Court 1",
            "Tennis Court 2",
            "Badminton Court 1",
            "Badminton Court 2",
            "Squash Court 1",
            "Squash Court 2",
            "Table Tennis Room",
            "Gym Room",
            "Yoga Room",
            "Swimming Pool"
    };

    public static boolean isValidFacility(String facilityName) {
        for (String facility : facilities) {
            if (facility.equals(facilityName)) {
                return true;
            }
        }
        return false;
    }

}

