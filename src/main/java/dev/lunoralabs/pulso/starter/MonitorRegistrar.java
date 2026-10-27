package dev.lunoralabs.pulso.starter;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringUtils;

/**
 * Finds the {@code @Monitored @Scheduled} methods while beans are created and registers them in
 * Pulso with the schedule Spring will run them on.
 *
 * <p>Registration is queued once all beans exist, before Spring starts running scheduled tasks:
 * the client sends in order, so a monitor is always created before its first ping arrives.
 */
public class MonitorRegistrar implements BeanPostProcessor, SmartInitializingSingleton {

    private static final Log log = LogFactory.getLog(MonitorRegistrar.class);

    private final ObjectProvider<PulsoClient> client;
    private final Environment environment;
    private final List<MonitorRegistration> found = new ArrayList<>();

    public MonitorRegistrar(ObjectProvider<PulsoClient> client, Environment environment) {
        this.client = client;
        this.environment = environment;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        Class<?> type = AopUtils.getTargetClass(bean);
        Map<Method, Monitored> methods = MethodIntrospector.selectMethods(type,
                (MethodIntrospector.MetadataLookup<Monitored>) method ->
                        AnnotatedElementUtils.findMergedAnnotation(method, Monitored.class));
        methods.forEach((method, monitored) -> {
            Scheduled scheduled = AnnotatedElementUtils.findMergedAnnotation(method, Scheduled.class);
            if (scheduled == null) {
                return; // Not scheduled by Spring: the monitor has to exist in Pulso already
            }
            MonitorRegistration registration = toRegistration(type, method, monitored, scheduled);
            if (registration != null) {
                synchronized (found) {
                    found.add(registration);
                }
            }
        });
        return bean;
    }

    @Override
    public void afterSingletonsInstantiated() {
        PulsoClient pulso = client.getIfAvailable();
        if (pulso == null) {
            return;
        }
        synchronized (found) {
            found.forEach(pulso::register);
            found.clear();
        }
    }

    MonitorRegistration toRegistration(Class<?> type, Method method, Monitored monitored, Scheduled scheduled) {
        String slug = monitored.value();
        String name = StringUtils.hasText(monitored.name())
                ? monitored.name()
                : type.getSimpleName() + "." + method.getName();
        Duration grace = parseGrace(slug, monitored.grace());

        String cron = environment.resolvePlaceholders(scheduled.cron());
        if (StringUtils.hasText(cron)) {
            if (Scheduled.CRON_DISABLED.equals(cron)) {
                return null;
            }
            String zone = environment.resolvePlaceholders(scheduled.zone());
            return new MonitorRegistration(slug, name, cron, StringUtils.hasText(zone) ? zone : null, null, grace);
        }

        Duration period = period(scheduled);
        if (period == null) {
            log.info("Pulso: can't tell how often " + name + " runs; create monitor '" + slug + "' in the dashboard");
            return null;
        }
        return new MonitorRegistration(slug, name, null, null, period, grace);
    }

    private Duration period(Scheduled scheduled) {
        if (scheduled.fixedRate() > 0) {
            return Duration.of(scheduled.fixedRate(), scheduled.timeUnit().toChronoUnit());
        }
        if (scheduled.fixedDelay() > 0) {
            return Duration.of(scheduled.fixedDelay(), scheduled.timeUnit().toChronoUnit());
        }
        String text = StringUtils.hasText(scheduled.fixedRateString())
                ? scheduled.fixedRateString()
                : scheduled.fixedDelayString();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String resolved = environment.resolvePlaceholders(text).trim();
        try {
            return resolved.startsWith("P") || resolved.startsWith("p")
                    ? Duration.parse(resolved)
                    : Duration.of(Long.parseLong(resolved), scheduled.timeUnit().toChronoUnit());
        } catch (DateTimeParseException | NumberFormatException e) {
            return null;
        }
    }

    private static Duration parseGrace(String slug, String grace) {
        if (!StringUtils.hasText(grace)) {
            return null;
        }
        try {
            return Duration.parse(grace);
        } catch (DateTimeParseException e) {
            log.warn("Pulso: grace '" + grace + "' of monitor '" + slug + "' is not an ISO-8601 duration (e.g. PT30M); ignored");
            return null;
        }
    }
}
