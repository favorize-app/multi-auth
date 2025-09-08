#!/bin/bash

# Multi-Auth Infrastructure Deployment Script
# Deploys PostgreSQL, MongoDB, Kafka, and Firestore for Multi-Auth system

set -e

# Configuration
ENVIRONMENT=${1:-dev}
PROJECT_NAME="multi-auth"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "🚀 Multi-Auth Infrastructure Deployment"
echo "Environment: $ENVIRONMENT"
echo "Project: $PROJECT_NAME"
echo ""

# Check prerequisites
echo "🔍 Checking prerequisites..."

if ! command -v terraform &> /dev/null; then
    echo "❌ Terraform not found. Please install Terraform first."
    echo "   Visit: https://developer.hashicorp.com/terraform/downloads"
    exit 1
fi

if ! command -v ansible-playbook &> /dev/null; then
    echo "❌ Ansible not found. Please install Ansible first."
    echo "   Run: pip install ansible"
    exit 1
fi

if ! command -v aws &> /dev/null; then
    echo "⚠️  AWS CLI not found. Some features may not work."
    echo "   Install: https://aws.amazon.com/cli/"
fi

echo "✅ Prerequisites check completed"
echo ""

# Set database password
if [ -z "$DB_PASSWORD" ]; then
    echo "🔐 Database password not set in environment."
    read -s -p "Enter database password (min 8 chars): " DB_PASSWORD
    echo ""
    export DB_PASSWORD
fi

# Validate password
if [ ${#DB_PASSWORD} -lt 8 ]; then
    echo "❌ Database password must be at least 8 characters long"
    exit 1
fi

echo "✅ Database password configured"
echo ""

# Deploy with Ansible
echo "🚀 Starting deployment with Ansible..."
cd "$SCRIPT_DIR/ansible"

ansible-playbook deploy-databases.yml \
    -e "environment=$ENVIRONMENT" \
    -e "project_name=$PROJECT_NAME" \
    -e "db_password=$DB_PASSWORD" \
    -e "auto_approve=false" \
    --ask-become-pass 2>/dev/null || \
ansible-playbook deploy-databases.yml \
    -e "environment=$ENVIRONMENT" \
    -e "project_name=$PROJECT_NAME" \
    -e "db_password=$DB_PASSWORD" \
    -e "auto_approve=false"

echo ""
echo "🎉 Deployment completed!"
echo ""
echo "📋 Next steps:"
echo "1. Review the generated terraform plan"
echo "2. Apply terraform changes: cd terraform && terraform apply"
echo "3. Initialize databases with: psql \$POSTGRES_CONNECTION_STRING -f init-multiauth-databases.sql"
echo "4. Configure your Multi-Auth application with the generated config"
echo ""
echo "📁 Generated files:"
echo "- terraform/terraform.tfvars (Terraform variables)"
echo "- multiauth-infrastructure-config.yml (Application config)"
echo "- multiauth-secrets.env (Environment variables)"
echo "- init-multiauth-databases.sql (Database schema)"
echo ""
echo "🔐 Security reminder:"
echo "- Keep terraform.tfvars and multiauth-secrets.env secure"
echo "- Use proper secret management in production"
echo "- Enable deletion protection for production databases"
