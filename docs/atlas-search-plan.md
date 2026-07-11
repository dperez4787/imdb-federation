# Plan: Atlas Search for autocomplete & unified search

Status: proposed (2026-07-11). Owner: next search-performance session.

## Why

Measured against the live M20 (full dataset, warm):

| Query | Mongo-native today | Problem |
|---|---|---|
| `search(query: "Daniel")` | 8.4 s (names side 5.7 s) | `$text` has no top-k: every one of ~1M matches is scored, then block-sorted |
| `searchNames(namePrefix: "Daniel")` | 7.3 s → ~1–2 s after the hint/cap quick-wins | prefix range + popularity sort can't share a B-tree; the cap trades correctness at hot prefixes |
| filtered searches (genre/year/rating/people) | 0.1–1.5 s | fine — **not** in scope here |

The quick-wins PR makes prefixes tolerable; neither path is autocomplete-grade
(<100 ms) nor gives good single-token relevance. Atlas Search (Lucene `mongot`,
available on M10+) is purpose-built for both: native top-k, `autocomplete`
(edgeGram) fields, and score functions that can boost by a numeric field —
i.e., popularity-weighted ranking *inside* the engine instead of our
sort-then-tiebreak approximation.

## Scope

Replace the execution path of exactly three things, keeping the GraphQL API
unchanged:

1. `search(query:)` — unified box (both kinds)
2. `searchTitles(filter: {titlePrefix})` / `searchNames(filter: {namePrefix})`
3. `searchTitles(filter: {query})` / `searchNames(filter: {query})` when NOT
   combined with joins (text-first/people-first strategies keep the current path)

Everything else (filtered browse, joins, facets-via-`$facet`, counts) stays on
B-tree pipelines — they're already fast and Atlas Search would add nothing.

## Design

### Index definitions (per collection, JSON kept in `rebuild/searchindexes/`)

`search_titles` index `default`:
```json
{
  "mappings": { "dynamic": false, "fields": {
    "primaryTitle": [
      { "type": "string" },
      { "type": "autocomplete", "tokenization": "edgeGram", "minGrams": 3, "maxGrams": 15, "foldDiacritics": true }
    ],
    "titleType": { "type": "token" },
    "isAdult":   { "type": "boolean" },
    "numVotes":  { "type": "number" }
  } }
}
```
`search_names` mirrors it (`primaryName`, `popularity`, `professions` as token).

### Query shapes

- Autocomplete (typing): `$search { autocomplete: { path: "primaryTitle", query: $q } , scoreDetails/function boost by numVotes }` → `$limit 10` → project id. Native top-k; target < 100 ms.
- Unified/submit: `compound { must: [text(primaryTitle)], should: [] }` with
  `function score: multiply [ relevance, log1p(numVotes|popularity) ]` — fixes both
  the "Tom Tom" tf artifact and the blockbuster-crew skew in one place.
- `kinds` filtering and adult exclusion move into the `$search` `filter` clause.

### The hard part: rebuild lifecycle

Atlas Search indexes are bound to a namespace and **do not survive our
staging→rename promote** (the renamed-in collection arrives without them; the
index on the old namespace dies with `dropTarget`). Plan:

1. `INDEXES` step additionally issues `createSearchIndexes` on the `_next`
   collections (idempotent by name) — Lucene builds *before* promote, in parallel
   with B-tree builds.
2. `PROMOTE` gains a wait: poll `$listSearchIndexes` until `status: READY` on
   `_next` before renaming (config cap, e.g. 30 min; on timeout promote anyway
   and set a `searchDegraded` flag in `search_meta`).
3. **Verify empirically first** (the one big unknown): whether search indexes
   follow the rename with the collection or must be re-created post-rename. The
   docs are thin here; a 10-minute experiment on a scratch collection decides
   whether step 2 waits pre-promote or re-creates post-promote. Fallback if
   post-rename rebuild is unavoidable: serve the Mongo-native path until READY
   (the service already has both paths — see rollout).

### Rollout (dual-path, flag-gated)

- `imdb.search.engine: btree | atlas` property; `UnifiedSearchService` and the
  prefix branches consult it per request, defaulting to `btree`.
- Ship dark → flip `atlas` on the live service → compare timings + result
  quality side by side (the flag makes A/B trivial via env var) → remove the
  flag once satisfied, keep the btree path for local dev.
- Tests: `mongodb/mongodb-atlas-local` Testcontainers image supports `$search`
  locally — ITs can cover both engines. CI stays green without Atlas.

### Risks

- **mongot RAM**: the Lucene process shares node memory with WiredTiger on the
  M20 (4GB). Two autocomplete indexes over 28M short strings ≈ several hundred
  MB - 1GB+. Measure post-build; M30 is the escape hatch.
- **Index build time per weekly rebuild**: est. 15–45 min for 28M docs — extends
  the rebuild window but runs before promote, so no serving gap.
- **Eventual consistency**: Atlas Search indexes lag writes by seconds — irrelevant
  for our batch-rebuilt collections.

### Estimate

One focused session: index defs + rebuild integration + dual-path service branch
+ atlas-local ITs + live A/B. The rename-lifecycle experiment is the first hour
and gates the rest.
