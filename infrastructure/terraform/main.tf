terraform {
  required_version = ">= 1.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }
}

# Variables
variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "multi-auth"
}

variable "region" {
  description = "AWS region"
  type        = string
  default     = "us-east-1"
}

variable "db_password" {
  description = "Database password"
  type        = string
  sensitive   = true
}

# Local values
locals {
  common_tags = {
    Environment = var.environment
    Project     = var.project_name
    ManagedBy   = "terraform"
  }
}

# AWS Provider
provider "aws" {
  region = var.region
  
  default_tags {
    tags = local.common_tags
  }
}

# Google Provider (for Firestore)
provider "google" {
  project = var.project_name
  region  = var.region
}

# VPC for database security
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-vpc"
  })
}

# Subnets for database
resource "aws_subnet" "private" {
  count             = 2
  vpc_id            = aws_vpc.main.id
  cidr_block        = "10.0.${count.index + 1}.0/24"
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-private-${count.index + 1}"
  })
}

# Internet Gateway
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-igw"
  })
}

# Route table
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-rt-private"
  })
}

# Route table associations
resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# Security group for databases
resource "aws_security_group" "database" {
  name_prefix = "${var.project_name}-${var.environment}-db-"
  vpc_id      = aws_vpc.main.id

  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [aws_vpc.main.cidr_block]
    description = "PostgreSQL access from VPC"
  }

  ingress {
    from_port   = 27017
    to_port     = 27017
    protocol    = "tcp"
    cidr_blocks = [aws_vpc.main.cidr_block]
    description = "MongoDB access from VPC"
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-db-sg"
  })
}

# PostgreSQL RDS Instance
resource "aws_db_subnet_group" "postgres" {
  name       = "${var.project_name}-${var.environment}-postgres"
  subnet_ids = aws_subnet.private[*].id

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-postgres-subnet-group"
  })
}

resource "aws_db_instance" "postgres" {
  identifier = "${var.project_name}-${var.environment}-postgres"

  # Database configuration
  engine         = "postgres"
  engine_version = "15.4"
  instance_class = "db.t3.micro"  # Free tier eligible
  
  # Storage
  allocated_storage     = 20
  max_allocated_storage = 100
  storage_type         = "gp2"
  storage_encrypted    = true

  # Database settings
  db_name  = "multiauth"
  username = "multiauth_user"
  password = var.db_password

  # Network & Security
  vpc_security_group_ids = [aws_security_group.database.id]
  db_subnet_group_name   = aws_db_subnet_group.postgres.name
  
  # Backup & Maintenance
  backup_retention_period = 7
  backup_window          = "03:00-04:00"
  maintenance_window     = "sun:04:00-sun:05:00"
  
  # Performance & Monitoring
  performance_insights_enabled = true
  monitoring_interval         = 60
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  # Security
  deletion_protection = var.environment == "prod"
  skip_final_snapshot = var.environment != "prod"

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-postgres"
  })
}

# MongoDB Atlas (using mongodbatlas provider would be ideal, but using AWS DocumentDB as alternative)
resource "aws_docdb_subnet_group" "mongodb" {
  name       = "${var.project_name}-${var.environment}-mongodb"
  subnet_ids = aws_subnet.private[*].id

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-mongodb-subnet-group"
  })
}

resource "aws_docdb_cluster" "mongodb" {
  cluster_identifier      = "${var.project_name}-${var.environment}-mongodb"
  engine                 = "docdb"
  master_username        = "multiauth_user"
  master_password        = var.db_password
  backup_retention_period = 5
  preferred_backup_window = "07:00-09:00"
  skip_final_snapshot    = var.environment != "prod"
  
  vpc_security_group_ids = [aws_security_group.database.id]
  db_subnet_group_name   = aws_docdb_subnet_group.mongodb.name

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-mongodb"
  })
}

resource "aws_docdb_cluster_instance" "mongodb" {
  count              = 1
  identifier         = "${var.project_name}-${var.environment}-mongodb-${count.index}"
  cluster_identifier = aws_docdb_cluster.mongodb.id
  instance_class     = "db.t3.medium"

  tags = merge(local.common_tags, {
    Name = "${var.project_name}-${var.environment}-mongodb-${count.index}"
  })
}

# Kafka using AWS MSK
resource "aws_msk_cluster" "kafka" {
  cluster_name           = "${var.project_name}-${var.environment}-kafka"
  kafka_version          = "3.5.1"
  number_of_broker_nodes = 2

  broker_node_group_info {
    instance_type  = "kafka.t3.small"
    client_subnets = aws_subnet.private[*].id
    security_groups = [aws_security_group.database.id]
    
    storage_info {
      ebs_storage_info {
        volume_size = 20
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.kafka.name
      }
    }
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_log_group" "kafka" {
  name              = "/aws/msk/${var.project_name}-${var.environment}"
  retention_in_days = 7

  tags = local.common_tags
}

# Firestore (Google Cloud)
resource "google_firestore_database" "main" {
  project     = var.project_name
  name        = "${var.project_name}-${var.environment}"
  location_id = "us-central"
  type        = "FIRESTORE_NATIVE"

  depends_on = [
    google_project_service.firestore
  ]
}

resource "google_project_service" "firestore" {
  project = var.project_name
  service = "firestore.googleapis.com"

  disable_dependent_services = true
}

# Data sources
data "aws_availability_zones" "available" {
  state = "available"
}

# Outputs
output "postgres_endpoint" {
  description = "PostgreSQL RDS endpoint"
  value       = aws_db_instance.postgres.endpoint
  sensitive   = true
}

output "postgres_connection_string" {
  description = "PostgreSQL connection string for the adapter"
  value       = "postgresql://${aws_db_instance.postgres.username}:${var.db_password}@${aws_db_instance.postgres.endpoint}:${aws_db_instance.postgres.port}/${aws_db_instance.postgres.db_name}"
  sensitive   = true
}

output "mongodb_endpoint" {
  description = "MongoDB DocumentDB endpoint"
  value       = aws_docdb_cluster.mongodb.endpoint
  sensitive   = true
}

output "mongodb_connection_string" {
  description = "MongoDB connection string for the adapter"
  value       = "mongodb://${aws_docdb_cluster.mongodb.master_username}:${var.db_password}@${aws_docdb_cluster.mongodb.endpoint}:${aws_docdb_cluster.mongodb.port}/multiauth?ssl=true&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false"
  sensitive   = true
}

output "kafka_bootstrap_brokers" {
  description = "Kafka bootstrap brokers"
  value       = aws_msk_cluster.kafka.bootstrap_brokers_tls
}

output "firestore_project_id" {
  description = "Firestore project ID"
  value       = google_firestore_database.main.project
}
