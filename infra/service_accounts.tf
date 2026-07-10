# Two-SA split: the deploy SA (impersonated by GitHub Actions via WIF) pushes
# images and deploys; the runtime SA is the identity of the running services
# and can only read the Mongo URI secret.

resource "google_service_account" "deploy" {
  account_id   = "imdbfed-deploy"
  display_name = "imdb-federation GitHub Actions deploy"
}

resource "google_service_account" "runtime" {
  account_id   = "imdbfed-run"
  display_name = "imdb-federation Cloud Run runtime"
}

resource "google_project_iam_member" "deploy_ar_writer" {
  project = local.project_id
  role    = "roles/artifactregistry.writer"
  member  = "serviceAccount:${google_service_account.deploy.email}"
}

resource "google_project_iam_member" "deploy_run_admin" {
  project = local.project_id
  role    = "roles/run.admin"
  member  = "serviceAccount:${google_service_account.deploy.email}"
}

# actAs on the runtime SA only (not project-wide serviceAccountUser)
resource "google_service_account_iam_member" "deploy_act_as_runtime" {
  service_account_id = google_service_account.runtime.name
  role               = "roles/iam.serviceAccountUser"
  member             = "serviceAccount:${google_service_account.deploy.email}"
}

# GitHub Actions from this repo (and only this repo) may impersonate the deploy SA.
resource "google_service_account_iam_member" "deploy_wif" {
  service_account_id = google_service_account.deploy.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/projects/${local.project_number}/locations/global/workloadIdentityPools/github-pool/attribute.repository/${local.github_repo}"
}

# The deploy workflow's smoke tests mint per-service ID tokens as itself
# (gcloud --impersonate-service-account): self-impersonation via the IAM
# credentials API needs an explicit tokenCreator grant — not implied by
# WIF's workloadIdentityUser.
resource "google_service_account_iam_member" "deploy_self_token_creator" {
  service_account_id = google_service_account.deploy.name
  role               = "roles/iam.serviceAccountTokenCreator"
  member             = "serviceAccount:${google_service_account.deploy.email}"
}
