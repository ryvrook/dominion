package dev.ryvrook.dominion.util;

import dev.ryvrook.dominion.text.TextClassCheck;
import org.junit.jupiter.api.Test;

class UtilTextTest {
    @Test
    void everyIdIsDefinedOnceWithItsPlaceholders() throws Exception {
        TextClassCheck.check(UtilText.class, "util", TextClassCheck.expect(
                UtilText.TIME_NOW, "",
                UtilText.TIME_DAYS_HOURS, "days hours",
                UtilText.TIME_HOURS_MINUTES, "hours minutes",
                UtilText.TIME_MINUTES_SECONDS, "minutes seconds",
                UtilText.TIME_SECONDS, "seconds",
                UtilText.TIME_AGO, "time"));
    }
}
