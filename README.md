# Pulso Spring Boot Starter

Know when your `@Scheduled` jobs stop running. Add one annotation and [Pulso](https://lunoralabs.dev/lab/pulso) alerts you (email, Slack, Telegram or webhook) when a job misses its schedule or fails.

```java
@Scheduled(cron = "0 0 3 * * *", zone = "America/Mexico_City")
@Monitored("nightly-backup")
public void backup() {
    // ...
}
```

## How it works

- Every run of a `@Monitored` method sends a **start** ping when it begins and a **success** or **failure** ping when it ends, with the run's duration. A failure includes the exception's stack trace.
- At startup, `@Monitored` methods that also have `@Scheduled` are **registered in Pulso** with the same schedule (`cron` and `zone`, or `fixedRate` / `fixedDelay`) if their slug doesn't exist yet. After that, the monitor is managed from the Pulso dashboard.
- Pulso marks a job as down when its ping doesn't arrive on time plus a grace period, and alerts you.

**Your jobs are never affected.** Pings go out in the background on a single thread with short timeouts. If Pulso is slow or unreachable, the ping is logged and dropped, and the job runs exactly as it would without the annotation. Exceptions thrown by the job propagate unchanged.

## Requirements

- Java 17 or later
- Spring Boot 3.5 or 4.x

## Setup

1. Add the dependency:

   ```xml
   <dependency>
       <groupId>dev.lunoralabs.pulso</groupId>
       <artifactId>pulso-spring-boot-starter</artifactId>
       <version>0.1.0</version>
   </dependency>
   ```

2. Create an API key in the Pulso dashboard (**API** page) and configure it:

   ```yaml
   pulso:
     api-key: ${PULSO_API_KEY}
     base-url: https://your-pulso-host
   ```

   Keep the key in an environment variable or your secrets manager, never in the code.

3. Annotate your jobs with `@Monitored("slug")`.

Without `pulso.api-key` and `pulso.base-url`, the starter stays off and `@Monitored` does nothing, which is handy for local development and tests.

## Configuration

| Property | Default | Description |
|---|---|---|
| `pulso.api-key` | — | API key from the Pulso dashboard |
| `pulso.base-url` | — | Address of Pulso |
| `pulso.enabled` | `true` | Turns the starter off |
| `pulso.auto-register` | `true` | Registers `@Monitored @Scheduled` methods at startup |
| `pulso.connect-timeout` | `2s` | Connection timeout for calls to Pulso |
| `pulso.read-timeout` | `5s` | Read timeout for calls to Pulso |

## `@Monitored`

| Attribute | Description |
|---|---|
| `value` | The monitor's slug: lowercase letters, numbers and hyphens (`nightly-backup`) |
| `name` | Display name when auto-registered (default `ClassName.methodName`) |
| `grace` | ISO-8601 grace period when auto-registered, such as `PT30M` (default: Pulso's) |

Methods that Spring doesn't schedule (for example, jobs triggered by a message) can be annotated too: they send pings, but their monitor must be created in the dashboard first.

## Development

```bash
./mvnw verify          # against Spring Boot 3.5
./mvnw verify -Pboot4  # against Spring Boot 4.1
```

CI runs both.

## License

[Apache License 2.0](LICENSE)
