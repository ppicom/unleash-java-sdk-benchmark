# Benchmark project for the Unleash Java SDK

## Running

```sh
./run-benchmarks.sh
```

This installs the SDK from `../../Unleash/unleash-java-sdk` (override with `SDK_DIR=...`) into
`~/.m2`, using whatever branch is checked out there, then builds and runs the JMH jar. Arguments
are passed through to JMH, e.g. `./run-benchmarks.sh -rf json -rff results/my-branch.json`.

Every branch installs as the same SNAPSHOT version, so the last install wins. To compare
branches, check each one out in the SDK, run the script, and save results to a separate file.

### With mise

```sh
cp mise.local.toml.example mise.local.toml   # set SDK_DIR etc. under [env]
mise run bench                               # or: mise run bench -- -rf json -rff results/x.json
```

`mise tasks` lists the other tasks (`sdk:install`, `build`).

### Against a real Unleash instance

By default the benchmark loads toggles from the bundled `smoke-features.json`. Set `UNLEASH_URL`
and `UNLEASH_API_KEY` (and optionally `UNLEASH_PROJECT`) to fetch them from a server instead. Either
way, the toggle evaluated is `UNLEASH_FEATURE` (default `benchmark-feature-flag`), which must
exist on the server or, when bootstrapping, in `smoke-features.json`.
