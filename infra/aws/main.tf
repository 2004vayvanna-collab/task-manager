terraform {
  required_version = ">= 1.6.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}
provider "aws" {
  region = var.region
}
variable "region" {
  type    = string
  default = "eu-central-1"
}
variable "ssh_public_key" {
  description = "Contents of your public SSH key, never a private key"
  type        = string
}
variable "admin_cidr" {
  description = "Your public IPv4 address with /32, for SSH only"
  type        = string
  validation {
    condition     = can(cidrnetmask(var.admin_cidr)) && can(regex("/32$", var.admin_cidr))
    error_message = "Use a single IPv4 address with /32."
  }
}
variable "k3s_version" {
  description = "Explicit K3s release to install, e.g. a supported v1.xx.y+k3s1 release"
  type        = string
  validation {
    condition     = can(regex("^v1\\.[0-9]+\\.[0-9]+\\+k3s[0-9]+$", var.k3s_version))
    error_message = "Use a full K3s release version."
  }
}
resource "aws_vpc" "lab" {
  cidr_block           = "10.42.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true
  tags = { Name = "task-manager-lab" }
}
resource "aws_subnet" "lab" {
  vpc_id                  = aws_vpc.lab.id
  cidr_block              = "10.42.1.0/24"
  map_public_ip_on_launch  = true
}
resource "aws_internet_gateway" "lab" {
  vpc_id = aws_vpc.lab.id
}
resource "aws_route_table" "lab" {
  vpc_id = aws_vpc.lab.id
  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.lab.id
  }
}
resource "aws_route_table_association" "lab" {
  subnet_id      = aws_subnet.lab.id
  route_table_id = aws_route_table.lab.id
}
resource "aws_security_group" "lab" {
  name_prefix = "task-manager-"
  vpc_id      = aws_vpc.lab.id
  ingress {
    description = "SSH from your computer"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.admin_cidr]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}
resource "aws_key_pair" "lab" {
  key_name_prefix = "task-manager-"
  public_key      = var.ssh_public_key
}
resource "aws_instance" "lab" {
  ami                         = data.aws_ami.ubuntu.id
  instance_type               = "t3.medium"
  subnet_id                   = aws_subnet.lab.id
  vpc_security_group_ids      = [aws_security_group.lab.id]
  key_name                    = aws_key_pair.lab.key_name
  user_data_replace_on_change = true
  user_data = <<-SCRIPT
    #!/bin/bash
    set -euo pipefail
    curl -fsSL https://get.k3s.io -o /tmp/install-k3s.sh
    INSTALL_K3S_VERSION='${var.k3s_version}' sh /tmp/install-k3s.sh --disable traefik --write-kubeconfig-mode 600 --cluster-cidr 10.50.0.0/16 --service-cidr 10.51.0.0/16
  SCRIPT
  metadata_options {
    http_tokens = "required"
  }
  root_block_device {
    volume_size = 30
    volume_type = "gp3"
    encrypted   = true
  }
  tags = { Name = "task-manager-lab" }
  depends_on = [aws_route_table_association.lab]
}
output "public_ip" {
  value = aws_instance.lab.public_ip
}
