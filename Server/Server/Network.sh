#!/bin/bash
# Network.sh - Performs a one-time connectivity check to clients
# Usage: Called by Server.java for validation. Exits 0 on success, 1 on failure.

# IPs of clients
CLIENT1_IP="192.168.244.129"  # VM2 (Client1)
CLIENT2_IP="192.168.244.130"  # VM3 (Client2)

# Ping each client once (-c 1) with a short timeout (-W 2, waits 2 seconds for reply)
ping -c 1 -W 2 "$CLIENT1_IP" > /dev/null 2>&1 # Suppress ping output for validation
if [ $? -ne 0 ]; then
    echo "[ERROR] Validation Failed: Cannot ping Client1VM ($CLIENT1_IP)"
    exit 1 # Exit immediately on first failure
fi

ping -c 1 -W 2 "$CLIENT2_IP" > /dev/null 2>&1 # Suppress ping output
if [ $? -ne 0 ]; then
    echo "[ERROR] Validation Failed: Cannot ping Client2VM ($CLIENT2_IP)"
    exit 1 # Exit on second failure
fi

# If both pings succeeded
echo "[SUCCESS] Validation: Both clients reachable."
exit 0
