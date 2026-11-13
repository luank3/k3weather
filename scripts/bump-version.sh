#!/usr/bin/env bash
# Usage: scripts/bump-version.sh <major|minor|patch>
set -euo pipefail
cd "$(dirname "$0")/.."
cur=$(grep -E '^VERSION_NAME=' gradle.properties | cut -d= -f2)
IFS=. read -r ma mi pa <<<"$cur"
case "${1:-}" in
  major) ma=$((ma+1)); mi=0; pa=0 ;;
  minor) mi=$((mi+1)); pa=0 ;;
  patch) pa=$((pa+1)) ;;
  *) echo "usage: $0 <major|minor|patch>"; exit 1 ;;
esac
new="$ma.$mi.$pa"
sed -i "s/^VERSION_NAME=.*/VERSION_NAME=$new/" gradle.properties
echo "Version: $cur -> $new"
echo "Next: update CHANGELOG.md, then:"
echo "  git commit -am 'chore(release): v$new' && git tag -a v$new -m 'v$new' && git push --follow-tags"
