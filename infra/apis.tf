# Cloud Build deliberately absent: images are built on the GitHub runner.
resource "google_project_service" "apis" {
  for_each = toset([
    "run.googleapis.com",
    "iamcredentials.googleapis.com",
    "secretmanager.googleapis.com",
    "artifactregistry.googleapis.com",
    "cloudresourcemanager.googleapis.com",
  ])

  service            = each.key
  disable_on_destroy = false
}
