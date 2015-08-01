package org.tasks.scheduling;

import android.app.PendingIntent;
import android.content.Context;

import org.joda.time.DateTime;
import org.tasks.injection.ForApplication;

import java.util.Calendar;

import javax.inject.Inject;

public class AlarmManager {

    private final android.app.AlarmManager alarmManager;

    @Inject
    public AlarmManager(@ForApplication Context context) {
        alarmManager = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    public void cancel(PendingIntent pendingIntent) {
        alarmManager.cancel(pendingIntent);
    }

    public void wakeup(long time, PendingIntent pendingIntent) {
        alarmManager.set(android.app.AlarmManager.RTC_WAKEUP, convertToDeviceTime(time), pendingIntent);
    }

    public void noWakeup(long time, PendingIntent pendingIntent) {
        alarmManager.set(android.app.AlarmManager.RTC, convertToDeviceTime(time), pendingIntent);
    }

    public void setInexactRepeating(long interval, PendingIntent pendingIntent) {
        alarmManager.setInexactRepeating(android.app.AlarmManager.RTC, 0, interval, pendingIntent);
    }

    private long convertToDeviceTime(long time) {
        DateTime dateTime = new DateTime(time);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, dateTime.getYear());
        calendar.set(Calendar.MONTH, dateTime.getMonthOfYear() - 1);
        calendar.set(Calendar.DAY_OF_MONTH, dateTime.getDayOfMonth());
        calendar.set(Calendar.HOUR_OF_DAY, dateTime.getHourOfDay());
        calendar.set(Calendar.MINUTE, dateTime.getMinuteOfHour());
        calendar.set(Calendar.SECOND, dateTime.getSecondOfMinute());
        calendar.set(Calendar.MILLISECOND, dateTime.getMillisOfSecond());
        return calendar.getTimeInMillis();
    }
}
