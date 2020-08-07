package org.kohsuke.github;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.TimeZone;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.fail;

/**
 * Unit test for {@link GitHub} static helpers.
 *
 * @author Liam Newman
 */
public class GitHubStaticTest extends AbstractGitHubWireMockTest {

    @Test
    public void timeRoundTrip() throws Exception {
        final long stableInstantEpochMilli = 1533721222255L;
        Instant instantNow = Instant.ofEpochMilli(stableInstantEpochMilli);

        Date instantSeconds = Date.from(instantNow.truncatedTo(ChronoUnit.SECONDS));
        Date instantMillis = Date.from(instantNow.truncatedTo(ChronoUnit.MILLIS));

        String instantFormatSlash = formatZonedDate(instantMillis, "yyyy/MM/dd HH:mm:ss ZZZZ", "PST");
        assertThat(instantFormatSlash, equalTo("2018/08/08 02:40:22 -0700"));

        String instantFormatDash = formatDate(instantMillis, "yyyy-MM-dd'T'HH:mm:ss'Z'");
        assertThat(instantFormatDash, equalTo("2018-08-08T09:40:22Z"));

        String instantFormatMillis = formatDate(instantMillis, "yyyy-MM-dd'T'HH:mm:ss.S'Z'");
        assertThat(instantFormatMillis, equalTo("2018-08-08T09:40:22.255Z"));

        String instantFormatMillisZoned = formatZonedDate(instantMillis, "yyyy-MM-dd'T'HH:mm:ss.SXXX", "PST");
        assertThat(instantFormatMillisZoned, equalTo("2018-08-08T02:40:22.255-07:00"));

        String instantSecondsFormatMillis = formatDate(instantSeconds, "yyyy-MM-dd'T'HH:mm:ss.S'Z'");
        assertThat(instantSecondsFormatMillis, equalTo("2018-08-08T09:40:22.0Z"));

        String instantSecondsFormatMillisZoned = formatZonedDate(instantSeconds, "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "PST");
        assertThat(instantSecondsFormatMillisZoned, equalTo("2018-08-08T02:40:22.000-07:00"));

        String instantBadFormat = formatDate(instantMillis, "yy-MM-dd'T'HH:mm'Z'");
        assertThat(instantBadFormat, equalTo("18-08-08T09:40Z"));

        assertThat(GitHubClient.parseDate(GitHubClient.printDate(instantSeconds)),
                equalTo(GitHubClient.parseDate(GitHubClient.printDate(instantMillis))));
        assertThat(GitHubClient.printDate(instantSeconds), equalTo("2018-08-08T09:40:22Z"));
        assertThat(GitHubClient.printDate(GitHubClient.parseDate(instantFormatMillisZoned)),
                equalTo("2018-08-08T09:40:22Z"));

        assertThat(instantSeconds, equalTo(GitHubClient.parseDate(GitHubClient.printDate(instantSeconds))));

        // printDate will truncate to the nearest second, so it should not be equal
        assertThat(instantMillis, not(equalTo(GitHubClient.parseDate(GitHubClient.printDate(instantMillis)))));

        assertThat(instantSeconds, equalTo(GitHubClient.parseDate(instantFormatSlash)));

        assertThat(instantSeconds, equalTo(GitHubClient.parseDate(instantFormatDash)));

        // This parser does not truncate to the nearest second, so it will be equal
        assertThat(instantMillis, equalTo(GitHubClient.parseDate(instantFormatMillis)));
        assertThat(instantMillis, equalTo(GitHubClient.parseDate(instantFormatMillisZoned)));

        assertThat(instantSeconds, equalTo(GitHubClient.parseDate(instantSecondsFormatMillis)));
        assertThat(instantSeconds, equalTo(GitHubClient.parseDate(instantSecondsFormatMillisZoned)));

        try {
            GitHubClient.parseDate(instantBadFormat);
            fail("Bad time format should throw.");
        } catch (DateTimeParseException e) {
            assertThat(e.getMessage(), equalTo("Text '" + instantBadFormat + "' could not be parsed at index 0"));
        }
    }

    @Test
    public void testGitHubRateLimitShouldReplaceRateLimit() throws Exception {

        GHRateLimit.UnknownLimitRecord.reset();
        GHRateLimit.UnknownLimitRecord.unknownLimitResetSeconds = 5;

        GHRateLimit.Record unknown0 = GHRateLimit.UnknownLimitRecord.current();

        Thread.sleep(1500);
        GHRateLimit.UnknownLimitRecord.reset();
        GHRateLimit.UnknownLimitRecord.unknownLimitResetSeconds = 5;

        // For testing, we create an new unknown.
        GHRateLimit.Record unknown1 = GHRateLimit.UnknownLimitRecord.current();

        assertThat("Valid unknown should not replace an existing one, regardless of created or reset time",
                unknown1.currentOrUpdated(unknown0),
                sameInstance(unknown1));
        assertThat("Valid unknown should not replace an existing one, regardless of created or reset time",
                unknown0.currentOrUpdated(unknown1),
                sameInstance(unknown0));

        // Sleep to make different created time
        Thread.sleep(1500);

        // To reduce object creation: There is only one valid Unknown record at a time.
        assertThat("Unknown current should should limit the creation of new unknown records",
                unknown1,
                sameInstance(GHRateLimit.UnknownLimitRecord.current()));

        long epochSeconds = Instant.now().getEpochSecond();

        GHRateLimit.Record record0 = new GHRateLimit.Record(10, 10, epochSeconds + 10L);
        GHRateLimit.Record record1 = new GHRateLimit.Record(10, 9, epochSeconds + 10L);
        GHRateLimit.Record record2 = new GHRateLimit.Record(10, 2, epochSeconds + 10L);
        GHRateLimit.Record record3 = new GHRateLimit.Record(10, 10, epochSeconds + 20L);
        GHRateLimit.Record record4 = new GHRateLimit.Record(10, 5, epochSeconds + 20L);
        GHRateLimit.Record recordExpired0 = new GHRateLimit.Record(10, 10, epochSeconds - 1L);
        GHRateLimit.Record recordExpired1 = new GHRateLimit.Record(10, 10, epochSeconds + 2L);

        // Sleep to make expired and different created time
        Thread.sleep(4000);

        GHRateLimit.Record recordWorst = new GHRateLimit.Record(Integer.MAX_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE);
        GHRateLimit.Record record00 = new GHRateLimit.Record(10, 10, epochSeconds + 10L);

        GHRateLimit.Record unknownExpired0 = unknown0;
        GHRateLimit.Record unknownExpired1 = unknown1;
        unknown0 = GHRateLimit.UnknownLimitRecord.current();

        // Rate-limit records maybe created and returned in different orders.
        // We should update to the unexpired regular records over unknowns.
        // After that, we should update to the candidate if its limit is lower or its reset is later.

        assertThat("Expired unknowns should not replace another expired one, regardless of created or reset time",
                unknownExpired0.currentOrUpdated(unknownExpired1),
                sameInstance(unknownExpired0));
        assertThat("Expired unknowns should not replace another expired one, regardless of created or reset time",
                unknownExpired1.currentOrUpdated(unknownExpired0),
                sameInstance(unknownExpired1));

        assertThat("Expired unknown should not be replaced by expired earlier normal record",
                unknownExpired0.currentOrUpdated(recordExpired0),
                sameInstance(unknownExpired0));
        assertThat("Expired normal record should not be replaced an expired earlier unknown record",
                recordExpired0.currentOrUpdated(unknownExpired0),
                sameInstance(recordExpired0));

        assertThat("Expired unknown should be replaced by expired later normal record",
                unknownExpired0.currentOrUpdated(recordExpired1),
                sameInstance(recordExpired1));
        assertThat(
                "Expired later normal record should not be replaced an expired unknown record, regardless of created or reset time",
                recordExpired1.currentOrUpdated(unknownExpired0),
                sameInstance(recordExpired1));

        assertThat("Valid unknown should not be replaced by an expired unknown",
                unknown0.currentOrUpdated(unknownExpired0),
                sameInstance(unknown0));
        assertThat("Expired unknown should be replaced by valid unknown",
                unknownExpired0.currentOrUpdated(unknown0),
                sameInstance(unknown0));

        assertThat("Valid unknown should replace an expired normal record",
                recordExpired1.currentOrUpdated(unknown0),
                sameInstance(unknown0));
        assertThat("Valid unknown record should not be replaced by expired normal record",
                unknown0.currentOrUpdated(recordExpired1),
                sameInstance(unknown0));

        // In normal comparision, expiration doesn't matter
        assertThat("Expired normal should not be replaced by an earlier expired one",
                recordExpired1.currentOrUpdated(recordExpired0),
                sameInstance(recordExpired1));
        assertThat("Expired normal should be replaced by a later expired one",
                recordExpired0.currentOrUpdated(recordExpired1),
                sameInstance(recordExpired1));

        assertThat("Later worst record should be replaced by earlier record",
                recordWorst.currentOrUpdated(record0),
                sameInstance(record0));
        assertThat("Later worst record should not replace earlier",
                record0.currentOrUpdated(recordWorst),
                sameInstance(record0));

        assertThat("Equivalent record should not replace other",
                record00.currentOrUpdated(record0),
                sameInstance(record00));
        assertThat("Equivalent record should not replace other",
                record0.currentOrUpdated(record00),
                sameInstance(record0));

        assertThat("Higher limit record should be replaced by lower",
                record0.currentOrUpdated(record1),
                sameInstance(record1));
        assertThat("Higher limit record should be replaced by lower",
                record1.currentOrUpdated(record2),
                sameInstance(record2));

        assertThat("Lower limit record should not be replaced higher",
                record2.currentOrUpdated(record1),
                sameInstance(record2));

        assertThat("Lower limit record should be replaced by higher limit record with later reset",
                record2.currentOrUpdated(record3),
                sameInstance(record3));

        assertThat("Higher limit record should be replaced by lower limit record with later reset",
                record1.currentOrUpdated(record4),
                sameInstance(record4));

        assertThat("Higher limit record should not be replaced by lower limit record with earlier reset",
                record4.currentOrUpdated(record2),
                sameInstance(record4));

    }

    @Test
    public void testMappingReaderWriter() throws Exception {

        // This test ensures that data objects can be written and read in a raw form from string.
        // This behavior is completely unsupported and should not be used but given that some
        // clients, such as Jenkins Blue Ocean, have already implemented their own Jackson
        // Reader and Writer that bind this library's data objects from outside this library
        // this makes sure they don't break.

        GHRepository repo = getTempRepository();
        assertThat(repo.root, not(nullValue()));

        String repoString = GitHub.getMappingObjectWriter().writeValueAsString(repo);
        assertThat(repoString, not(nullValue()));
        assertThat(repoString, containsString("testMappingReaderWriter"));

        GHRepository readRepo = GitHub.getMappingObjectReader().forType(GHRepository.class).readValue(repoString);

        // This should never happen if these methods aren't used
        assertThat(readRepo.root, nullValue());

        String readRepoString = GitHub.getMappingObjectWriter().writeValueAsString(readRepo);
        assertThat(readRepoString, equalTo(repoString));

    }

    static String formatDate(Date dt, String format) {
        return formatZonedDate(dt, format, "GMT");
    }

    static String formatZonedDate(Date dt, String format, String timeZone) {
        SimpleDateFormat df = new SimpleDateFormat(format);
        df.setTimeZone(TimeZone.getTimeZone(timeZone));
        return df.format(dt);
    }

}
