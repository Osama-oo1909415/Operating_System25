#!/bin/bash
# file_audit.sh - Monitor file changes in /projects/development/ and log to /var/log/file_changes.log

# Exit on errors
set -e

# Tool installations
if ! command -v inotifywait &> /dev/null; then
    echo "Installing inotify-tools..."
    sudo apt update && sudo apt install -y inotify-tools
fi

# Log setup
LOG_FILE="/var/log/file_changes.log"
sudo touch "$LOG_FILE"
sudo chmod 644 "$LOG_FILE"

# Monitoring setup and getting current user
CURRENT_USER=$(whoami)

echo "Starting file monitoring for /projects/development/..."
echo "Logs: $LOG_FILE"

inotifywait -m -r -q \
  -e create,modify,delete \
  --timefmt '%Y-%m-%d %H:%M:%S' \
  --format "User: $CURRENT_USER | File: %w%f | Event: %e | Time: %T" \
  /projects/development/ | while read -r line; do
    echo "$line" | sudo tee -a "$LOG_FILE" > /dev/null
done


