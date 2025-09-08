# Multi-Auth Infrastructure Deployment

This directory contains **Terraform** and **Ansible** scripts to deploy the database infrastructure required by the Multi-Auth persistence adapters.

## 🎯 **What Gets Deployed**

### **📊 Databases**
- **PostgreSQL** (AWS RDS) - Primary relational database
- **MongoDB** (AWS DocumentDB) - Document-based storage  
- **Kafka** (AWS MSK) - Event streaming for audit logs
- **Firestore** (Google Cloud) - Real-time NoSQL database

### **🛡️ Security Features**
- **VPC with private subnets** for database isolation
- **Security groups** restricting access to application only
- **TLS/SSL encryption** for all database connections
- **Backup and monitoring** enabled by default

## 🚀 **Quick Start**

### **1. Prerequisites**
```bash
# Install required tools
brew install terraform ansible awscli

# Configure AWS credentials
aws configure

# Set up Google Cloud (for Firestore)
gcloud auth application-default login
```

### **2. Deploy Infrastructure**
```bash
# Simple deployment
./deploy.sh dev

# Or with custom environment
./deploy.sh staging

# Production deployment
./deploy.sh prod
```

### **3. Manual Terraform Deployment**
```bash
cd terraform

# Initialize
terraform init

# Plan deployment
terraform plan -var="environment=dev" -var="db_password=YourSecurePassword123!"

# Apply changes
terraform apply
```

## 📋 **Configuration**

### **Environment Variables**
```bash
# Required
export DB_PASSWORD="YourSecurePassword123!"

# Optional (for Firestore)
export GOOGLE_CLOUD_PROJECT="your-gcp-project"

# For SendGrid/Twilio integration
export SENDGRID_API_KEY="your-sendgrid-key"
export TWILIO_ACCOUNT_SID="your-twilio-sid"
export TWILIO_AUTH_TOKEN="your-twilio-token"
export TWILIO_FROM_NUMBER="+1234567890"
```

### **Terraform Variables**
Create `terraform/terraform.tfvars`:
```hcl
environment = "dev"
project_name = "multi-auth"
region = "us-east-1"
db_password = "YourSecurePassword123!"

# Optional customizations
postgres_instance_class = "db.t3.micro"  # Free tier
mongodb_instance_class = "db.t3.medium"
kafka_instance_type = "kafka.t3.small"
```

## 🔧 **Using with Multi-Auth**

### **1. Get Connection Strings**
```bash
cd terraform
terraform output postgres_connection_string
terraform output mongodb_connection_string
terraform output kafka_bootstrap_brokers_tls
```

### **2. Configure Multi-Auth Application**
```kotlin
// Use the PostgreSQL adapter
val postgresAdapter = PersistenceAdapters.createPostgreSQL(
    connectionString = "postgresql://user:pass@endpoint:5432/multiauth"
)

// Use the MongoDB adapter  
val mongoAdapter = PersistenceAdapters.createMongoDB(
    connectionString = "mongodb://user:pass@endpoint:27017/multiauth",
    databaseName = "multiauth"
)

// Use the Kafka event store
val kafkaAdapter = PersistenceAdapters.createKafkaEventStore(
    bootstrapServers = "broker1:9092,broker2:9092",
    topicPrefix = "multi-auth-dev"
)
```

## 🎛️ **Infrastructure Components**

### **AWS Resources**
- **VPC** with private subnets across 2 AZs
- **RDS PostgreSQL** with automated backups
- **DocumentDB** (MongoDB-compatible) cluster
- **MSK Kafka** cluster with TLS encryption
- **Security Groups** with minimal required access
- **CloudWatch Logs** for monitoring

### **Google Cloud Resources**
- **Firestore Database** in native mode
- **IAM permissions** for service access

## 💰 **Cost Estimates**

### **Development Environment**
- **PostgreSQL (db.t3.micro)**: ~$15/month
- **DocumentDB (db.t3.medium)**: ~$50/month  
- **Kafka (kafka.t3.small x2)**: ~$60/month
- **Firestore**: Pay-per-use (minimal for testing)
- **Total**: ~$125/month for full dev environment

### **Production Environment**
- Scale instance types based on load
- Enable deletion protection
- Increase backup retention
- Add read replicas if needed

## 🔧 **Customization**

### **Different Database Providers**
```hcl
# Use PostgreSQL only
# Comment out mongodb and kafka resources in main.tf

# Use MongoDB Atlas instead of DocumentDB
# Replace aws_docdb_* resources with mongodbatlas provider

# Use Confluent Cloud instead of AWS MSK
# Replace aws_msk_* resources with confluent provider
```

### **Environment-Specific Configurations**
```hcl
# Development
postgres_instance_class = "db.t3.micro"
enable_deletion_protection = false
backup_retention_days = 3

# Production
postgres_instance_class = "db.r6g.large"
enable_deletion_protection = true
backup_retention_days = 30
```

## 🛠️ **Troubleshooting**

### **Common Issues**
1. **AWS credentials not configured**
   ```bash
   aws configure
   # Or set AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
   ```

2. **Terraform state conflicts**
   ```bash
   terraform refresh
   terraform plan
   ```

3. **Database connection issues**
   ```bash
   # Test PostgreSQL connection
   psql "$(terraform output -raw postgres_connection_string)" -c "SELECT 1;"
   
   # Test MongoDB connection
   mongosh "$(terraform output -raw mongodb_connection_string)"
   ```

## 🔐 **Security Best Practices**

1. **Secure your terraform state**
   ```hcl
   # Use remote state backend
   terraform {
     backend "s3" {
       bucket = "your-terraform-state-bucket"
       key    = "multi-auth/terraform.tfstate"
       region = "us-east-1"
     }
   }
   ```

2. **Use AWS Secrets Manager for passwords**
3. **Enable VPC Flow Logs for network monitoring**
4. **Set up CloudTrail for audit logging**
5. **Use IAM roles instead of access keys when possible**

## 📊 **Monitoring**

The infrastructure includes:
- **CloudWatch metrics** for all databases
- **Performance Insights** for PostgreSQL
- **CloudWatch Logs** for Kafka
- **Automated alerting** (configure SNS topics)

## 🧹 **Cleanup**

```bash
# Destroy all infrastructure
cd terraform
terraform destroy

# Or use Ansible
ansible-playbook deploy-databases.yml -e "state=absent"
```

---

**This infrastructure perfectly matches the Multi-Auth persistence adapters and provides a production-ready database backend!** 🎯
