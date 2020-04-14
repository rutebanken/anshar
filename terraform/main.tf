# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.12"
}

provider "google" {
  version = "~> 2.19"
}
provider "kubernetes" {
  load_config_file = var.load_config_file
}

# create service account
resource "google_service_account" "app_service_account" {
  account_id   = "${var.labels.team}-${var.labels.app}-sa"
  display_name = "${var.labels.team}-${var.labels.app} service account"
  project = var.gcp_project
}

# add service account as member to the bucket
resource "google_storage_bucket_iam_member" "storage_bucket_iam_member" {
  bucket = var.storage_bucket_name
  role   = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.app_service_account.email}"
}

# add service account as member to the pubsub
resource "google_project_iam_member" "project" {
  project = var.gcp_project
  role    = var.service_account_pubsub_role
  member = "serviceAccount:${google_service_account.app_service_account.email}"
}

# create key for service account
resource "google_service_account_key" "app_service_account_key" {
  service_account_id = google_service_account.app_service_account.name
}

  # Add SA key to to k8s
resource "kubernetes_secret" "app_service_account_credentials" {
  metadata {
    name      = "${var.labels.team}-${var.labels.app}-sa-key"
    namespace = var.kube_namespace
  }
  data = {
    "credentials.json" = "${base64decode(google_service_account_key.app_service_account_key.private_key)}"
  }
}

# Add anshar mqtt password
resource "kubernetes_secret" "ror-anshar-mqtt-secrets" {
  metadata {
  name      = "${var.labels.team}-${var.labels.app}-secrets"
  namespace = var.kube_namespace
  }

  data = {
  "mqtt-password"     = var.ror-anshar-mqtt-password
  }
}