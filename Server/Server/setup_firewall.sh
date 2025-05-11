#!/bin/bash

# setup_firewall.sh - Enable firewall and allow SSH + custom app port

echo "Setting up firewall..."
sudo ufw allow OpenSSH
sudo ufw allow 2500/tcp

# Enable firewall (will block all other traffic unless explicitly allowed)
echo "Enabling firewall..."
sudo ufw enable

# Show active rules
echo "Firewall status:"
sudo ufw status verbose
