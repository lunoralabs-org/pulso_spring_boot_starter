package dev.lunoralabs.pulso.starter;

import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;

/** Scheduled jobs as an application would write them. Nothing runs them on a timer in the tests. */
public class Jobs {

    @Scheduled(cron = "0 0 3 * * *", zone = "America/Mexico_City")
    @Monitored(value = "nightly-backup", name = "Nightly backup", grace = "PT30M")
    public String backup() {
        return "1.2 GB";
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    @Monitored("queue-drain")
    public void drainQueue() {
        throw new IllegalStateException("queue is locked");
    }

    @Scheduled(fixedDelayString = "PT10M")
    @Monitored("cache-refresh")
    public void refreshCache() {
    }

    @Scheduled(cron = "${jobs.report.cron}")
    @Monitored("daily-report")
    public void report() {
    }

    @Scheduled(cron = Scheduled.CRON_DISABLED)
    @Monitored("switched-off")
    public void switchedOff() {
    }

    // Not scheduled by Spring (for example, triggered by a message): pinged, never auto-registered
    @Monitored("on-demand")
    public void onDemand() {
    }
}
