#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root_dir=$(cd "$script_dir/../.." && pwd)
csv_path=${1:-"$root_dir/docs/operations/requirements-traceability.csv"}
prd_path="$root_dir/docs/AI_Empowered_Financial_Management_PRD.md"
plan_path="$root_dir/todo.md"
failures=0

error() {
  printf 'ERROR: %s\n' "$1" >&2
  failures=$((failures + 1))
}

if [ ! -f "$csv_path" ]; then
  printf 'ERROR: traceability CSV not found: %s\n' "$csv_path" >&2
  exit 1
fi
if [ ! -f "$prd_path" ]; then
  printf 'ERROR: PRD not found: %s\n' "$prd_path" >&2
  exit 1
fi
if [ ! -f "$plan_path" ]; then
  printf 'ERROR: implementation plan not found: %s\n' "$plan_path" >&2
  exit 1
fi

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

normalized="$tmp_dir/requirements.csv"
awk '{ sub(/\r$/, ""); print }' "$csv_path" > "$normalized"
expected="$tmp_dir/expected-ids"
: > "$expected"
for ((i = 1; i <= 15; i++)); do printf 'US-%03d\n' "$i" >> "$expected"; done
for ((i = 1; i <= 44; i++)); do printf 'F-%03d\n' "$i" >> "$expected"; done
awk '{
  while (match($0, /AC-F-[0-9]{3}-[0-9]{2}/)) {
    print substr($0, RSTART, RLENGTH)
    $0 = substr($0, RSTART + RLENGTH)
  }
}' "$prd_path" | sort -u > "$tmp_dir/ac-ids"
ac_count=$(wc -l < "$tmp_dir/ac-ids")
if [ "$ac_count" -ne 101 ]; then
  error "PRD contains $ac_count unique acceptance-criterion IDs; expected 101"
fi
cat "$tmp_dir/ac-ids" >> "$expected"
for ((i = 1; i <= 40; i++)); do printf 'NFR-%03d\n' "$i" >> "$expected"; done
for ((i = 1; i <= 12; i++)); do printf 'NFR-HITL-%03d\n' "$i" >> "$expected"; done
sort -u "$expected" -o "$expected"
expected_count=$(wc -l < "$expected")
if [ "$expected_count" -ne 212 ]; then
  error "authoritative requirement inventory contains $expected_count IDs; expected 212"
fi

tail -n +2 "$normalized" | cut -d, -f1 | sort > "$tmp_dir/actual-all"
sort -u "$tmp_dir/actual-all" > "$tmp_dir/actual"
actual_count=$(wc -l < "$tmp_dir/actual")
if [ "$actual_count" -ne 212 ]; then
  error "traceability register contains $actual_count unique requirement IDs; expected 212"
fi
comm -23 "$expected" "$tmp_dir/actual" > "$tmp_dir/missing"
comm -13 "$expected" "$tmp_dir/actual" > "$tmp_dir/extra"
while IFS= read -r id; do
  [ -z "$id" ] || error "missing requirement row: $id"
done < "$tmp_dir/missing"
while IFS= read -r id; do
  [ -z "$id" ] || error "unknown requirement row: $id"
done < "$tmp_dir/extra"
uniq -d "$tmp_dir/actual-all" > "$tmp_dir/duplicates"
while IFS= read -r id; do
  [ -z "$id" ] || error "duplicate requirement row: $id"
done < "$tmp_dir/duplicates"

awk 'match($0, /^### Task [0-9]+:/) {
  id = substr($0, RSTART + 9, RLENGTH - 10)
  print id + 0
}' "$plan_path" > "$tmp_dir/tasks"

header='requirement_id,requirement_type,scope,releases,task_ids,story_ids,evidence_paths'
IFS= read -r actual_header < "$normalized" || actual_header=''
if [ "$actual_header" != "$header" ]; then
  error "invalid CSV header; expected: $header"
fi

if ! awk -F',' -v tasks_file="$tmp_dir/tasks" '
  BEGIN {
    while ((getline task < tasks_file) > 0) valid_tasks[task] = 1
    close(tasks_file)
  }
  function invalid_releases(value,    parts, count, i, seen) {
    if (value == "") return 1
    count = split(value, parts, ";")
    for (i = 1; i <= count; i++) {
      if (parts[i] !~ /^R[0-4]$/ || (parts[i] in seen)) return 1
      seen[parts[i]] = 1
    }
    return 0
  }
  function row_error(message) {
    printf "ERROR: row %d (%s): %s\n", NR, ($1 == "" ? "<blank>" : $1), message > "/dev/stderr"
    bad = 1
  }
  NR == 1 { next }
  {
    if ($0 == "") { row_error("blank records are not allowed"); next }
    if (index($0, "\"") != 0) { row_error("quoted or multiline CSV fields are not supported"); next }
    if (NF != 7) { row_error("expected 7 unquoted fields; multi-value fields use semicolons"); next }

    id = $1
    type = $2
    scope = $3
    releases = $4
    tasks = $5
    stories = $6
    evidence = $7
    ids[NR] = id
    types[id] = type
    story_values[id] = stories

    if (id ~ /^US-[0-9][0-9][0-9]$/) expected_type = "story"
    else if (id ~ /^AC-F-[0-9][0-9][0-9]-[0-9][0-9]$/) expected_type = "acceptance_criterion"
    else if (id ~ /^NFR-HITL-[0-9][0-9][0-9]$/) expected_type = "hitl"
    else if (id ~ /^NFR-[0-9][0-9][0-9]$/) expected_type = "nfr"
    else if (id ~ /^F-[0-9][0-9][0-9]$/) expected_type = "feature"
    else expected_type = ""
    if (expected_type == "") row_error("invalid requirement ID format")
    else if (type != expected_type) row_error("requirement_type does not match requirement ID")

    if (scope !~ /^(in_scope|excluded)$/) row_error("scope must be in_scope or excluded")
    if (id == "F-043" || id == "F-044") {
      if (scope != "excluded" || releases != "EXCLUDED") row_error("exclusion must use excluded scope and EXCLUDED release")
    } else {
      if (scope != "in_scope") row_error("only F-043 and F-044 may be excluded")
      if (releases == "") row_error("in-scope requirement has no release")
      else if (invalid_releases(releases)) row_error("releases must be unique semicolon-separated R0-R4 values")
    }

    if (tasks == "") row_error("at least one owning task is required")
    else {
      task_count = split(tasks, task_parts, ";")
      delete seen_tasks
      for (i = 1; i <= task_count; i++) {
        if (task_parts[i] !~ /^[0-9]+$/) row_error("invalid owning task: " task_parts[i])
        else {
          if (task_parts[i] in seen_tasks) row_error("duplicate owning task: " task_parts[i])
          seen_tasks[task_parts[i]] = 1
          used_tasks[task_parts[i]] = id
        }
      }
    }

    if (evidence == "") row_error("absent evidence path")
    else {
      evidence_count = split(evidence, evidence_parts, ";")
      for (i = 1; i <= evidence_count; i++) {
        path = evidence_parts[i]
        trimmed_path = path
        gsub(/^[[:space:]]+/, "", trimmed_path)
        gsub(/[[:space:]]+$/, "", trimmed_path)
        if (path == "") row_error("empty evidence path list item")
        else if (trimmed_path == "") row_error("absent evidence path")
        else if (trimmed_path != path) row_error("evidence path has leading or trailing whitespace: " path)
        else if (path == "-") row_error("evidence path must name a concrete file or directory")
        else if (path ~ /^\// || path ~ /^[A-Za-z]:/ || path ~ /\\/ || path ~ /(^|\/)\.\.($|\/)/) {
          row_error("evidence path must be normalized and repository-relative: " path)
        }
      }
    }

    if (type == "story") {
      story_rows[id] = 1
      if (stories != "-") row_error("story rows must use - for story_ids")
    } else if (type == "feature") {
      feature_stories[id] = stories
      if (scope == "in_scope" && (stories == "" || stories == "-")) row_error("in-scope feature has no parent story")
      if (scope == "excluded" && stories != "-") row_error("excluded feature must use - for story_ids")
    } else if (type == "nfr" || type == "hitl") {
      if (stories != "-") row_error("NFR and HITL rows must use - for story_ids")
    } else if (type == "acceptance_criterion" && (stories == "" || stories == "-")) {
      row_error("acceptance criterion has no inherited story mapping")
    }

    if (stories != "-" && stories != "") {
      story_count = split(stories, story_parts, ";")
      delete seen_stories
      for (i = 1; i <= story_count; i++) {
        story = story_parts[i]
        if (story !~ /^US-[0-9][0-9][0-9]$/) row_error("invalid story reference: " story)
        else {
          if (story in seen_stories) row_error("duplicate story reference: " story)
          seen_stories[story] = 1
          referenced_stories[id, story] = 1
          if (type == "feature") feature_references[story] = 1
        }
      }
    }
  }
  END {
    for (task in used_tasks) {
      if (!(task in valid_tasks)) {
        printf "ERROR: row for %s references nonexistent Task %s\n", used_tasks[task], task > "/dev/stderr"
        bad = 1
      }
    }
    for (row in ids) {
      id = ids[row]
      if (types[id] == "acceptance_criterion") {
        parent = id
        sub(/^AC-/, "", parent)
        sub(/-[0-9][0-9]$/, "", parent)
        if (!(parent in feature_stories)) {
          printf "ERROR: %s has no parent feature row %s\n", id, parent > "/dev/stderr"
          bad = 1
        } else if (story_values[id] != feature_stories[parent]) {
          printf "ERROR: %s story_ids must match parent %s\n", id, parent > "/dev/stderr"
          bad = 1
        }
      }
    }
    for (key in referenced_stories) {
      split(key, pair, SUBSEP)
      if (!(pair[2] in story_rows)) {
        printf "ERROR: %s references unknown story %s\n", pair[1], pair[2] > "/dev/stderr"
        bad = 1
      }
    }
    for (story in story_rows) {
      if (!(story in feature_references)) {
        printf "ERROR: orphan story: %s\n", story > "/dev/stderr"
        bad = 1
      }
    }
    exit bad ? 1 : 0
  }
' "$normalized"; then
  failures=$((failures + 1))
fi

if [ "$failures" -ne 0 ]; then
  printf 'FAIL: traceability validation failed\n' >&2
  exit 1
fi

printf 'PASS: 212 requirements traced with zero unmapped IDs\n'
