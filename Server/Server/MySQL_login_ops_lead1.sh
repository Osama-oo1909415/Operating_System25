#!/bin/bash
# MySQL_login_ops_lead1.sh - Create ops_lead1 and verify access

# Exit on errors
set -e

# MySQL root password = 123 aswell
MYSQL_ROOT_PASS="User@112233"

# Create ops_lead1 and grant privileges
echo "Creating user ops_lead1..."
sudo mysql << EOF
CREATE USER IF NOT EXISTS 'ops_lead1'@'localhost' IDENTIFIED BY '123';
CREATE DATABASE IF NOT EXISTS ops_db;
GRANT SELECT ON ops_db.* TO 'ops_lead1'@'localhost';
GRANT PROCESS ON *.* TO 'ops_lead1'@'localhost';  # For monitoring server status
FLUSH PRIVILEGES;
EOF


# Verify authentication and privileges
echo "Verifying ops_lead1 access..."
mysql -u ops_lead1 -p123 << EOF
SELECT USER(), CURRENT_USER();
SHOW GRANTS;
SHOW DATABASES;
USE ops_db;
SHOW TABLES;
EOF

echo "Audit logs stored in /var/log/mysql_audit.log"

