#!/bin/bash

# fix_perms.sh - Create nested groups and assign users to roles

# Define groups and create them if they don’t exist
groups=("developers" "dev_leads" "operations" "ops_admin" "monitoring")
for group in "${groups[@]}"; do
    getent group "$group" > /dev/null || sudo groupadd "$group"
done

# Define users, passwords, and create them if missing
users=("dev_lead1" "ops_lead1" "ops_monitor1")
passwords=("123" "123" "123")

for i in "${!users[@]}"; do
    user="${users[$i]}"
    pass="${passwords[$i]}"
    if ! id "$user" &>/dev/null; then
        echo "Creating user $user..."
        sudo useradd -m "$user"
        echo "$user:$pass" | sudo chpasswd
        echo "User $user created with password set."
    else
        echo "User $user already exists."
    fi
done

# Assign nested group memberships
sudo usermod -aG developers,dev_leads dev_lead1
sudo usermod -aG operations,ops_admin ops_lead1
sudo usermod -aG operations,monitoring ops_monitor1

# Add to sudo group
sudo usermod -aG sudo dev_lead1
sudo usermod -aG sudo ops_lead1

# Output final validation
echo "Group memberships after setup:"
id dev_lead1
id ops_lead1
id ops_monitor1
