# Synthetic Finance Fixtures

All records under `db/fixtures/` are **fully synthetic**. They contain no customer data, no
confidential finance information, no credentials, and no customer-derived values. Any resemblance
to real entities, banks, or accounts is coincidental and unintended for training or production use.

## Layout

| Path | Contents |
|---|---|
| `master-data/` | Entities, effective-dated hierarchy versions, bank accounts, FX rate versions |
| `treasury/` | Opening balances, daily cash movements, facilities, deposits, intercompany loan pairs, covenant definitions |
| `planning/` | Planning opening balances and allocation drivers |
| `adversarial/` | Declared edge-case populations consumed by validation/quarantine tests (Tasks 19-23) |
| `expected/` | **Independently hand-calculated** golden results; never produced by implementation code |
| `provenance.manifest` | SHA-256 digest of every fixture file, with authorship metadata |

## Conventions (enforced by `FixtureIntegrityTest`)

1. **Stable IDs** are unique within each dataset and follow the documented prefix scheme
   (`ENT`, `ACC`, `FXR`, `BAL`, `MV`, `FAC`, `DEP`, `ICL`, `COV`, `RCP`, `ALC`, `PLN`).
2. **Money and rates are decimal strings** with explicit scale (2 for money, 4 for FX rates,
   6 for ratios). Scientific notation, leading `+`, and floating-point artifacts are rejected.
3. **Currencies** are ISO 4217; every amount column is paired with a currency column.
4. **Dates** are ISO-8601 (`YYYY-MM-DD`); timestamps are RFC-3339 UTC. `effective_from` is
   inclusive, `effective_to` is exclusive; empty `effective_to` means open-ended. Adjacent
   versions share boundary dates without overlapping.
5. **Source revisions** identify the landed revision a fact came from (`REV` prefix).
6. **Provenance**: every file is digest-listed in `provenance.manifest`; the integrity test
   recomputes digests and fails on drift.

## Expected-result independence

Files in `expected/` were hand-calculated from the fixture inputs; the arithmetic steps are
documented in `expected/README.md`. Implementation calculators (Tasks 19-27) must reproduce
these values and may not read them as inputs. Sign-off status for the Finance/Treasury reviewer
is tracked in `docs/operations/r0-build-status.md`.

## Rule versions

Validation fixtures reference rule/DMN versions `val-1.0.0` (validation rules) and
`cov-1.0.0` (covenant formulas) which are registered with their owning tasks (13/26).
