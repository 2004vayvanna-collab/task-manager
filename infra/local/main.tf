terraform {
  required_version = ">= 1.6.0"
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.38"
    }
  }
}
variable "kubeconfig_path" {
  type    = string
  default = "~/.kube/config"
}
variable "context" {
  type    = string
  default = "minikube"
}
provider "kubernetes" {
  config_path    = pathexpand(var.kubeconfig_path)
  config_context = var.context
}
resource "kubernetes_namespace_v1" "lab" {
  metadata {
    name = "task-manager"
    labels = {
      managed-by = "terraform"
    }
  }
  lifecycle {
    prevent_destroy = true
  }
}
output "namespace" {
  value = kubernetes_namespace_v1.lab.metadata[0].name
}
