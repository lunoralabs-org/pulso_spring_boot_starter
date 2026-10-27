package dev.lunoralabs.pulso.starter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Reports every run of the annotated method to Pulso: a start ping when it begins, and a success
 * or failure ping when it returns or throws. The method itself is never affected: if Pulso can't
 * be reached, the job runs exactly as it would without this annotation.
 *
 * <pre>{@code
 * @Scheduled(cron = "0 0 3 * * *", zone = "America/Mexico_City")
 * @Monitored("nightly-backup")
 * public void backup() { ... }
 * }</pre>
 *
 * <p>With {@code pulso.auto-register=true} (the default), a method that also has {@code @Scheduled}
 * is registered in Pulso at startup with the same schedule, if its slug doesn't exist yet, before
 * its first run.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Monitored {

    /** The monitor's slug in Pulso: lowercase letters, numbers and hyphens. */
    String value();

    /** Display name for an auto-registered monitor. Defaults to {@code ClassName.methodName}. */
    String name() default "";

    /**
     * How long Pulso waits past the expected time before alerting, as an ISO-8601 duration such as
     * {@code PT30M}. Only used when the monitor is auto-registered; empty means Pulso's default.
     */
    String grace() default "";
}
