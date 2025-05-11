#!/bin/bash
# system_monitor.sh - Monitor system metrics and service statuses

# Exit on errors
set -e

# Create log directory if missing
LOG_DIR="/var/operations/monitoring"
sudo mkdir -p $LOG_DIR
LOG_FILE="$LOG_DIR/metrics_$(date +'%Y%m%d_%H%M%S').log"

# Install required tools if missing
if ! command -v iostat &> /dev/null; then
    echo "Installing sysstat for disk I/O monitoring..."
    sudo apt update && sudo apt install -y sysstat
fi

# Collect metrics
echo "=== System Metrics ($(date)) ===" | sudo tee -a $LOG_FILE

# CPU/Memory usage
echo "---- CPU/Memory ----" | sudo tee -a $LOG_FILE
top -b -n 2 -d 0.1 | grep -E "CPU|MiB Mem" | tail -n 2 | sudo tee -a $LOG_FILE

# Disk I/O
echo -e "\n---- Disk I/O ----" | sudo tee -a $LOG_FILE
iostat -dx 1 2 | grep -E "Device|sda" | tail -n 2 | sudo tee -a $LOG_FILE

# Top 5 processes by CPU
echo -e "\n---- Top 5 Processes ----" | sudo tee -a $LOG_FILE
ps -eo %cpu,pid,cmd --sort=-%cpu | head -n 6 | sudo tee -a $LOG_FILE

# Service status checks
echo -e "\n---- Service Status ----" | sudo tee -a $LOG_FILE
check_service() {
    service=$1
    status=$(systemctl is-active $service 2>&1)
    echo "$service: $status" | sudo tee -a $LOG_FILE
    if [ "$status" != "active" ]; then
        echo "ALERT: $service failed. Restarting..." | sudo tee -a $LOG_FILE
        sudo systemctl restart $service && echo "Restarted $service" | sudo tee -a $LOG_FILE
    fi
}

check_service mysql
check_service ssh
check_service apache2 2>/dev/null || true  # Check Apache if installed

# Set log file permissions
sudo chmod 644 $LOG_FILE

echo "Metrics logged to: $LOG_FILE"
