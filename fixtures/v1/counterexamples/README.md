# P1 counterexample fixtures

Hand-authored graphs and expected outcomes for the Java domain suite.
**Not** copied from `build-tree.mjs` or any other generator. Expected
`treeIds` / `crossIds` / `parentEdgeIds` / `minHops` are independent
literals.

| File | Counterexample |
|---|---|
| `parallel-relations.json` | Same endpoints, two relation ids; both kept; one tree, one cross |
| `self-loop.json` | Self-loop dropped before indegree; successor still ranked |
| `cycle-tail.json` | Directed cycle + tail; no force-rewired parent; tail stays in reach |
| `reorder-stability.json` | Parent/tree/cross independent of input array order |
| `java-out-edges.json` | Java out-edges truncated for display BFS; facts retained |
| `hidden-intermediate.json` | Denied intermediate must not bridge an authorized tail |
| `path-unknown.json` | Path status `unknown` + `PATH_LENGTH_LIMIT` (257-object chain) |

Alignment with `fixtures/v1/import.json` → `expected.json` lives in the
same JUnit class as a compile-time hook; this folder is the counterexample
suite.
