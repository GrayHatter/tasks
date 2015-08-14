package org.tasks;

import android.content.Context;

import org.joda.time.DateTime;

public class TestUtilities {
    private static boolean mockitoInitialized;

    public static void initializeMockito(Context context) {
        if (!mockitoInitialized) {
            // for mockito: https://code.google.com/p/dexmaker/issues/detail?id=2
            System.setProperty("dexmaker.dexcache", context.getCacheDir().toString());
            mockitoInitialized = true;
        }
    }

    public static DateTime newDate(int year, int month, int day) {
        return new DateTime(year, month, day, 0, 0);
    }
}
