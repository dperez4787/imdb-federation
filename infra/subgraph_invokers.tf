# The subgraphs accept only IAM-authenticated invocations: deploy.yml ships
# them with --no-allow-unauthenticated, so Google's front end rejects direct
# calls before Spring Boot sees them. Invokers:
#   - cosmorouter-run:    the router's subgraphtoken module attaches per-audience
#                         Google-signed ID tokens to every subgraph request
#   - cosmorouter-deploy: cosmo-router CI introspects live subgraph SDLs to compose
#   - imdbfed-deploy:     this repo's post-deploy smoke tests
locals {
  subgraph_modules = ["titles", "names", "ratings", "episodes", "crew", "akas", "principals", "orchestrator"]
  subgraph_invokers = [
    "serviceAccount:cosmorouter-run@${local.project_id}.iam.gserviceaccount.com",
    "serviceAccount:cosmorouter-deploy@${local.project_id}.iam.gserviceaccount.com",
    "serviceAccount:${google_service_account.deploy.email}",
  ]
}

resource "google_cloud_run_v2_service_iam_member" "subgraph_invoker" {
  for_each = {
    for pair in setproduct(local.subgraph_modules, local.subgraph_invokers) :
    "${pair[0]}:${pair[1]}" => pair
  }

  project  = local.project_id
  location = local.region
  name     = "imdb-subgraph-${each.value[0]}"
  role     = "roles/run.invoker"
  member   = each.value[1]
}
