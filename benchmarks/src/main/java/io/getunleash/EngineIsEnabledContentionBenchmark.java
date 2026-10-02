package io.getunleash;

import io.getunleash.engine.Context;
import io.getunleash.engine.UnleashEngine;
import io.getunleash.engine.YggdrasilInvalidInputException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Arm A of the yggdrasil-engine contention report (ygg-repro/ISSUE.md): UnleashEngine.isEnabled
 * called directly, without the SDK in between, against the engine built from bindings/.
 *
 * <p>Same shape as {@link IsEnabledContentionBenchmark}: one engine shared by all threads, one
 * fork per thread count, 2 s warmup and 6 s measurement.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(value = 1, jvmArgsAppend = "--enable-native-access=ALL-UNNAMED")
@Warmup(iterations = 1, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 1, time = 6, timeUnit = TimeUnit.SECONDS)
public class EngineIsEnabledContentionBenchmark {

    private static final String TOGGLE = "repro-toggle";

    private UnleashEngine engine;

    @Setup
    public void setup() throws IOException, YggdrasilInvalidInputException {
        engine = new UnleashEngine();
        engine.takeState(readResource("/repro-features.json"));

        // Fail loudly rather than measure a toggle that silently does not exist.
        if (!Boolean.TRUE.equals(engine.isEnabled(TOGGLE, new Context()).value)) {
            throw new IllegalStateException(
                    "Expected '" + TOGGLE + "' to be enabled in repro-features.json");
        }
    }

    @Benchmark
    @Threads(1)
    public Boolean isEnabled_001t() throws YggdrasilInvalidInputException {
        return evaluate();
    }

    @Benchmark
    @Threads(8)
    public Boolean isEnabled_008t() throws YggdrasilInvalidInputException {
        return evaluate();
    }

    @Benchmark
    @Threads(64)
    public Boolean isEnabled_064t() throws YggdrasilInvalidInputException {
        return evaluate();
    }

    @Benchmark
    @Threads(200)
    public Boolean isEnabled_200t() throws YggdrasilInvalidInputException {
        return evaluate();
    }

    // Same work per op as Repro.ARM_A: a fresh context with a userId, then one evaluation.
    private Boolean evaluate() throws YggdrasilInvalidInputException {
        Context ctx = new Context();
        ctx.setUserId("u");
        return engine.isEnabled(TOGGLE, ctx).value;
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream in = EngineIsEnabledContentionBenchmark.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
