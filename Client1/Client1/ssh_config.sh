#!/bin/bash

# ssh_config.sh - Configure SSH key-based auth and disable password login for dev_lead1
set -e

KEY_DIR="/home/dev_lead1/.ssh"
KEY_FILE="$KEY_DIR/id_ed25519"
PUB_KEY="$KEY_FILE.pub"
AUTHORIZED_KEYS="$KEY_DIR/authorized_keys"

# IPs for VM2 and VM3
VM2_IP="192.168.244.129"
VM3_IP="192.168.244.130"

# Ensure dev_lead1 has .ssh directory
sudo -u dev_lead1 mkdir -p "$KEY_DIR"
sudo -u dev_lead1 chmod 700 "$KEY_DIR"

# Generate SSH keys only if missing
if [ ! -f "$KEY_FILE" ]; then
    echo "Generating SSH keys for dev_lead1..."
    sudo -u dev_lead1 ssh-keygen -t ed25519 -N "" -f "$KEY_FILE" > /dev/null 2>&1
    echo "SSH keys generated for dev_lead1."
else
    echo "SSH keys already exist for dev_lead1. Skipping generation."
fi

# Deploy public key to authorized_keys
sudo cp "$PUB_KEY" "$AUTHORIZED_KEYS"
sudo -u dev_lead1 chmod 600 "$AUTHORIZED_KEYS"

# Prepare temporary key for copying
TEMP_KEY="/tmp/dev_lead1_key"
sudo cp "$KEY_FILE" "$TEMP_KEY"
sudo chmod 644 "$TEMP_KEY"

# Use sshpass to automate SCP
echo "Copying private key to VM2 and VM3..."
sshpass -p '123' scp "$TEMP_KEY" "vm2@$VM2_IP:/tmp/" > /dev/null 2>&1 && echo "Key copied to VM2." || echo "Failed to copy to VM2."
sshpass -p '123' scp "$TEMP_KEY" "vm3@$VM3_IP:/tmp/" > /dev/null 2>&1 && echo "Key copied to VM3." || echo "Failed to copy to VM3."

# Disable password authentication for dev_lead1
SSHD_CONFIG="/etc/ssh/sshd_config"
if ! grep -q "Match User dev_lead1" "$SSHD_CONFIG"; then
    echo -e "\nMatch User dev_lead1\n    PasswordAuthentication no" | sudo tee -a "$SSHD_CONFIG" > /dev/null
    echo "Password authentication disabled for dev_lead1."
else
    echo "SSH config already updated for dev_lead1."
fi

# Restart SSH service
echo "Restarting SSH service..."
sudo systemctl restart ssh

# Verify SSH configuration
echo "Verifying setup..."
sudo sshd -t && echo "SSH config syntax is valid."

# Cleanup
sudo rm -f "$TEMP_KEY"
