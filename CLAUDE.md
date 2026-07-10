# imdb-federation

Multi-module Maven monorepo: 7 Spring Boot + Netflix DGS federation subgraphs
(one per IMDb MongoDB collection), later composed by a WunderGraph Cosmo router.
Java 21, Spring Boot 3.5.x, DGS 10.2.x (spring-graphql integration — only
`graphql-dgs-spring-graphql-starter`, never the legacy starter).

## Commands

- Build + all tests: `./mvnw -B verify` (integration tests need Docker; they are
  `*IT` classes run by failsafe)
- One module: `./mvnw -B -pl subgraph-titles -am verify` (`-am` is required —
  modules depend on `imdb-common`, which is never installed)
- Composition check: `npx -y wgc@0.129.0 router compose -i router/graph.local.yaml -o /tmp/x.json`
- Local stack: see README (package -> wgc compose -> `docker compose up`)

## Architecture rules

- `Title` (key `tconst`) is owned by subgraph-titles; `Name` (key `nconst`) by
  subgraph-names. Everyone else re-declares the entity with `@key` and only
  their contributed fields. Referenced-but-not-extended entities use
  `@key(..., resolvable: false)` and need no entity fetcher.
- Every schema file starts with the federation v2.5 `@link` — Cosmo silently
  falls back to Fed v1 semantics without it.
- ALL Mongo reads go through DataLoaders built by `common`'s `BatchLoaders`
  (byUniqueKey / groupedByKey / groupedPaged) on the `imdbLoaderExecutor`.
  Owner subgraphs load inside the entity fetcher (async, Pattern A); contributor
  subgraphs return sync key stubs and load at field level (Pattern B). Root
  single-parent queries use a plain indexed find.
- Root queries may only use existing indexes: tconst/nconst uniques,
  `parentTconst`, principals `nconst`, akas `(titleId, ordering)`.
- Data quirks (from the import pipeline): `\N` fields are ABSENT in Mongo (never
  null); multi-value columns are comma-separated strings (`CsvValues.split`);
  `title_principals.characters` is a JSON-array string (`CharactersJson.parse`);
  int32 0/1 flags map via `Fields.toBoolean`.
- Every module's federation IT must assert the Mongo command count (via
  `MongoCommandCounter`) to prove batching — keep that pattern for new fields.

## Gotchas

- Local Docker Desktop 29 needs `common/src/test/resources/docker-java.properties`
  (`api.version=1.44`) or Testcontainers gets HTTP 400 — don't delete it.
- The SDL printer emits `@key(fields : "tconst", resolvable : true)` (spaces,
  explicit defaults) — assert with the whitespace-tolerant regex used in the ITs.
- Mongo pool defaults (maxSize 15) live in `ImdbCommonAutoConfiguration`; URI
  options override them. Don't raise them: 7 services share one Atlas M10.
- Deploys: push to main; `dorny/paths-filter` picks changed modules (shared
  files -> all 7). Services are `imdb-subgraph-<name>` in us-central1,
  min-instances=0. Terraform in `infra/` covers IAM/AR/WIF only, never the
  Cloud Run services.
