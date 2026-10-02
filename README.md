# Benchmark project for the Unleash Java SDK

## Running

```sh
mise run install                       # clone the SDK into sdk/ and the bindings into bindings/ (both git-ignored), build against main
mise run bench                         # benchmark the SDK's and the bindings' main branches
mise run bench --branch feat/new       # benchmark another SDK branch
mise run bench --bindings-branch fix/x # benchmark another bindings branch
```

Building the bindings needs a Rust toolchain (`cargo`), since the engine's native library is
built from the same checkout.

`bench` fetches `sdk/`, checks out the branch (fast-forwarding it to `origin`), installs it into
`~/.m2`, builds the JMH jar against that SDK version and runs it. Anything after `--` goes to JMH,
e.g. `mise run bench --branch feat/new -- -rf json -rff results/feat-new.json`. Without mise, use
`./run-benchmarks.sh [--branch <name>] [JMH args...]`.

`sdk/` is a normal git clone, so you can also commit or edit there; uncommitted changes are
benchmarked as long as the checkout of `--branch` doesn't conflict with them.

Every branch installs into `~/.m2` under its SNAPSHOT version, so the last install wins. To compare
branches, run `bench` once per branch and save each result to its own file.

### The engine

`bindings/` is a clone of [yggdrasil-bindings](https://github.com/Unleash/yggdrasil-bindings), and
works the same way as `sdk/`. `mise run bindings:install` (`./install-bindings.sh`) builds whatever
is checked out there: `cargo build --release`, then the Gradle project in `bindings/java-engine`
with that native library bundled, published to `~/.m2` as `<version>-SNAPSHOT` (e.g.
`1.0.3-SNAPSHOT`) so it never overwrites a released engine.

`benchmarks/pom.xml` depends on that engine directly, which overrides the version the SDK pins, so
**every** benchmark (SDK ones included) runs against the engine in `bindings/`.

To try an engine change: edit `bindings/java-engine` (or the Rust code), then
`mise run bindings:install && mise run build` and run `benchmarks/target/benchmarks.jar`.

`mise tasks` lists the other tasks (`sdk:install`, `bindings:install`, `build`).

## IsEnabledContentionBenchmark

Reproduces arm A of the yggdrasil-engine contention report in `ygg-repro/ISSUE.md`, going through
`DefaultUnleash.isEnabled` instead of calling the engine directly. One client is shared by 1, 8, 64
and 200 threads (one fork each, 2 s warmup + 6 s measurement). Each op builds a context with a
`userId` and evaluates `repro-toggle`, which is loaded from `repro-features.json` (the `default`
strategy, always on).

Each `isEnabled` allocates a direct `ByteBuffer` in `UnleashEngine.buildMessage`, which takes the
JVM-global `Cleaner.add` monitor, so total throughput stays flat (~0.5–0.6 M ops/s) no matter how
many threads you add. A fix should make the 8/64/200-thread results scale.

## EngineIsEnabledContentionBenchmark

The same benchmark one layer down: `UnleashEngine.isEnabled` called directly on one shared engine,
loaded with the same `repro-features.json`, like arm A of `ygg-repro/Repro.java`. Comparing it with
`IsEnabledContentionBenchmark` shows how much of the cost is the engine versus the SDK. Run only it
with `java -jar benchmarks/target/benchmarks.jar EngineIsEnabledContention`.
