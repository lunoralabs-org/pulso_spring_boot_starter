package dev.lunoralabs.pulso.starter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Talks to Pulso in the background. Calls never block the job and never throw: if Pulso is slow or
 * down, the ping is logged and dropped.
 *
 * <p>One background thread, so a job's start ping always reaches Pulso before its end ping. On
 * shutdown, pending pings get a few seconds to be sent.
 */
public class PulsoClient implements AutoCloseable {

    static final String API_KEY_HEADER = "X-Api-Key";
    private static final int MAX_PENDING = 1000;
    private static final int MAX_FAILURE_BODY = 4000;
    private static final Log log = LogFactory.getLog(PulsoClient.class);

    private final RestClient http;
    private final ThreadPoolExecutor executor;

    public PulsoClient(PulsoProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build());
        factory.setReadTimeout(properties.getReadTimeout());
        this.http = RestClient.builder()
                .baseUrl(properties.getBaseUrl().replaceAll("/+$", ""))
                .defaultHeader(API_KEY_HEADER, properties.getApiKey())
                .requestFactory(factory)
                .build();
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(MAX_PENDING),
                runnable -> {
                    Thread thread = new Thread(runnable, "pulso-client");
                    thread.setDaemon(true);
                    return thread;
                },
                (runnable, pool) -> log.warn("Pulso: too many pending pings, dropping one"));
    }

    public void start(String slug) {
        post("/api/v1/ping/{slug}/start", slug, null);
    }

    /** @param duration how long the run took, measured here: more exact than what Pulso sees */
    public void success(String slug, Duration duration) {
        post("/api/v1/ping/{slug}?durationMs=" + duration.toMillis(), slug, null);
    }

    /** Reports a failed run, with the exception and its stack trace as the log Pulso shows. */
    public void failure(String slug, Duration duration, Throwable error) {
        post("/api/v1/ping/{slug}/fail?durationMs=" + duration.toMillis(), slug, describe(error));
    }

    public void register(MonitorRegistration registration) {
        submit("register " + registration.slug(), () -> http.post()
                .uri("/api/v1/monitors")
                .contentType(MediaType.APPLICATION_JSON)
                .body(registration.toJson())
                .retrieve()
                .toBodilessEntity());
    }

    private void post(String path, String slug, String body) {
        submit(path.replace("{slug}", slug).replaceAll("\\?.*", ""), () -> {
            RestClient.RequestBodySpec request = http.post().uri(path, slug);
            if (body != null) {
                request.contentType(MediaType.TEXT_PLAIN).body(body);
            }
            request.retrieve().toBodilessEntity();
        });
    }

    private void submit(String what, Runnable call) {
        executor.execute(() -> {
            try {
                call.run();
            } catch (RuntimeException e) {
                // Never the job's problem: Pulso being unreachable must not break the application
                log.warn("Pulso: could not " + what + ": " + e.getMessage());
            }
        });
    }

    static String describe(Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        String text = trace.toString();
        return text.length() <= MAX_FAILURE_BODY ? text : text.substring(0, MAX_FAILURE_BODY);
    }

    /** Lets pending pings go out before the application stops, for up to five seconds. */
    @Override
    public void close() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            log.warn("Pulso: " + executor.getQueue().size() + " ping(s) not sent before shutdown");
            executor.shutdownNow();
        }
    }
}
