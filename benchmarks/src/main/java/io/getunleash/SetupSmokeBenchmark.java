package io.getunleash;

import io.getunleash.repository.ToggleBootstrapProvider;
import io.getunleash.util.UnleashConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Not a real benchmark: verifies that the SDK is on the classpath, that a client can be built
 * offline from a bootstrap file, and that the JMH harness runs.
 */
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
public class SetupSmokeBenchmark {

    private static final String FEATURE = "smoke.enabled";

    private Unleash unleash;

    @Setup
    public void setup() {
        UnleashConfig config =
                UnleashConfig.builder()
                        .appName("benchmark-smoke")
                        .unleashAPI("http://localhost:4242/api")
                        .apiKey("irrelevant")
                        .disablePolling()
                        .disableMetrics()
                        .toggleBootstrapProvider(classpathBootstrap("/smoke-features.json"))
                        .build();
        unleash = new DefaultUnleash(config);

        if (!unleash.isEnabled(FEATURE)) {
            throw new IllegalStateException(
                    "Expected '" + FEATURE + "' to be enabled; bootstrap toggles were not loaded");
        }
    }

    @TearDown
    public void tearDown() {
        unleash.shutdown();
    }

    @Benchmark
    public void isEnabled(Blackhole bh) {
        bh.consume(unleash.isEnabled(FEATURE));
    }

    // ToggleBootstrapFileProvider resolves classpath resources to a File, which fails inside the
    // shaded benchmarks jar, so read the resource as a stream instead.
    private static ToggleBootstrapProvider classpathBootstrap(String resource) {
        return () -> {
            try (InputStream in = SetupSmokeBenchmark.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("Missing classpath resource " + resource);
                }
                return Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }
}
