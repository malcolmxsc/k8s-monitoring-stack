#!/bin/bash

# Multi-model continuous traffic generator for dashboard testing

MODELS=("gpt-4o" "gpt-4.0" "claude-3.5-sonnet" "claude-3-opus" "gemini-1.5-pro" "gemini-2.0-flash" "gpt-3.5-turbo" "llama-3.3-70b")
USERS=("alice.smith" "bob.johnson" "carol.williams" "david.brown" "emma.jones")
REGIONS=("us-west-1" "us-west-2" "us-east-1" "eu-west-1" "eu-central-1" "ap-southeast-1" "ap-northeast-1")

echo "🚀 Starting multi-model continuous traffic..."
echo "📊 Generating requests for ${#MODELS[@]} different models"
echo "⏹️  Press Ctrl+C to stop"
echo ""

count=0
while true; do
    count=$((count + 1))
    
    # Random selection
    model=${MODELS[$RANDOM % ${#MODELS[@]}]}
    user=${USERS[$RANDOM % ${#USERS[@]}]}
    region=${REGIONS[$RANDOM % ${#REGIONS[@]}]}
    
    # Send request
    response=$(curl -s --fail --connect-timeout 2 --max-time 5 --retry 2 --retry-delay 0.5 -w "\n%{http_code}" -X POST http://localhost:8080/generate \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $user" \
        -H "X-Region: $region" \
        -H "X-Model: $model" \
        -d "{\"prompt\": \"test request $count\", \"model\": \"$model\"}")
    
    status=$(echo "$response" | tail -n1)
    
    if [ "$status" = "200" ]; then
        echo "[$count] ✅ $model | $region | $user"
    else
        echo "[$count] ❌ $model | $region | $user | Status: $status"
    fi
    
    # Random delay between ~1.0 and ~3.0 seconds (portable, no bc dependency)
    sleep "$(awk 'BEGIN{srand(); printf "%.1f", 1+rand()*2}')"
done
