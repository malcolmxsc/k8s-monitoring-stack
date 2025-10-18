#!/usr/bin/env bash
set -euo pipefail

# find-in-history.sh
# Search for a literal string across all commits for a file path, safely.
# Usage: scripts/find-in-history.sh "search-string" path/to/file

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 \"search-string\" path/to/file" >&2
  exit 2
fi

SEARCH="$1"
FILEPATH="$2"

echo "Searching for literal '$SEARCH' in $FILEPATH across all commits..."

FOUND=0
while IFS= read -r rev; do
  if git show "$rev:$FILEPATH" 2>/dev/null | grep -Fq -- "$SEARCH"; then
    echo "FOUND '$SEARCH' in commit $rev"
    FOUND=1
  fi
done < <(git rev-list --all)

if [ "$FOUND" -eq 0 ]; then
  echo "No occurrences of '$SEARCH' found in any commit for $FILEPATH"
fi
