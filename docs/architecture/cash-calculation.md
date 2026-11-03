# Cash Calculation Design

## Account position (rule `cash-1.0.0`)

```
closing = opening + inflows − outflows          (scale 2, HALF_UP, decimal strings)
```

- Opening comes from the prior certified close (continuity evidence) or the governed
  opening balance for a new account.
- Movements are signed in the fixtures; the calculator consumes them as inflow and
  outflow magnitudes so a negative overdraft balance stays explicit rather than being
  reinterpreted.
- **Negative authorized overdrafts are valid**: a negative opening or closing on an
  overdraft account is a position, never an error (golden ACC004: −75.50 − 4.50 →
  −80.00).

## Group rollup (FX basis)

```
group_closing = Σ_accounts closing × fx(accountCurrency → groupCurrency, rate version)
```

- Rate applied per account, scale 4, product rounded to scale 2 HALF_UP per account,
  then summed — no float anywhere.
- Golden (FX-V1, EUR→USD 1.1000): USD accounts 1150.25 + 375.75 − 80.00 = 1446.00;
  EUR account 450.00 × 1.1000 = 495.00; **group 1941.00**.

## Continuity

`prior certified close == today's opening`, exact (difference 0.00). A break surfaces
the entity, amount, prior revision, and difference; certification of the affected
position is blocked until the break is explained (case) or corrected (new revision).

## Zero and null

Zero stays numeric (`0.00`); a null amount is a validation quarantine (Task 19), never
a silent zero.

## Certification linkage

A calculated position becomes certified only through the Task 20 gate (passed
validation, matched reconciliation, independent certifier); publication of a certified
position is a separate human action (Task 28).
