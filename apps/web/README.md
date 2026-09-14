# lineage-web

P0 empty Vue 2 embedded shell for 程序血缘地图.

```bash
npm install
npm run generate:api  # spec/v1/openapi.json → src/generated/openapi.d.ts
npm run dev           # http://127.0.0.1:5173
npm run test          # placeholder
npm run build
```

This is an iframe-friendly empty page, not a full SPA product shell.
Graph UI is intentionally not included yet.

### OpenAPI types

Client types are generated with `openapi-typescript` 7.x (devDependency) from
the repo contract `../../spec/v1/openapi.json`. Output is committed at
`src/generated/openapi.d.ts` so clones do not need to run the generator.
Do not edit that file by hand. After contract changes:

```bash
npm run generate:api
```

The Vue 2 shell is still JavaScript; TypeScript is only a peer of the
generator. Types are not wired to a runtime client in P0.
