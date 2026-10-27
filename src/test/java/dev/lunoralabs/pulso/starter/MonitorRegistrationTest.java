package dev.lunoralabs.pulso.starter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MonitorRegistrationTest {

    @Test
    void escapesNamesSoTheJsonStaysValid() {
        MonitorRegistration registration = new MonitorRegistration(
                "report", "Report \"daily\"\\ok\n", null, null, Duration.ofSeconds(90), null);

        assertThat(registration.toJson()).isEqualTo(
                "{\"slug\":\"report\",\"name\":\"Report \\\"daily\\\"\\\\ok\\n\",\"scheduleType\":\"PERIOD\",\"periodSeconds\":90}");
    }

    @Test
    void subSecondPeriodsAreRoundedUpToOneSecond() {
        assertThat(new MonitorRegistration("fast", "fast", null, null, Duration.ofMillis(200), null).toJson())
                .contains("\"periodSeconds\":1");
    }
}
