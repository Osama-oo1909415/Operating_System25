#!/bin/bash
# resource_report.sh - Gather system resource information and copy to VM1

REPORT_FILE="/tmp/resource_report_$(date +%Y%m%d_%H%M%S).log"
VM1_IP="192.168.244.128"
VM1_USER="vm1"
VM1_DEST="/home/$VM1_USER/reports/"

# Gather process tree
echo "=== Process Tree ===" > "$REPORT_FILE"
pstree >> "$REPORT_FILE"

# Gather zombie processes
echo "=== Zombie Processes ===" >> "$REPORT_FILE"
ps aux | grep 'Z' >> "$REPORT_FILE"

# CPU/Memory Usage
echo "=== CPU and Memory Usage ===" >> "$REPORT_FILE"
top -bn1 | head -n 10 >> "$REPORT_FILE"

# Top 5 resource-consuming processes
echo "=== Top 5 Resource-Consuming Processes ===" >> "$REPORT_FILE"
ps -eo pid,ppid,cmd,%mem,%cpu --sort=-%mem | head -n 6 >> "$REPORT_FILE"

# Securely copy the report to VM1 using SSH key
scp -i ~/.ssh/dev_lead1_key "$REPORT_FILE" "$VM1_USER@$VM1_IP:$VM1_DEST"

# Clean up
rm "$REPORT_FILE"
