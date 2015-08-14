package org.tasks.date;

import android.test.AndroidTestCase;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.tasks.Snippet;

import static org.tasks.Freeze.freezeAt;
import static org.tasks.date.DateTimeUtils.currentTimeMillis;

public class DateTimeUtilsTest extends AndroidTestCase {

    private final DateTime now = new DateTime(2014, 1, 1, 15, 17, 53, 0);

    public void testGetCurrentTime() {
        freezeAt(now).thawAfter(new Snippet() {{
            assertEquals(now.getMillis(), currentTimeMillis());
        }});
    }

    public void testIllegalInstant() {
        new DateTime(2015, 7, 24, 0, 0, 0, 0, DateTimeZone.forID("Africa/Cairo"));
    }
}
