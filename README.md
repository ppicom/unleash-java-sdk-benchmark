# Benchmark project for the Unleash Java SDK

## Running

```sh
mise run install                       # clone the SDK into sdk/ (git-ignored) and build against main
mise run bench                         # benchmark the SDK's main branch
mise run bench --branch feat/new       # benchmark another SDK branch
```

`bench` fetches `sdk/`, checks out the branch (fast-forwarding it to `origin`), installs it into
`~/.m2`, builds the JMH jar against that SDK version and runs it. Anything after `--` goes to JMH,
e.g. `mise run bench --branch feat/new -- -rf json -rff results/feat-new.json`. Without mise, use
`./run-benchmarks.sh [--branch <name>] [JMH args...]`.

`sdk/` is a normal git clone, so you can also commit or edit there; uncommitted changes are
benchmarked as long as the checkout of `--branch` doesn't conflict with them.

Every branch installs into `~/.m2` under its SNAPSHOT version, so the last install wins. To compare
branches, run `bench` once per branch and save each result to its own file.

`mise tasks` lists the other tasks (`sdk:install`, `build`).

## IsEnabledContentionBenchmark

Reproduces arm A of the yggdrasil-engine contention report in `ygg-repro/ISSUE.md`, going through
`DefaultUnleash.isEnabled` instead of calling the engine directly. One client is shared by 1, 8, 64
and 200 threads (one fork each, 2 s warmup + 6 s measurement). Each op builds a context with a
`userId` and evaluates `repro-toggle`, which is loaded from `repro-features.json` (the `default`
strategy, always on).

Each `isEnabled` allocates a direct `ByteBuffer` in `UnleashEngine.buildMessage`, which takes the
JVM-global `Cleaner.add` monitor, so total throughput stays flat (~0.5–0.6 M ops/s) no matter how
many threads you add. A fix should make the 8/64/200-thread results scale.
