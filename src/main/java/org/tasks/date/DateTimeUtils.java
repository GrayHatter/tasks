package org.tasks.date;

import org.joda.time.DateTime;

public class DateTimeUtils {

    public static long currentTimeMillis() {
        return org.joda.time.DateTimeUtils.currentTimeMillis();
    }

    public static DateTime newDateTime() {
        return new DateTime();
    }

    public static DateTime newDateTime(long timestamp) {
        return new DateTime(timestamp);
    }

    public static DateTime newDateTime(int year, int month, int day, int hour, int minute) {
        return new DateTime(year, month, day, hour, minute);
    }

    public static String printTimestamp(long timestamp) {
        return new DateTime(timestamp).toString();
    }
}
