# imdb-federation

GraphQL federation over the IMDb datasets: one Spring Boot + [Netflix DGS](https://netflix.github.io/dgs/)
subgraph per MongoDB collection, composed by a [WunderGraph Cosmo](https://cosmo-docs.wundergraph.com/) router.
Data is loaded into MongoDB Atlas by [imdb-data-pipeline](https://github.com/dperez4787/imdb-data-pipeline).

## Architecture

Two federated entities, seven subgraphs (Apollo Federation v2.5 spec):

| Module | Collection | Contributes |
|---|---|---|
| `subgraph-titles` | `title_basics` | **owns `Title`**; `Query.title/titles` |
| `subgraph-names` | `name_basics` | **owns `Name`**; `Name.knownForTitles`; `Query.name/names` |
| `subgraph-ratings` | `title_ratings` | `Title.rating` |
| `subgraph-episodes` | `title_episode` | `Title.episode`, `Title.episodes`, `Query.episodesOfSeries` |
| `subgraph-crew` | `title_crew` | `Title.directors`, `Title.writers` |
| `subgraph-akas` | `title_akas` | `Title.akas` |
| `subgraph-principals` | `title_principals` | `Title.principals`, `Name.credits`, `Query.principalsByName` |

Every lookup is a DataLoader-batched, indexed `$in` query (entity fetchers included:
N `_entities` representations collapse into one Mongo command — the integration
tests assert the command count). Cross-entity references return zero-cost key
stubs; the router joins them across subgraphs.

`common/` holds the shared csv/characters parsing, pagination clamping,
DataLoader factories, Mongo pool defaults, and the Testcontainers test support
(published as a test-jar).

## Local development

Requirements: JDK 21, Docker.

```bash
./mvnw -B verify                 # build + unit/integration tests (Testcontainers)

# full local stack: 7 subgraphs + Cosmo router
./mvnw -B -DskipTests package
npx -y wgc@0.129.0 router compose -i router/graph.local.yaml -o router/execution-config.json
MONGODB_URI='mongodb+srv://...' docker compose up --build
# router GraphiQL: http://localhost:3002  (subgraphs directly on 8081-8087)
```

Example federated query (through the router):

```graphql
{
  title(tconst: "tt0944947") {
    primaryTitle
    rating { averageRating numVotes }
    directors { primaryName }
    episodes(limit: 3) { primaryTitle episode { seasonNumber episodeNumber } }
  }
}
```

## Deployment

Push to `main` deploys each changed module (changes to `common/`, the parent pom,
or the Dockerfile deploy all seven) as Cloud Run services
`imdb-subgraph-<name>` via GitHub Actions + Workload Identity Federation
(no service-account keys). See `.github/workflows/deploy.yml`.

Infrastructure (service accounts, WIF provider, Artifact Registry, secret IAM)
is Terraform in `infra/`; the Cloud Run services themselves are created by
`gcloud run deploy` in CI. Subgraphs read the Atlas URI from the
`IMDB_MONGODB_URI` secret owned by the pipeline stack.

The Cosmo router deployment (composing the deployed subgraph URLs) is a later
phase; until then cross-subgraph fields resolve only through a local router.
