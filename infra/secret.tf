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
