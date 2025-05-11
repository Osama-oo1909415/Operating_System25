#!/bin/bash

# login_audit.sh - Monitor failed SSH login attempts and block IPs after 3 failures

LOG_FILE="/var/log/ssh_attempts.log"
BLOCKED_IPS="/var/log/blocked_ips.log"
THRESHOLD=3
WATCH_INTERVAL=60

# Check if another instance is running
LOCK_FILE="/tmp/login_audit.lock"

if [ -f "$LOCK_FILE" ]; then
    PID=$(cat "$LOCK_FILE")
    if ps -p "$PID" > /dev/null; then
        echo "[$(date)] Script already running. Exiting."
        exit 1
    else
        echo "[$(date)] Stale lock file found. Removing..."
        rm -f "$LOCK_FILE"
    fi
fi

# Create lock file with current PID
echo $$ > "$LOCK_FILE"

# Cleanup on exit
trap 'rm -f "$LOCK_FILE"; exit' INT TERM EXIT

echo "[$(date)] Starting SSH login monitor..."

while true; do
    # Get failed logins from journalctl
    journalctl _SYSTEMD_UNIT=sshd.service --since "$WATCH_INTERVAL seconds ago" | \
        grep 'Failed password' | awk '{print $11}' | sort | uniq -c | while read count ip; do

        if [ "$count" -ge "$THRESHOLD" ]; then
            echo "$(date): Blocking IP $ip due to $count failed login attempts" >> "$LOG_FILE"

            # Block IP if not already blocked
            if ! iptables -L -n | grep -q "$ip"; then
                sudo iptables -A INPUT -s "$ip" -j DROP
                echo "$(date): Blocked IP $ip" >> "$BLOCKED_IPS"
            fi
        fi
    done

    sleep "$WATCH_INTERVAL"
done
