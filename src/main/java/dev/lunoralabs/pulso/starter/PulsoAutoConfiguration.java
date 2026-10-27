package dev.lunoralabs.pulso.starter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

/**
 * Active when {@code pulso.api-key} and {@code pulso.base-url} are set and {@code pulso.enabled}
 * isn't {@code false}. Otherwise {@code @Monitored} is simply ignored.
 */
@AutoConfiguration
@ConditionalOnClass(RestClient.class)
@ConditionalOnBooleanProperty(name = "pulso.enabled", matchIfMissing = true)
@ConditionalOnProperty(prefix = "pulso", name = {"api-key", "base-url"})
@EnableConfigurationProperties(PulsoProperties.class)
// Same proxy mode as Spring Boot's own AOP setup, for apps that don't have it
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class PulsoAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    PulsoClient pulsoClient(PulsoProperties properties) {
        return new PulsoClient(properties);
    }

    @Bean
    MonitoredAspect pulsoMonitoredAspect(PulsoClient client) {
        return new MonitoredAspect(client);
    }

    // Static: a BeanPostProcessor must not make this configuration class initialize early
    @Bean
    @ConditionalOnBooleanProperty(name = "pulso.auto-register", matchIfMissing = true)
    static MonitorRegistrar pulsoMonitorRegistrar(ObjectProvider<PulsoClient> client, Environment environment) {
        return new MonitorRegistrar(client, environment);
    }
}
