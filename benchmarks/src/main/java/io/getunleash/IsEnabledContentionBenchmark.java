package io.getunleash;

import io.getunleash.repository.ToggleBootstrapProvider;
import io.getunleash.util.UnleashConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Reproduces arm A of the yggdrasil-engine contention report (ygg-repro/ISSUE.md), going through
 * the SDK instead of calling UnleashEngine directly.
 *
 * <p>Every isEnabled ends up in UnleashEngine.buildMessage, which allocates a direct ByteBuffer per
 * call. That takes the JVM-global Cleaner.add monitor, so total throughput stays flat as threads
 * are added. The same client is shared by all threads, and each thread count gets its own fork,
 * with 2 s warmup and 6 s measurement, like the original Repro.java.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = "--enable-native-access=ALL-UNNAMED")
@Warmup(iterations = 1, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 6, timeUnit = TimeUnit.SECONDS)
public class IsEnabledContentionBenchmark {

    private static final String TOGGLE = "repro-toggle";

    private Unleash unleash;

    @Setup
    public void setup() {
        UnleashConfig config =
                UnleashConfig.builder()
                        .appName("benchmark-contention")
                        .unleashAPI("http://localhost:4242/api")
                        .apiKey("irrelevant")
                        .disablePolling()
                        .disableMetrics()
                        // A fresh backup path per run, so toggles cached by a previous run
                        // can't shadow the ones loaded here.
                        .backupFile(
                                Path.of(
                                                System.getProperty("java.io.tmpdir"),
                                                "unleash-benchmark-" + System.nanoTime() + ".json")
                                        .toString())
                        .toggleBootstrapProvider(classpathBootstrap("/repro-features.json"))
                        .build();
        unleash = new DefaultUnleash(config);

        // Fail loudly rather than measure a toggle that silently does not exist.
        if (!unleash.isEnabled(TOGGLE)) {
            throw new IllegalStateException(
                    "Expected '" + TOGGLE + "' to be enabled in repro-features.json");
        }
    }

    @TearDown
    public void tearDown() {
        unleash.shutdown();
    }

    @Benchmark
    @Threads(1)
    public boolean isEnabled_001t() {
        return evaluate();
    }

    @Benchmark
    @Threads(8)
    public boolean isEnabled_008t() {
        return evaluate();
    }

    @Benchmark
    @Threads(64)
    public boolean isEnabled_064t() {
        return evaluate();
    }

    @Benchmark
    @Threads(200)
    public boolean isEnabled_200t() {
        return evaluate();
    }

    // Same work per op as Repro.ARM_A: a fresh context with a userId, then one evaluation.
    private boolean evaluate() {
        UnleashContext ctx = UnleashContext.builder().userId("u").build();
        return unleash.isEnabled(TOGGLE, ctx);
    }

    // ToggleBootstrapFileProvider resolves classpath resources to a File, which fails inside the
    // shaded benchmarks jar, so read the resource as a stream instead.
    private static ToggleBootstrapProvider classpathBootstrap(String resource) {
        return () -> {
            try (InputStream in =
                    IsEnabledContentionBenchmark.class.getResourceAsStream(resource)) {
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
