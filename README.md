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
