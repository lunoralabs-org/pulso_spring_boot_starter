package dev.lunoralabs.pulso.starter;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code pulso.*} settings. Without an API key and a base URL, {@code @Monitored} does nothing. */
@ConfigurationProperties("pulso")
public class PulsoProperties {

    /** Turns the starter off, for example in development or tests. */
    private boolean enabled = true;

    /** API key from the Pulso dashboard (API page). Keep it in an environment variable. */
    private String apiKey;

    /** Address of Pulso, for example https://pulso.example.com. */
    private String baseUrl;

    /** Registers {@code @Monitored @Scheduled} methods in Pulso at startup when they don't exist yet. */
    private boolean autoRegister = true;

    /** Connection timeout for calls to Pulso. */
    private Duration connectTimeout = Duration.ofSeconds(2);

    /** Read timeout for calls to Pulso. */
    private Duration readTimeout = Duration.ofSeconds(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isAutoRegister() {
        return autoRegister;
    }

    public void setAutoRegister(boolean autoRegister) {
        this.autoRegister = autoRegister;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
