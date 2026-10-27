package dev.lunoralabs.pulso.starter;

import java.time.Duration;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/** Wraps every {@link Monitored} method with a start ping and a success or failure ping. */
@Aspect
public class MonitoredAspect {

    private final PulsoClient client;

    public MonitoredAspect(PulsoClient client) {
        this.client = client;
    }

    @Around("@annotation(monitored)")
    public Object report(ProceedingJoinPoint job, Monitored monitored) throws Throwable {
        String slug = monitored.value();
        client.start(slug);
        long started = System.nanoTime();
        Object result;
        try {
            result = job.proceed();
        } catch (Throwable error) {
            client.failure(slug, Duration.ofNanos(System.nanoTime() - started), error);
            throw error; // the job behaves exactly as without @Monitored
        }
        client.success(slug, Duration.ofNanos(System.nanoTime() - started));
        return result;
    }
}
