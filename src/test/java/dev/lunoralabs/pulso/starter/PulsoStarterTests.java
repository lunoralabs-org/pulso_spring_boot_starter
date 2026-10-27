package dev.lunoralabs.pulso.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/** Runs a real Spring Boot application with the starter, against a fake Pulso. */
class PulsoStarterTests {

    private static final String KEY = "pulso_test-key";

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class App {

        @Bean
        Jobs jobs() {
            return new Jobs();
        }
    }

    private FakePulso pulso;
    private ConfigurableApplicationContext app;

    @BeforeEach
    void startFakePulso() throws Exception {
        pulso = new FakePulso();
    }

    @AfterEach
    void stop() {
        if (app != null) {
            app.close();
        }
        pulso.close();
    }

    @Test
    void aSuccessfulRunSendsStartThenSuccessAndKeepsTheResult() throws Exception {
        start("pulso.auto-register=false");

        assertThat(app.getBean(Jobs.class).backup()).isEqualTo("1.2 GB");

        FakePulso.Request start = pulso.next();
        assertThat(start.method()).isEqualTo("POST");
        assertThat(start.path()).isEqualTo("/api/v1/ping/nightly-backup/start");
        assertThat(start.apiKey()).isEqualTo(KEY);
        FakePulso.Request success = pulso.next();
        assertThat(success.path()).isEqualTo("/api/v1/ping/nightly-backup");
        assertThat(success.query()).matches("durationMs=\\d+");
    }

    @Test
    void aFailedRunSendsTheFailureWithItsStackTraceAndStillThrows() throws Exception {
        start("pulso.auto-register=false");

        assertThatThrownBy(() -> app.getBean(Jobs.class).drainQueue())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("queue is locked");

        assertThat(pulso.next().path()).isEqualTo("/api/v1/ping/queue-drain/start");
        FakePulso.Request fail = pulso.next();
        assertThat(fail.path()).isEqualTo("/api/v1/ping/queue-drain/fail");
        assertThat(fail.query()).startsWith("durationMs=");
        assertThat(fail.contentType()).startsWith("text/plain");
        assertThat(fail.body()).contains("java.lang.IllegalStateException: queue is locked", "at dev.lunoralabs");
    }

    @Test
    void whenPulsoIsDownTheJobRunsAsIfNothingHappened() {
        pulso.close();
        start("pulso.auto-register=false");

        long started = System.nanoTime();
        assertThat(app.getBean(Jobs.class).backup()).isEqualTo("1.2 GB");
        assertThatThrownBy(() -> app.getBean(Jobs.class).drainQueue()).hasMessage("queue is locked");

        // Pings go out in the background: the job never waits for Pulso
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(500);
    }

    @Test
    void scheduledMethodsAreRegisteredAtStartupWithTheirSchedule() throws Exception {
        start("jobs.report.cron=0 30 6 * * MON-FRI");

        List<String> bodies = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            FakePulso.Request request = pulso.next();
            assertThat(request.path()).isEqualTo("/api/v1/monitors");
            assertThat(request.contentType()).startsWith("application/json");
            bodies.add(request.body());
        }

        assertThat(bodies).containsExactlyInAnyOrder(
                """
                {"slug":"nightly-backup","name":"Nightly backup","scheduleType":"CRON","cron":"0 0 3 * * *",\
                "timezone":"America/Mexico_City","graceSeconds":1800}""",
                """
                {"slug":"queue-drain","name":"Jobs.drainQueue","scheduleType":"PERIOD","periodSeconds":300}""",
                """
                {"slug":"cache-refresh","name":"Jobs.refreshCache","scheduleType":"PERIOD","periodSeconds":600}""",
                """
                {"slug":"daily-report","name":"Jobs.report","scheduleType":"CRON","cron":"0 30 6 * * MON-FRI"}""");
        // Disabled crons and methods Spring doesn't schedule are left out
        assertThat(pulso.receivesWithin(300)).isFalse();
    }

    @Test
    void monitorsAreRegisteredBeforeTheirFirstRun() throws Exception {
        // Spring may run a fixedRate job right away: its first pings must find the monitor created
        start("jobs.report.cron=-");
        app.getBean(Jobs.class).backup();

        List<String> paths = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            paths.add(pulso.next().path());
        }
        assertThat(paths.subList(0, 3)).containsOnly("/api/v1/monitors");
        assertThat(paths.subList(3, 5)).containsExactly(
                "/api/v1/ping/nightly-backup/start", "/api/v1/ping/nightly-backup");
    }

    @Test
    void withoutAnApiKeyTheStarterStaysOffAndJobsStillRun() throws Exception {
        app = new SpringApplicationBuilder(App.class).web(WebApplicationType.NONE)
                .properties("pulso.base-url=" + pulso.url(), "jobs.report.cron=-")
                .run();

        assertThat(app.getBeansOfType(PulsoClient.class)).isEmpty();
        assertThat(app.getBean(Jobs.class).backup()).isEqualTo("1.2 GB");
        assertThat(pulso.receivesWithin(300)).isFalse();
    }

    @Test
    void canBeTurnedOff() throws Exception {
        start("pulso.enabled=false");

        assertThat(app.getBeansOfType(PulsoClient.class)).isEmpty();
        app.getBean(Jobs.class).backup();
        assertThat(pulso.receivesWithin(300)).isFalse();
    }

    @Test
    void pendingPingsAreSentBeforeTheApplicationStops() throws Exception {
        start("pulso.auto-register=false");
        app.getBean(Jobs.class).backup();

        app.close();
        app = null;

        assertThat(pulso.next().path()).endsWith("/start");
        assertThat(pulso.next().path()).isEqualTo("/api/v1/ping/nightly-backup");
    }

    private void start(String... properties) {
        List<String> all = new ArrayList<>(List.of("pulso.api-key=" + KEY, "pulso.base-url=" + pulso.url(),
                "jobs.report.cron=-"));
        all.addAll(List.of(properties));
        app = new SpringApplicationBuilder(App.class).web(WebApplicationType.NONE)
                .properties(all.toArray(String[]::new))
                .run();
    }
}
