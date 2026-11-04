import type { ReactNode } from 'react';

/**
 * Task 42 planning model: non-driver schedule input, validation, recalculation,
 * statement, balance-check, and drill views. All formulas live server-side
 * (rule version icstm-1.0.0); the browser never computes statements.
 */
export interface StatementRow {
  readonly statement: 'PL' | 'BS' | 'CF';
  readonly lineKey: string;
  readonly amount: string;
  readonly lineage: string;
}

export interface BalanceCheckRow {
  readonly entity: string;
  readonly period: string;
  readonly assets: string;
  readonly equityAndLiabilities: string;
  readonly difference: string;
  readonly status: 'BALANCED' | 'IMBALANCE';
}

export const STATEMENT_ROWS: readonly StatementRow[] = [
  { statement: 'PL', lineKey: 'revenue_total', amount: '1500.00', lineage: 'REVENUE/*' },
  { statement: 'PL', lineKey: 'opex_total', amount: '500.00', lineage: 'OPERATING_COST/*' },
  { statement: 'PL', lineKey: 'net_income', amount: '750.00', lineage: 'derived' },
  { statement: 'CF', lineKey: 'cash_flow_total', amount: '500.00', lineage: 'PL + CAPITAL/*' },
  { statement: 'BS', lineKey: 'closing_cash', amount: '2500.00', lineage: 'OPENING_BALANCE + CF' },
  { statement: 'BS', lineKey: 'assets_total', amount: '2750.00', lineage: 'closing_cash + CAPITAL/*' },
  { statement: 'BS', lineKey: 'equity_total', amount: '2750.00', lineage: 'OPENING_BALANCE + PL' },
];

export const BALANCE_CHECKS: readonly BalanceCheckRow[] = [
  {
    entity: 'ENT-101',
    period: '2027-P01',
    assets: '2750.00',
    equityAndLiabilities: '2750.00',
    difference: '0.00',
    status: 'BALANCED',
  },
];

export function PlanningModelPanel({
  statementRows,
  balanceChecks,
}: {
  statementRows: readonly StatementRow[];
  balanceChecks: readonly BalanceCheckRow[];
}): ReactNode {
  return (
    <section aria-labelledby="planning-model-heading">
      <h3 id="planning-model-heading">Integrated planning model</h3>
      <p>
        Schedules feed the server-side integrated calculation (rule version
        icstm-1.0.0). Zero participates as a value; a null required input blocks the
        calculation. Imbalance blocks submission and opens a case — case closure never
        approves or publishes.
      </p>
      <table className="pack-table">
        <caption>Integrated statements with drill lineage</caption>
        <thead>
          <tr>
            <th scope="col">Statement</th>
            <th scope="col">Line</th>
            <th scope="col">Amount</th>
            <th scope="col">Drill lineage</th>
          </tr>
        </thead>
        <tbody>
          {statementRows.map((row) => (
            <tr key={`${row.statement}-${row.lineKey}`}>
              <td>{row.statement}</td>
              <th scope="row">{row.lineKey}</th>
              <td>{row.amount}</td>
              <td>{row.lineage}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <table className="pack-table">
        <caption>Balance checks per entity and period</caption>
        <thead>
          <tr>
            <th scope="col">Entity</th>
            <th scope="col">Period</th>
            <th scope="col">Assets</th>
            <th scope="col">Liabilities + equity</th>
            <th scope="col">Difference</th>
            <th scope="col">Status</th>
          </tr>
        </thead>
        <tbody>
          {balanceChecks.map((check) => (
            <tr key={`${check.entity}-${check.period}`} data-testid={`balance-${check.entity}`}>
              <th scope="row">{check.entity}</th>
              <td>{check.period}</td>
              <td>{check.assets}</td>
              <td>{check.equityAndLiabilities}</td>
              <td>{check.difference}</td>
              <td>
                <span
                  className={
                    check.status === 'BALANCED' ? 'badge badge-ok' : 'badge badge-blocked'
                  }
                >
                  {check.status === 'BALANCED'
                    ? 'Balanced'
                    : 'Blocked: imbalance case opened'}
                </span>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
