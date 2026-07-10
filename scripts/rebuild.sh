#!/usr/bin/env bash
# Drives the orchestrator's derived-collection rebuild step by step so each
# request stays well within the Cloud Run request timeout. Requires an identity
# with roles/run.invoker on imdb-subgraph-orchestrator (your gcloud user or an
# impersonated SA via IMPERSONATE_SA).
#
#   ./scripts/rebuild.sh                 # full rebuild against the live service
#   URL=http://localhost:8080 ./scripts/rebuild.sh   # local run
set -euo pipefail

URL="${URL:-https://imdb-subgraph-orchestrator-dkuqnmldta-uc.a.run.app}"
STEPS=(titles ratings names kft popularity indexes promote facets)

id_token() {
  if [ -n "${IMPERSONATE_SA:-}" ]; then
    gcloud auth print-identity-token --impersonate-service-account "$IMPERSONATE_SA" --audiences "$URL"
  else
    gcloud auth print-identity-token
  fi
}

echo "rebuilding search collections at $URL"
for step in "${STEPS[@]}"; do
  echo "== step: $step"
  # fresh token per step: earlier steps can outlive a token's 1h lifetime
  curl -sf --max-time 3500 -X POST "$URL/admin/rebuild?steps=$step" \
    -H "Authorization: Bearer $(id_token)" | sed 's/^/   /'
  echo
done

echo "== final status"
curl -sf "$URL/admin/rebuild/status" -H "Authorization: Bearer $(id_token)"
echo
