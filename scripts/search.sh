#!/usr/bin/env bash
# Quick search against the live federated search backend, through the router
# (requires a Google identity with router access — your gcloud login works).
#
#   ./scripts/search.sh              # searches "Tom"
#   ./scripts/search.sh "godfather"  # searches anything else
#   KINDS='["TITLE"]' ./scripts/search.sh "alien"   # restrict kinds
set -euo pipefail

QUERY="${1:-Tom}"
KINDS="${KINDS:-null}"   # null = both kinds; or '["TITLE"]' / '["NAME"]'
ROUTER="${ROUTER:-https://cosmo-router-dkuqnmldta-uc.a.run.app}"

# Passed to jq via --arg so newlines are JSON-escaped correctly (never embed
# the doc as a literal inside the jq program — raw newlines break the wire).
# NB: don't select @governed fields here (Name.birthYear/deathYear,
# Rating.numVotes) — the router's fieldauth denies them for tokens without
# a roles claim, which is what gcloud identity tokens are.
GQL='
query($q: String!, $kinds: [SearchKind!]) {
  search(query: $q, kinds: $kinds, limit: 10) {
    __typename
    ... on Title { tconst primaryTitle startYear titleType rating { averageRating } }
    ... on Name  { nconst primaryName }
  }
}'

BODY=$(jq -cn --arg q "$QUERY" --arg gql "$GQL" --argjson kinds "$KINDS" \
  '{query: $gql, variables: {q: $q, kinds: $kinds}}')

curl -sf --max-time 60 -X POST "$ROUTER/graphql" \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer $(gcloud auth print-identity-token)" \
  -d "$BODY" \
| jq -r '.data.search[] |
    if .__typename == "Title"
    then "TITLE  \(.tconst)  \(.primaryTitle) (\(.startYear // "?")) [\(.titleType)]\(if .rating then "  ★\(.rating.averageRating)" else "" end)"
    else "NAME   \(.nconst)  \(.primaryName)"
    end'
