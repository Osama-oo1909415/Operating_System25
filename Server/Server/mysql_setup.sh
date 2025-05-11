#!/bin/bash
# mysql_setup.sh - Install MySQL and configure autostart

# Exit on errors
set -e

# Install MySQL Server
echo "Installing MySQL Server..."
sudo apt update
sudo apt install -y mysql-server

# Enable MySQL to start on boot
echo "Enabling MySQL autostart..."
sudo systemctl enable mysql

# Start MySQL
echo "Starting MySQL service..."
sudo systemctl start mysql

# Status verification
echo "Checking MySQL status..."
sudo systemctl status mysql

echo "MySQL setup complete. Service is configured to start automatically on boot."
