#!/usr/bin/env bash
set -euo pipefail
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root_dir=$(cd "$script_dir/../.." && pwd)
cd "$root_dir"
./mvnw -B -ntp -DskipTests enforcer:enforce@enforce-toolchain
./mvnw -B -ntp -DskipTests com.spotify.fmt:fmt-maven-plugin:check
git diff --check
printf 'PASS: current Maven build contracts and formatting checks passed; public API contracts are introduced by Task 6\n'
