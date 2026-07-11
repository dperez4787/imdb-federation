# The Mongo URI secret is owned by the imdb-data-pipeline stack; this repo only
# grants its runtime SA read access. (Future: dedicated read-only Atlas user.)
data "google_secret_manager_secret" "imdb_mongodb_uri" {
  secret_id = "IMDB_MONGODB_URI"
}

resource "google_secret_manager_secret_iam_member" "runtime_reads_uri" {
  secret_id = data.google_secret_manager_secret.imdb_mongodb_uri.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.runtime.email}"
}

# OMDb API key for Title.imgUrl (subgraph-titles). Owned by this stack; the
# VALUE is added out-of-band (never in Terraform state):
#   printf '<key>' | gcloud secrets versions add OMDB_API_KEY --data-file=-
resource "google_secret_manager_secret" "omdb_api_key" {
  secret_id = "OMDB_API_KEY"

  replication {
    auto {}
  }

  depends_on = [google_project_service.apis]
}

resource "google_secret_manager_secret_iam_member" "runtime_reads_omdb" {
  secret_id = google_secret_manager_secret.omdb_api_key.id
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.runtime.email}"
}
