#!/bin/bash
# quota_check.sh - Monitor disk usage for dev_lead1 and ops_lead1

LOG_FILE="/home/vm3/quota.log"
SHARED_DIR="/mnt/shared"

check_quota() {
    local user=$1
    local hard_limit=$2
    local warn_limit=$3
    local user_dir="$SHARED_DIR/$user"

    # Check if directory exists
    if [ ! -d "$user_dir" ]; then
        echo "$(date): Directory $user_dir does not exist." >> "$LOG_FILE"
        return
    fi

    # Get usage in GB
    usage=$(du -sb "$user_dir" | awk '{print $1}' 2>/dev/null)
    if [[ -z "$usage" || "$usage" == "" ]]; then
        echo "$(date): Failed to retrieve usage for $user_dir" >> "$LOG_FILE"
        return
    fi

    usage_gb=$(echo "scale=2; $usage / 1024 / 1024" | bc)

    if (( $(echo "$usage_gb > $hard_limit" | bc -l) )); then
        echo "$(date): Quota exceeded for $user: $usage_gb GB used (hard limit: $hard_limit GB)" >> "$LOG_FILE"
    elif (( $(echo "$usage_gb > $warn_limit" | bc -l) )); then
        echo "$(date): Quota warning for $user: $usage_gb GB used (warning at: $warn_limit GB)" >> "$LOG_FILE"
    else
        echo "$(date): $user within quota: $usage_gb GB used" >> "$LOG_FILE"
    fi
}

# Check quotas for dev_lead1 and ops_lead1
check_quota "dev_lead1" 5 4
check_quota "ops_lead1" 3 2
