package com.spendos.common.util;

/** CSV cell helpers shared by every export. */
public final class CsvCells {

    private CsvCells() {
    }

    /** Neutralizes spreadsheet formula injection (cells starting with = + - @ are executed by Excel). */
    public static String safe(String value) {
        if (value == null) {
            return "";
        }
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }
}
