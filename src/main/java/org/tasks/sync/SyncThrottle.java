package org.tasks.sync;

import org.joda.time.DateTime;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import static org.tasks.date.DateTimeUtils.newDateTime;

@Singleton
public class SyncThrottle {

    private final Map<Long, DateTime> lastSync = new HashMap<>();

    @Inject
    public SyncThrottle() {
    }

    public synchronized boolean canSync(long listId) {
        DateTime now = newDateTime();
        DateTime last = lastSync.get(listId);
        lastSync.put(listId, now);
        return last == null || last.isBefore(now.minusMinutes(10));
    }
}
