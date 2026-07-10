# The github-pool workload identity pool is shared infrastructure owned by the
# linear-example stack; this repo only adds its own provider scoped to itself.
# NOTE: display_name has a 32-char GCP limit.
resource "google_iam_workload_identity_pool_provider" "github_imdbfed" {
  workload_identity_pool_id          = "github-pool"
  workload_identity_pool_provider_id = "github-provider-imdbfed"
  display_name                       = "GitHub imdb-federation"
  description                        = "OIDC for dperez4787/imdb-federation GitHub Actions"

  attribute_mapping = {
    "google.subject"             = "assertion.sub"
    "attribute.repository"       = "assertion.repository"
    "attribute.repository_owner" = "assertion.repository_owner"
  }

  attribute_condition = "assertion.repository == '${local.github_repo}'"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}
