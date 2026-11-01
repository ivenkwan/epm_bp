#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root_dir=$(cd "$script_dir/../.." && pwd)
allow_uncommitted=false
if [ "${1:-}" = "--allow-uncommitted" ]; then
  allow_uncommitted=true
elif [ "$#" -ne 0 ]; then
  printf 'ERROR: usage: verify-provenance.sh [--allow-uncommitted]\n' >&2
  exit 2
fi

matrix_path=${PROVENANCE_VERSION_MATRIX:-"$root_dir/docs/decisions/version-matrix.md"}

for command_name in git node pnpm syft; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    printf 'ERROR: required command is unavailable: %s\n' "$command_name" >&2
    exit 1
  fi
done

require_file() {
  if [ ! -f "$1" ]; then
    printf 'ERROR: required provenance input is missing: %s\n' "$1" >&2
    exit 1
  fi
}

require_literal() {
  local file=$1
  local needle=$2
  local label=$3
  if ! awk -v needle="$needle" 'index($0, needle) { found = 1; exit } END { exit found ? 0 : 1 }' "$file"; then
    printf 'ERROR: missing required locked version or configuration: %s (%s)\n' "$label" "$needle" >&2
    exit 1
  fi
}

require_image_digest() {
  local image=$1
  if ! awk -v image="$image" '
    index($0, image) {
      found = 1
      if ($0 !~ /@sha256:[0-9a-f]{64}/) {
        printf "ERROR: image %s lacks a pinned SHA-256 digest\n", image > "/dev/stderr"
        bad = 1
      }
    }
    END {
      if (!found) {
        printf "ERROR: required image is missing from version matrix: %s\n", image > "/dev/stderr"
        bad = 1
      }
      exit bad ? 1 : 0
    }
  ' "$matrix_path"; then
    exit 1
  fi
}

required_inputs=(
  "$root_dir/pom.xml"
  "$root_dir/package.json"
  "$root_dir/pnpm-lock.yaml"
  "$root_dir/pnpm-workspace.yaml"
  "$root_dir/apps/finance-web/package.json"
  "$root_dir/tests/e2e/package.json"
  "$root_dir/tests/integration/toolchain/ToolchainCompatibilityTest.java"
  "$root_dir/.github/workflows/ci.yml"
  "$root_dir/.gitleaksignore"
  "$root_dir/.gitleaks.toml"
  "$root_dir/.mvn/wrapper/maven-wrapper.properties"
  "$matrix_path"
)
for input_path in "${required_inputs[@]}"; do
  require_file "$input_path"
done

require_literal "$matrix_path" '| Java | Eclipse Temurin 21.0.13+9' 'Java 21.0.13+9'
require_literal "$matrix_path" '| Maven | 3.9.11 |' 'Maven 3.9.11'
require_literal "$matrix_path" '| Quarkus | 3.27.4.1 |' 'Quarkus 3.27.4.1'
require_literal "$matrix_path" '| Kogito | 10.2.0 |' 'Kogito 10.2.0'
require_literal "$matrix_path" '| Drools | 10.2.0 |' 'Drools 10.2.0'
require_literal "$matrix_path" '| DMN | 10.2.0 |' 'DMN 10.2.0'
require_literal "$matrix_path" '| Jackson BOM | 2.21.7 |' 'Jackson 2.21.7'
require_literal "$matrix_path" '| Netty BOM | 4.1.137.Final |' 'Netty 4.1.137.Final'
require_literal "$matrix_path" '| PostgreSQL JDBC | 42.7.12 |' 'PostgreSQL JDBC 42.7.12'
require_literal "$matrix_path" '| Guava | 33.7.2-jre |' 'Guava 33.7.2-jre'
require_literal "$matrix_path" '| Error Prone annotations / JSpecify | 2.50.0 / 1.0.1 |' 'Error Prone annotations 2.50.0 and JSpecify 1.0.1'
require_literal "$matrix_path" '| PostgreSQL | 16.10 ' 'PostgreSQL 16.10'
require_literal "$matrix_path" '| Apache AGE | 1.6.0 |' 'Apache AGE 1.6.0'
require_literal "$matrix_path" '| APISIX | 3.13.0 |' 'APISIX 3.13.0'
require_literal "$matrix_path" '| Testcontainers | 1.21.3 |' 'Testcontainers 1.21.3'
require_literal "$matrix_path" '| Node.js | 24.13.0 ' 'Node.js 24.13.0'
require_literal "$matrix_path" '| pnpm | 12.9.1 |' 'pnpm 12.9.1'
require_literal "$matrix_path" '| React / React DOM | 19.3.1 |' 'React 19.3.1'
require_literal "$matrix_path" '| Vite / plugin-react | 7.3.7 ' 'Vite 7.3.7'
require_literal "$matrix_path" '| TypeScript | 6.0.3 |' 'TypeScript 6.0.3'
require_literal "$matrix_path" '| Trivy | 0.70.0 |' 'Trivy 0.70.0'
require_literal "$matrix_path" '| Gitleaks | 8.30.1 |' 'Gitleaks 8.30.1'
require_literal "$matrix_path" '| Syft | 1.55.0 |' 'Syft 1.55.0'
require_literal "$root_dir/pom.xml" '<quarkus.version>3.27.4.1</quarkus.version>' 'Quarkus 3.27.4.1'
require_literal "$root_dir/pom.xml" '<kogito.version>10.2.0</kogito.version>' 'Kogito and Drools 10.2.0'
require_literal "$root_dir/pom.xml" '<testcontainers.version>1.21.3</testcontainers.version>' 'Testcontainers 1.21.3'
require_literal "$root_dir/pom.xml" '<version>2.21.7</version>' 'Jackson 2.21.7 in the parent POM'
require_literal "$root_dir/pom.xml" '<version>4.1.137.Final</version>' 'Netty 4.1.137.Final in the parent POM'
require_literal "$root_dir/pom.xml" '<version>42.7.12</version>' 'PostgreSQL JDBC 42.7.12 in the parent POM'
require_literal "$root_dir/pom.xml" '<version>33.7.2-jre</version>' 'Guava 33.7.2-jre in the parent POM'
require_literal "$root_dir/pom.xml" '<version>2.50.0</version>' 'Error Prone annotations 2.50.0 in the parent POM'
require_literal "$root_dir/pom.xml" '<version>1.0.1</version>' 'JSpecify 1.0.1 in the parent POM'
require_literal "$root_dir/package.json" '"packageManager": "pnpm@12.9.1"' 'pnpm 12.9.1'
require_literal "$root_dir/package.json" '"node": "24.13.0"' 'Node.js 24.13.0'
require_literal "$root_dir/apps/finance-web/package.json" '"react": "19.3.1"' 'React 19.3.1'
require_literal "$root_dir/apps/finance-web/package.json" '"vite": "7.3.7"' 'Vite 7.3.7'
require_literal "$root_dir/apps/finance-web/package.json" '"typescript": "6.0.3"' 'TypeScript 6.0.3'
require_literal "$root_dir/tests/e2e/package.json" '"@playwright/test": "1.64.1"' 'Playwright 1.64.1'
require_literal "$root_dir/.github/workflows/ci.yml" 'java-version: "21.0.13+9"' 'CI Java 21.0.13+9'
require_literal "$root_dir/.github/workflows/ci.yml" 'attestations: write' 'CI attestation write permission'
require_literal "$root_dir/.github/workflows/ci.yml" 'node-version: 24.13.0' 'CI Node.js 24.13.0'
require_literal "$root_dir/.mvn/wrapper/maven-wrapper.properties" 'distributionSha256Sum=0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb' 'Maven distribution SHA-256'
require_image_digest 'apache/age:release_PG16_1.6.0'
require_image_digest 'apache/apisix:3.13.0-debian'
require_image_digest 'maven:3.9.11-eclipse-temurin-21'

if [ "$allow_uncommitted" = false ] && [ -n "$(git -C "$root_dir" status --porcelain --untracked-files=all)" ]; then
  printf 'ERROR: clean generated outputs and Git state are required; --allow-uncommitted is for development only\n' >&2
  git -C "$root_dir" status --short --untracked-files=all >&2
  exit 1
fi

pnpm --dir "$root_dir" install --frozen-lockfile
pnpm --dir "$root_dir" run sbom
node "$script_dir/verify-provenance.cjs" "$root_dir" "$(git -C "$root_dir" rev-parse HEAD)"
