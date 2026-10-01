package io.getunleash;

import io.getunleash.repository.ToggleBootstrapProvider;
import io.getunleash.util.UnleashConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
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
 * Not a real benchmark: verifies that the SDK is on the classpath, that a client can be built and
 * loaded with toggles, and that the JMH harness runs.
 *
 * <p>With UNLEASH_URL set, toggles are fetched from that server (using UNLEASH_API_KEY and,
 * optionally, UNLEASH_PROJECT) and the first one is evaluated. Otherwise they come from the
 * bundled smoke-features.json.
 */
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
public class SetupSmokeBenchmark {

    private static final String BOOTSTRAP_FEATURE = "smoke.enabled";

    private Unleash unleash;
    private String feature;

    @Setup
    public void setup() {
        String url = System.getenv("UNLEASH_URL");
        UnleashConfig.Builder config =
                UnleashConfig.builder()
                        .appName("benchmark-smoke")
                        .disablePolling()
                        .disableMetrics()
                        // A fresh backup path per run, so toggles cached by a previous run
                        // can't shadow the ones loaded here.
                        .backupFile(
                                Path.of(
                                                System.getProperty("java.io.tmpdir"),
                                                "unleash-benchmark-" + System.nanoTime() + ".json")
                                        .toString());

        if (url == null || url.isBlank()) {
            config.unleashAPI("http://localhost:4242/api")
                    .apiKey("irrelevant")
                    .toggleBootstrapProvider(classpathBootstrap("/smoke-features.json"));
            unleash = new DefaultUnleash(config.build());
            feature = BOOTSTRAP_FEATURE;
            if (!unleash.isEnabled(feature)) {
                throw new IllegalStateException(
                        "Expected '" + feature + "' to be enabled; bootstrap toggles were not loaded");
            }
        } else {
            config.unleashAPI(url)
                    .apiKey(requireEnv("UNLEASH_API_KEY"))
                    .synchronousFetchOnInitialisation(true);
            String project = System.getenv("UNLEASH_PROJECT");
            if (project != null && !project.isBlank()) {
                config.projectName(project);
            }
            unleash = new DefaultUnleash(config.build());
            List<String> toggles = unleash.more().getFeatureToggleNames();
            if (toggles.isEmpty()) {
                throw new IllegalStateException(
                        "No toggles fetched from " + url + "; check the API key and project");
            }
            feature = toggles.get(0);
        }
        System.out.println("Benchmarking isEnabled(\"" + feature + "\")");
    }

    @TearDown
    public void tearDown() {
        unleash.shutdown();
    }

    @Benchmark
    public void isEnabled(Blackhole bh) {
        bh.consume(unleash.isEnabled(feature));
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set when UNLEASH_URL is set");
        }
        return value;
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
