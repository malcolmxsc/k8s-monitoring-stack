#!/bin/bash

# Continuous traffic generator for LLM observability testing
# Generates requests across multiple models with even distribution

MODELS=(
  "gpt-4o"
  "gpt-3.5-turbo"
  "claude-3.5-sonnet"
  "claude-3-opus"
  "gemini-2.0-flash"
  "gemini-1.5-pro"
  "llama-3.3-70b"
  "gpt-4.0"
)

echo "🚀 Starting continuous traffic generation..."
echo "Press Ctrl+C to stop"
echo ""

# Counter for requests
REQUEST_COUNT=0
ROUND=0

while true; do
  # Cycle through all models in order for even distribution
  for MODEL in "${MODELS[@]}"; do
    # Generate random prompt
    PROMPT_NUM=$((RANDOM % 1000))
    PROMPT="Generate a response for test prompt #${PROMPT_NUM}"
    
    # Make the request
    curl -s --fail --connect-timeout 2 --max-time 5 --retry 2 --retry-delay 0.5 -X POST http://localhost:8080/generate \
      -H "Content-Type: application/json" \
      -d "{\"prompt\": \"${PROMPT}\", \"model\": \"${MODEL}\"}" \
      > /dev/null
    
    REQUEST_COUNT=$((REQUEST_COUNT + 1))
    
    # Print progress every 20 requests
    if [ $((REQUEST_COUNT % 20)) -eq 0 ]; then
      echo "✓ Generated ${REQUEST_COUNT} requests (Round $((ROUND + 1)))..."
    fi
    
    # Small delay between requests (0.3-0.8 seconds)
    DELAY=$(awk -v min=0.3 -v max=0.8 'BEGIN{srand(); print min+rand()*(max-min)}')
    sleep $DELAY
  done
  
  ROUND=$((ROUND + 1))
done
