# CoreGuard Network Defense Lab CLI

Prototype Cobra CLI for a small CoreGuard network defense simulation.

This directory is a standalone Go module:

```text
github.com/victormart43210-ship-it/CoreGuard-Android/cli
```

## Prototype scope

- The lab state is in memory only. A Cobra command tree can share one engine inside a single process; a new `coreguard` process starts from the default all-healthy lab state.
- The Expo/tRPC React Native project referenced in delivery materials is not present in this repository.
- This CLI stores no secrets and does not bundle external executables such as `uv.exe`.

## Topology

The default topology is a fixed, connected, undirected graph with nodes `N0` through `N15`.

Ring edges:

- `N0-N1`
- `N1-N2`
- `N2-N3`
- `N3-N4`
- `N4-N5`
- `N5-N6`
- `N6-N7`
- `N7-N8`
- `N8-N9`
- `N9-N10`
- `N10-N11`
- `N11-N12`
- `N12-N13`
- `N13-N14`
- `N14-N15`
- `N15-N0`

Chord edges:

- `N0-N8`
- `N1-N9`
- `N2-N10`
- `N3-N11`
- `N4-N12`
- `N5-N13`
- `N6-N14`
- `N7-N15`

## Commands

```sh
coreguard status [--json]
coreguard topology [--json] [--mst]
coreguard simulate attack --mode bfs|dfs --seed N3 --steps 4 [--json]
coreguard simulate defend --mode direct|cascade --target N3 [--json]
coreguard simulate rollback [--json]
```

## Build and test

```sh
go mod tidy
go test -race ./...
go vet ./...
go build -o /tmp/coreguard-cli ./cmd/coreguard
```

## Examples

```sh
/tmp/coreguard-cli topology --mst
/tmp/coreguard-cli status --json
/tmp/coreguard-cli simulate attack --mode bfs --seed N3 --steps 4 --json
/tmp/coreguard-cli simulate defend --mode cascade --target N3
```
