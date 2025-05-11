#!/bin/bash
# user_setup.sh - Create users, groups, and assign roles
# Must be run with sudo

# Check for sudo/root privileges
if [[ $EUID -ne 0 ]]; then
   echo "This script must be run as root. Use 'sudo ./user_setup.sh'"
   exit 1
fi

# Define groups and users
groups=("developers" "dev_leads" "operations" "ops_admin" "monitoring")
users=("dev_lead1" "ops_lead1" "ops_monitor1")
passwords=("123" "123" "123")  # Replace with secure passwords in production

# Create groups if they don't exist
for group in "${groups[@]}"; do
    if ! getent group "$group" > /dev/null; then
        groupadd "$group"
        echo "[+] Group $group created."
    else
        echo "[ ] Group $group already exists."
    fi
done

# Create users if they don't exist and set passwords
for i in "${!users[@]}"; do
    user="${users[$i]}"
    pass="${passwords[$i]}"
    
    if id "$user" &>/dev/null; then
        echo "[ ] User $user already exists."
        continue
    fi
    
    useradd -m "$user"
    echo "$user:$pass" | chpasswd
    echo "[+] User $user created with password set."
done

# Assign nested group memberships
echo "[+] Assigning group memberships..."
usermod -aG developers,dev_leads dev_lead1
usermod -aG operations,ops_admin ops_lead1
usermod -aG operations,monitoring ops_monitor1

# Add dev_lead1 and ops_lead1 to sudo group
usermod -aG sudo dev_lead1
usermod -aG sudo ops_lead1
echo "[+] Users added to sudo group."

# Verify sudo access
echo "[+] Verifying sudo privileges:"
id dev_lead1 | grep -q 'sudo' && echo "✔ dev_lead1 has sudo access." || echo "✘ ERROR: dev_lead1 sudo setup failed."
id ops_lead1 | grep -q 'sudo' && echo "✔ ops_lead1 has sudo access." || echo "✘ ERROR: ops_lead1 sudo setup failed."

# Final validation
echo "[+] Final group memberships:"
echo "dev_lead1: $(id dev_lead1)"
echo "ops_lead1: $(id ops_lead1)"
echo "ops_monitor1: $(id ops_monitor1)"

echo "[+] Script completed successfully."
