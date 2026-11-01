#!/usr/bin/env bash
set -euo pipefail
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root_dir=$(cd "$script_dir/../.." && pwd)
cd "$root_dir"
./mvnw -B -ntp -pl tests/integration -Dtest=ToolchainCompatibilityTest#droolsRuleTerminates+dmnDecisionEvaluates test
printf 'PASS: current DRL termination and DMN evaluation checks passed; governed rule coverage expands in Task 13\n'
