#!/bin/bash

# Load Generator for Observability Sandbox
# Generates realistic traffic with varied users, regions, and patterns

BASE_URL="${BASE_URL:-http://localhost:8080}"
COLORS="${COLORS:-true}"
SKIP_HEALTH_CHECK=false
PATTERN_OVERRIDE=""

# Color output
if [ "$COLORS" = true ]; then
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    RED='\033[0;31m'
    BLUE='\033[0;34m'
    NC='\033[0m' # No Color
else
    GREEN=''
    YELLOW=''
    RED=''
    BLUE=''
    NC=''
fi

# User pool (realistic usernames)
USERS=(
    "alice.smith"
    "bob.johnson"
    "carol.williams"
    "david.brown"
    "emma.jones"
    "frank.garcia"
    "grace.martinez"
    "henry.rodriguez"
    "iris.lopez"
    "jack.wilson"
    "karen.anderson"
    "leo.thomas"
    "maria.taylor"
    "nathan.moore"
    "olivia.jackson"
    "peter.martin"
    "quinn.lee"
    "rachel.perez"
    "steve.thompson"
    "tina.white"
)

# Region pool
REGIONS=(
    "us-east-1"
    "us-west-1"
    "us-west-2"
    "eu-west-1"
    "eu-central-1"
    "ap-southeast-1"
    "ap-northeast-1"
)

# Model pool
MODELS=(
    "gpt-4.0"
    "gpt-4o"
    "gpt-3.5-turbo"
    "claude-3.5-sonnet"
    "claude-3-opus"
    "claude-3-haiku"
    "gemini-2.0-flash"
    "gemini-1.5-pro"
    "llama-3.3-70b"
    "mistral-large"
)

# Prompt variations for different request patterns
PROMPTS=(
    "Write a short poem about clouds"
    "Explain quantum computing in simple terms"
    "Generate a product description for smart watch"
    "Create a haiku about programming"
    "Summarize the benefits of exercise"
    "Write a joke about developers"
    "Explain machine learning to a 5 year old"
    "Generate a catchy slogan for a coffee shop"
    "Describe the perfect vacation destination"
    "Write a motivational quote"
)

# Get random element from array
get_random() {
    local arr=("$@")
    echo "${arr[RANDOM % ${#arr[@]}]}"
}

# Make a request
make_request() {
    local user=$1
    local region=$2
    local prompt=$3
    local model=$4
    
    echo -e "${BLUE}[$(date '+%H:%M:%S')]${NC} User: ${GREEN}$user${NC} | Region: ${YELLOW}$region${NC} | Model: ${BLUE}$model${NC}"
    
    response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/generate" \
        -H "Content-Type: application/json" \
        -H "X-User-Id: $user" \
        -H "X-Region: $region" \
        -H "X-Model: $model" \
        -d "{\"prompt\":\"$prompt\"}")
    
    http_code=$(echo "$response" | tail -n 1)
    body=$(echo "$response" | sed '$d')
    
    if [ "$http_code" = "200" ]; then
        latency=$(echo "$body" | jq -r '.latencyMs // "N/A"')
        tokens=$(echo "$body" | jq -r '.respTokens // "N/A"')
        echo -e "  ${GREEN}✓${NC} Status: $http_code | Latency: ${latency}ms | Tokens: $tokens"
    else
        echo -e "  ${RED}✗${NC} Status: $http_code"
    fi
    echo ""
}

# Traffic patterns
pattern_steady() {
    echo -e "${YELLOW}═══ Starting Steady Traffic Pattern ═══${NC}"
    echo "Generating consistent load with varied users and regions..."
    echo ""
    
    for i in {1..20}; do
        user=$(get_random "${USERS[@]}")
        region=$(get_random "${REGIONS[@]}")
        prompt=$(get_random "${PROMPTS[@]}")
        model=$(get_random "${MODELS[@]}")
        
        make_request "$user" "$region" "$prompt" "$model"
        sleep 1
    done
}

pattern_burst() {
    echo -e "${YELLOW}═══ Starting Burst Traffic Pattern ═══${NC}"
    echo "Simulating traffic spikes with rapid requests..."
    echo ""
    
    for burst in {1..3}; do
        echo -e "${YELLOW}--- Burst $burst ---${NC}"
        for i in {1..5}; do
            user=$(get_random "${USERS[@]}")
            region=$(get_random "${REGIONS[@]}")
            prompt=$(get_random "${PROMPTS[@]}")
            model=$(get_random "${MODELS[@]}")
            
            make_request "$user" "$region" "$prompt" "$model" &
        done
        wait
        echo "Cooling down..."
        sleep 5
    done
}

pattern_regional() {
    echo -e "${YELLOW}═══ Starting Regional Traffic Pattern ═══${NC}"
    echo "Simulating region-specific load..."
    echo ""
    
    for region in "${REGIONS[@]}"; do
        echo -e "${YELLOW}--- Traffic from $region ---${NC}"
        for i in {1..3}; do
            user=$(get_random "${USERS[@]}")
            prompt=$(get_random "${PROMPTS[@]}")
            model=$(get_random "${MODELS[@]}")
            
            make_request "$user" "$region" "$prompt" "$model"
            sleep 0.5
        done
    done
}

pattern_user_session() {
    echo -e "${YELLOW}═══ Starting User Session Pattern ═══${NC}"
    echo "Simulating individual user sessions with multiple requests..."
    echo ""
    
    for session in {1..5}; do
        user=$(get_random "${USERS[@]}")
        region=$(get_random "${REGIONS[@]}")
        model=$(get_random "${MODELS[@]}")
        
        echo -e "${YELLOW}--- Session for $user from $region using $model ---${NC}"
        
        num_requests=$((RANDOM % 4 + 2))  # 2-5 requests per session
        for i in $(seq 1 $num_requests); do
            prompt=$(get_random "${PROMPTS[@]}")
            make_request "$user" "$region" "$prompt" "$model"
            sleep $((RANDOM % 3 + 1))  # 1-3 second delay between requests
        done
        
        echo "Session ended."
        echo ""
        sleep 2
    done
}

pattern_continuous() {
    echo -e "${YELLOW}═══ Starting Continuous Traffic Pattern ═══${NC}"
    echo "Running continuous varied traffic (Ctrl+C to stop)..."
    echo ""
    
    counter=1
    while true; do
        user=$(get_random "${USERS[@]}")
        region=$(get_random "${REGIONS[@]}")
        prompt=$(get_random "${PROMPTS[@]}")
        model=$(get_random "${MODELS[@]}")
        
        echo -e "${BLUE}Request #$counter${NC}"
        make_request "$user" "$region" "$prompt" "$model"
        
        # Variable delay (0.5-3 seconds)
        delay=$(awk "BEGIN {print (0.5 + rand() * 2.5)}")
        sleep "$delay"
        
        ((counter++))
    done
}

# Show menu
show_menu() {
    echo ""
    echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║   Observability Sandbox Load Generator    ║${NC}"
    echo -e "${GREEN}╔════════════════════════════════════════════╗${NC}"
    echo ""
    echo "Select a traffic pattern:"
    echo ""
    echo "  1) Steady Traffic      - Consistent load (20 requests, 1s interval)"
    echo "  2) Burst Traffic       - Traffic spikes (3 bursts of 5 parallel requests)"
    echo "  3) Regional Traffic    - Region-by-region load"
    echo "  4) User Sessions       - Realistic user sessions (2-5 requests each)"
    echo "  5) Continuous Traffic  - Non-stop varied traffic (Ctrl+C to stop)"
    echo "  6) All Patterns        - Run all patterns sequentially"
    echo "  7) Quick Test          - 5 quick varied requests"
    echo "  q) Quit"
    echo ""
    echo -n "Enter choice: "
}

quick_test() {
    echo -e "${YELLOW}═══ Quick Test ═══${NC}"
    for i in {1..5}; do
        user=$(get_random "${USERS[@]}")
        region=$(get_random "${REGIONS[@]}")
        prompt=$(get_random "${PROMPTS[@]}")
        model=$(get_random "${MODELS[@]}")
        make_request "$user" "$region" "$prompt" "$model"
    done
}

run_all_patterns() {
    pattern_steady
    echo ""
    sleep 2
    pattern_burst
    echo ""
    sleep 2
    pattern_regional
    echo ""
    sleep 2
    pattern_user_session
}

# Check if service is running
check_service() {
    if [ "$SKIP_HEALTH_CHECK" = true ]; then
        return
    fi
    
    if ! curl -s "$BASE_URL/actuator/health" > /dev/null 2>&1; then
        echo -e "${RED}Error: Service not running at $BASE_URL${NC}"
        echo "Please start the application first with: ./gradlew bootRun"
        exit 1
    fi
}

# Main
main() {
    check_service
    
    if [ $# -eq 0 ]; then
        while true; do
            show_menu
            read -r choice
            case $choice in
                1) pattern_steady ;;
                2) pattern_burst ;;
                3) pattern_regional ;;
                4) pattern_user_session ;;
                5) pattern_continuous ;;
                6) run_all_patterns ;;
                7) quick_test ;;
                q|Q) echo "Goodbye!"; exit 0 ;;
                *) echo -e "${RED}Invalid option${NC}" ;;
            esac
            echo ""
            echo -e "${BLUE}Press Enter to continue...${NC}"
            read -r
            clear
        done
    else
        case $1 in
            steady) pattern_steady ;;
            burst) pattern_burst ;;
            regional) pattern_regional ;;
            session) pattern_user_session ;;
            continuous) pattern_continuous ;;
            all) run_all_patterns ;;
            test) quick_test ;;
            *) 
                echo "Usage: $0 [steady|burst|regional|session|continuous|all|test]"
                echo "Run without arguments for interactive menu"
                exit 1
                ;;
        esac
    fi
}

print_usage() {
    cat <<EOF
Usage: $0 [OPTIONS] [pattern]

Patterns: steady | burst | regional | session | continuous | all | test

Options:
  --base-url URL          Override the target base URL (default: $BASE_URL)
  --skip-health-check     Do not probe /actuator/health before sending traffic
  --pattern NAME          Shortcut for specifying the pattern (same values as above)
  --colors [true|false]   Enable/disable color output (default: true)
  -h, --help              Show this help text

You can also pass the pattern as the first positional argument (legacy mode).
EOF
}

parse_args() {
    POSITIONAL=()
    
    while [ $# -gt 0 ]; do
        case "$1" in
            --base-url)
                if [ -z "$2" ]; then
                    echo "Error: --base-url requires a value" >&2
                    exit 1
                fi
                BASE_URL="$2"
                shift 2
                ;;
            --skip-health-check)
                SKIP_HEALTH_CHECK=true
                shift
                ;;
            --pattern)
                if [ -z "$2" ]; then
                    echo "Error: --pattern requires a value" >&2
                    exit 1
                fi
                PATTERN_OVERRIDE="$2"
                shift 2
                ;;
            --colors)
                if [ -z "$2" ]; then
                    echo "Error: --colors requires true or false" >&2
                    exit 1
                fi
                COLORS="$2"
                shift 2
                ;;
            -h|--help)
                print_usage
                exit 0
                ;;
            --)
                shift
                POSITIONAL+=("$@")
                break
                ;;
            -*)
                echo "Error: Unknown option $1" >&2
                print_usage >&2
                exit 1
                ;;
            *)
                POSITIONAL+=("$1")
                shift
                ;;
        esac
    done
    
    if [ -n "$PATTERN_OVERRIDE" ]; then
        POSITIONAL=("$PATTERN_OVERRIDE" "${POSITIONAL[@]}")
    fi
    
    set -- "${POSITIONAL[@]}"
    
    if [ "$COLORS" != true ]; then
        GREEN=''
        YELLOW=''
        RED=''
        BLUE=''
        NC=''
    fi
    
    main "$@"
}

parse_args "$@"
