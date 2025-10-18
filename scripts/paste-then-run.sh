#!/usr/bin/env bash
set -euo pipefail

# paste-then-run.sh
# macOS helper: save clipboard contents to a file so you can review before executing.
# Usage: scripts/paste-then-run.sh [output-file] [--run]

OUTFILE=${1:-/tmp/clipboard.sh}
RUN=false
if [ "${2:-}" = "--run" ] || [ "${3:-}" = "--run" ]; then
  RUN=true
fi

if command -v pbpaste >/dev/null 2>&1; then
  pbpaste > "$OUTFILE"
  echo "Saved clipboard to $OUTFILE"
else
  echo "pbpaste not found. If you're on Linux, pipe your clipboard into this script (e.g. xclip/xsel)." >&2
  exit 2
fi

echo "Review the file before running:"
echo "  less $OUTFILE"
echo "To run it now: bash $OUTFILE"
if [ "$RUN" = true ]; then
  echo "Running $OUTFILE"
  bash "$OUTFILE"
fi
