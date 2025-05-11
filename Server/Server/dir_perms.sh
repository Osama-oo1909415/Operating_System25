#!/bin/bash
# dir_perms.sh - Configure directory structure and ACLs

# Exit on errors
set -e

# Install ACL tools if missing
if ! command -v setfacl &> /dev/null; then
    echo "Installing ACL tools..."
    sudo apt update
    sudo apt install -y acl
fi

# Create nested directories
echo "Creating directory structure..."
sudo mkdir -p /projects/development/{source,builds} /var/operations/{monitoring,reports}

# Set ACLs for developers group
echo "Setting ACLs for developers group..."
# Full access to /projects/development/source
sudo setfacl -R -m g:developers:rwx -m d:g:developers:rwx /projects/development/source
# Read-only for /projects/development/builds
sudo setfacl -R -m g:developers:r-x -m d:g:developers:r-x /projects/development/builds

# Set ACLs for monitoring group 
echo "Setting ACLs for monitoring group..."
sudo setfacl -R -m g:monitoring:r-x -m d:g:monitoring:r-x /var/operations/reports

# Verifications
echo "Verifying permissions..."
echo "=== /projects/development/source ==="
getfacl /projects/development/source | grep -E "group:developers|default"
echo "=== /projects/development/builds ==="
getfacl /projects/development/builds | grep -E "group:developers|default"
echo "=== /var/operations/reports ==="
getfacl /var/operations/reports | grep -E "group:monitoring|default"

echo "Directory permissions and ACLs configured successfully."


