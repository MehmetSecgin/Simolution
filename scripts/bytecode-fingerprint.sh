#!/usr/bin/env bash
set -u
REPO="$(cd "$(dirname "$0")/.." && pwd)"
[ $# -eq 1 ] || { echo "usage: $0 <out-file>" >&2; exit 2; }
mkdir -p "$(dirname "$1")"
OUT="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
(cd "$REPO" && ./gradlew -q clean compileJava compileTestJava -PnoDebug) || exit 1
cd "$REPO/build/classes/java" || exit 1
find . -name '*.class' | sed 's#^\./##' | LC_ALL=C sort | while IFS= read -r f; do
    echo "$(shasum -a 256 "$f" | cut -d' ' -f1)  $f"
done >"$OUT"
