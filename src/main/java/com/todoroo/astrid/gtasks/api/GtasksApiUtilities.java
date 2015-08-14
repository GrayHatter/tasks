/**
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.gtasks.api;

import com.google.api.client.util.DateTime;

import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TimeZone;

public class GtasksApiUtilities {

    private static final Logger log = LoggerFactory.getLogger(GtasksApiUtilities.class);

    public static DateTime unixTimeToGtasksCompletionTime(long time) {
        if (time < 0) {
            return null;
        }
        return new DateTime(time);
    }

    public static long gtasksCompletedTimeToUnixTime(DateTime gtasksCompletedTime) {
        if (gtasksCompletedTime == null) {
            return 0;
        }
        return gtasksCompletedTime.getValue();
    }

    /**
     * Google deals only in dates for due times, so on the server side they normalize to utc time
     * and then truncate h:m:s to 0. This can lead to a loss of date information for
     * us, so we adjust here by doing the normalizing/truncating ourselves and
     * then correcting the date we get back in a similar way.
     */
    public static DateTime unixTimeToGtasksDueDate(long time) {
        if (time < 0) {
            return null;
        }
        org.joda.time.DateTime dateTime = new LocalDateTime(time).withMillisOfDay(0).toDateTime(DateTimeZone.UTC);
        return new DateTime(dateTime.toDate(), TimeZone.getTimeZone("UTC"));
    }

    //Adjust for google's rounding
    public static long gtasksDueTimeToUnixTime(DateTime gtasksDueTime) {
        if (gtasksDueTime == null) {
            return 0;
        }
        try {
            org.joda.time.DateTime dateTime = new org.joda.time.DateTime(gtasksDueTime.getValue(), DateTimeZone.UTC);
            return dateTime.toLocalDateTime().toDateTime().getMillis();
        } catch (NumberFormatException e) {
            log.error(e.getMessage(), e);
            return 0;
        }
    }
}
