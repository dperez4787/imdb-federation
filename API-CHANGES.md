# API Changes — Search Orchestrator (July 2026)

Audience: the UI phase. Everything below is served through the Cosmo router at
`https://cosmo-router-dkuqnmldta-uc.a.run.app/graphql` (Google/Firebase JWT required;
anonymous → 401). Existing entity fields are unchanged — search results are `Title`/`Name`
stubs the router transparently hydrates, so any field documented for those entities can be
selected directly on search results.

## New entity field: `Title.imgUrl: String`

OMDb poster URL with the API key attached **server-side** (key lives in Secret Manager,
served only through the authenticated router — no more key in client bundles). Nullable:
environments without the key configured return null, never an error. Hydrates onto search
results and stubs like any other Title field:

```graphql
{ search(query: "godfather", limit: 3) { ... on Title { primaryTitle imgUrl } } }
```

Scope note for UI: the returned URL embeds the key and the browser fetches
`img.omdbapi.com` directly — this is access-gating and rotation hygiene, not absolute
secrecy. Select `imgUrl` instead of constructing OMDb URLs client-side.

## New root queries

### `searchTitles(filter, sort, limit, offset): TitleSearchResult!`

Multi-dimensional title search. All filter dimensions AND together. Empty filter = browse
everything (use sort + paging) — that IS the "top titles" query.

```graphql
{
  searchTitles(
    filter: {genresAny: ["Sci-Fi"], titleTypes: ["movie"], startYearFrom: 1970,
             startYearTo: 1989, votesFrom: 10000}
    sort: RATING_DESC, limit: 25, offset: 0
  ) {
    total totalIsCapped
    items { tconst primaryTitle startYear rating { averageRating numVotes }
            directors { primaryName } }   # hydrated from other subgraphs
  }
}
```

**`TitleSearchFilter`** (all fields optional; values are data-driven strings — see
"Facet values" below):

| Field | Semantics | Cap |
|---|---|---|
| `query: String` | Full-text (word/stem) match on primaryTitle. Not substring. | — |
| `titlePrefix: String` | Case-insensitive anchored prefix — use for autocomplete. **Min 3 chars.** Mutually exclusive with `query`. Evaluated against a deterministic alphabetical cap of prefix matches (25k) — narrow prefixes exact; hot short ones ("the…") sampled. | — |
| `titleTypes: [String!]` | e.g. `movie`, `tvSeries`, `short` | — |
| `genresAny: [String!]` | at least one genre matches | 10 |
| `genresAll: [String!]` | every genre present (combinable with genresAny) | 5 |
| `startYearFrom/To: Int` | release-year window | — |
| `runtimeFrom/To: Int` | minutes | — |
| `ratingFrom/To: Float` | **excludes unrated titles by definition** | — |
| `votesFrom: Int` | minimum vote count (also excludes unrated) | — |
| `includeAdult: Boolean! = false` | adult titles excluded unless opted in | — |
| `withPeople: [ID!]` | nconsts credited on the title | 20 |
| `peopleMode: ANY \| ALL = ALL` | "with Hanks AND Ryan" vs "with either" | — |
| `peopleCategories: [String!]` | restrict credit match, e.g. `["actor","actress"]` | — |

**`TitleSort`**: `RELEVANCE` (text score with `query`, otherwise = popularity),
`POPULARITY_DESC` (vote count — the default ranking), `RATING_DESC` (pair with `votesFrom`
to avoid 10-vote 9.9s), `YEAR_DESC`, `YEAR_ASC`.

### `searchNames(filter, sort, limit, offset): NameSearchResult!`

People search. Title-scoped dimensions join through credits at query time.

```graphql
{
  searchNames(
    filter: {inGenres: ["Film-Noir"], activeFrom: 1940, activeTo: 1955,
             categories: ["actor", "actress"]}
  ) {
    total titleCandidatesCapped
    items { nconst primaryName birthYear knownForTitles { primaryTitle } }
  }
}
```

**`NameSearchFilter`**:

| Field | Semantics | Cap |
|---|---|---|
| `query: String` | Text match on primaryName. With a title-scoped dimension it degrades to case-insensitive substring over the joined candidates. Exclusive with `namePrefix`. | — |
| `namePrefix: String` | anchored prefix, autocomplete. **Min 3 chars.** Same prefix-candidate cap as titlePrefix. | — |
| `professions: [String!]` | e.g. `actor`, `composer` | — |
| `bornFrom/To: Int` | birth-year window | — |
| `inTitles: [ID!]` | credited on at least one of these tconsts | 100 |
| `inGenres: [String!]` | credited on a title with one of these genres | 10 |
| `activeFrom/To: Int` | credited on a title whose startYear is in range | — |
| `categories: [String!]` | credit category — **requires** a title-scoped dimension | — |

**`NameSort`**: `RELEVANCE` (text score with `query`; matching-credit count when
title-scoped; otherwise popularity), `POPULARITY_DESC` (materialized: sum of numVotes over
the person's knownForTitles — the default people ranking), `CREDITS_DESC` (requires title
scope), `NAME_ASC`, `BIRTH_YEAR_ASC/DESC`.

### `search(query: String!, kinds: [SearchKind!], limit: Int): [SearchHit!]!`

The global search box: one call, titles and people merged and ranked (text relevance,
popularity breaking ties). `union SearchHit = Title | Name` — branch on `__typename`.
`kinds: [TITLE]`/`[NAME]` restricts; default both. `limit` 1–50 (default 10), no paging,
no counts, adult titles excluded. Query min 2 chars; word/stem matching — use
`titlePrefix`/`namePrefix` autocomplete while the user is still typing.

```graphql
{ search(query: "carmencita", limit: 10) {
    __typename
    ... on Title { tconst primaryTitle startYear rating { averageRating } }
    ... on Name { nconst primaryName }
} }
```

### `facets: Facets!` — vocabulary facets (dropdown/checkbox sources)

Materialized at rebuild with global counts, sorted by count desc (≤200 values each):
`genres`, `titleTypes`, `principalCategories` (crew/credit job types from title_principals),
`professions`, `akaRegions`, `akaLanguages` — each `[FacetValue!]!` of `{value, count}`.
One cheap read; safe to fetch on app load and cache.

```graphql
{ facets { genres { value count } principalCategories { value count } } }
```

### Contextual facets — `facets(dimensions, perDimension)` on both result types

Live value counts **within the current filter** (checkbox counts that update as the user
narrows). Computed only when selected; evaluated over at most the same 10k candidate cap
as `total`; `perDimension` 1–50 (default 20).

- `TitleSearchResult.facets(dimensions: [TitleFacetDimension!]!)` — `GENRES`,
  `TITLE_TYPES`, `DECADES` (value = decade start, e.g. "1990"), `RATING_BANDS`
  (value = floor, "8" = 8.0–8.9), `RUNTIME_BANDS` (0/30/60/90/120/150/180+ minutes).
- `NameSearchResult.facets(dimensions: [NameFacetDimension!]!)` — `PROFESSIONS`,
  `BIRTH_DECADES`.

```graphql
{ searchTitles(filter: {genresAny: ["Drama"]}) {
    items { tconst primaryTitle }
    facets(dimensions: [GENRES, DECADES, RATING_BANDS]) { dimension values { value count } }
} }
```

This is the deliberate alternative to exposing raw Mongo pipelines: typed dimensions map
internally onto `$facet`/`$sortByCount`/`$bucket`. Award dropdowns will slot in as new
facet ids + an `AWARDS` dimension once an awards dataset exists (still no awards data in
the IMDb TSVs).

### `searchInfo: SearchInfo!`

`{ rebuiltAt, titleCount, nameCount }` — freshness of the derived search data. Surface
`rebuiltAt` in the UI; search data is only as fresh as the last rebuild (source imports do
not update it automatically).

## Result types & paging contract

- `items: [Title!]! / [Name!]!` — key stubs; select any owned-subgraph field on them.
- `total: Int!` + `totalIsCapped: Boolean!` — total is **capped at 10,000** (`totalIsCapped:
  true` means "at least 10,000"). Counts cost a second query — only select `total` when the
  UI shows it.
- `titleCandidatesCapped: Boolean!` (names only) — an open-ended title scope
  (inGenres/activeFrom without inTitles) is evaluated against the **most popular 5,000
  matching titles**; `true` means the scope was truncated (show "from the most popular
  matching titles").
- `limit` 1–100 (default 25); `offset` 0–10,000 (deeper paging rejected with BAD_REQUEST).
- Ordering is deterministic (stable tiebreaks) — pages are disjoint and repeatable between
  rebuilds.
- Validation failures (caps, exclusive fields, inverted ranges, unscoped
  CREDITS_DESC/categories) return GraphQL errors, not empty results.
- Every search query carries a server-side execution ceiling (~15 s); pathological
  queries fail fast instead of camping on the database.

## UI performance guidance (measured 2026-07-11)

- **While typing**: send ONLY `titlePrefix`/`namePrefix` queries, debounced — never the
  `$text`-backed `search`/`query` per keystroke. Common single tokens ("Daniel") cost
  seconds through the text index; prefixes are index-hinted + capped.
- **On submit (Enter)**: use `search`/`query` for word/stem semantics.
- Fetch `searchInfo` and `facets` once on load, not per keystroke.
- Avoid combining several search root fields in one operation per keystroke — they run
  concurrently but contend for the same database.

## Facet values for UI controls

Data-driven string enums (checkbox sources). Current values:
- `titleTypes`: from `searchInfo`-backed meta (movie, short, tvEpisode, tvSeries, tvMovie,
  tvMiniSeries, tvSpecial, video, videoGame, tvShort) — treat as open set.
- `genres`: IMDb's ~28 genres (Action, Adventure, Animation, ..., Western) — open set.
- `categories`: actor, actress, director, writer, producer, composer, cinematographer,
  editor, casting_director, production_designer, self, archive_footage, archive_sound.
- `professions`: same vocabulary as categories plus miscellaneous, soundtrack, etc.

## Not in this release

- **Awards filters** — no awards data exists in the IMDb public datasets. The filter input
  has a documented extension point (`awards: AwardFilter`) for when a dataset lands.
- Exact totals beyond 10k, cursor pagination, unscoped credit-category search.

## Operational notes

- Rebuild: `./scripts/rebuild.sh` (IAM-protected REST, not GraphQL). Run after each
  pipeline import; takes on the order of an hour on the M10 cluster.
- Existing queries (`title`, `titles`, `name`, `names`, `episodesOfSeries`,
  `principalsByName`) and all entity fields are unchanged.
