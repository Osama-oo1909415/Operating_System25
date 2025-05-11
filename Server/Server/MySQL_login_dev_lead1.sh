#!/bin/bash
# MySQL_login_dev_lead1.sh - Create dev_lead1 and verify access

# Exit on errors
set -e

# MySQL root password = 123
MYSQL_ROOT_PASS="User@112233" 

# Create dev_lead1 and grant privileges, and creates database
echo "Creating user dev_lead1..."
sudo mysql << EOF
CREATE USER IF NOT EXISTS 'dev_lead1'@'localhost' IDENTIFIED BY '123';
CREATE DATABASE IF NOT EXISTS dev_db;
GRANT SELECT, INSERT, UPDATE, DELETE ON dev_db.* TO 'dev_lead1'@'localhost';
FLUSH PRIVILEGES;
EOF

# Verify authentication and privileges
echo "Verifying dev_lead1 access..."
mysql -u dev_lead1 -p123 << EOF
SELECT USER(), CURRENT_USER();
SHOW GRANTS;
SHOW DATABASES;
USE dev_db;
SHOW TABLES;
EOF

# Enable audit logging and restart myQSL services
if ! sudo grep -q "general_log" /etc/mysql/mysql.conf.d/mysqld.cnf; then
  echo "Enabling audit logging..."
  sudo tee -a /etc/mysql/mysql.conf.d/mysqld.cnf > /dev/null <<EOL
general_log = 1
general_log_file = /var/log/mysql_audit.log
EOL
  sudo systemctl restart mysql
fi

echo "Audit logs stored in /var/log/mysql_audit.log"

