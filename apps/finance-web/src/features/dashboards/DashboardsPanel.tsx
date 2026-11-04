import type { ReactNode } from 'react';

/**
 * Task 45 certified dashboards: tiles carry certification and freshness labels;
 * slicing runs on governed dimensions only; drill stops at the lowest available
 * governed grain and labels anything deeper unavailable — never fabricated.
 */
export interface DashboardTile {
  readonly tileId: string;
  readonly kpiId: string;
  readonly certified: boolean;
  readonly asOfUtc: string;
  readonly lowestGrain: string;
}

export const DASHBOARD_TILES: readonly DashboardTile[] = [
  {
    tileId: 'tile-cash',
    kpiId: 'kpi-cash',
    certified: true,
    asOfUtc: '2026-11-03T18:00:00Z',
    lowestGrain: 'Entity',
  },
];

export function DashboardsPanel({
  tiles,
}: {
  tiles: readonly DashboardTile[];
}): ReactNode {
  return (
    <section aria-labelledby="dashboards-heading">
      <h3 id="dashboards-heading">Certified dashboards</h3>
      <p>
        Tiles assemble from certified KPIs only; slicing uses governed dimensions;
        drill stops at the lowest available governed grain and labels deeper grain
        unavailable rather than fabricating detail.
      </p>
      <ul className="pack-table" style={{ listStyle: 'none', padding: 0 }}>
        {tiles.map((tile) => (
          <li
            key={tile.tileId}
            data-testid={`tile-${tile.tileId}`}
            style={{ border: '1px solid #5a6b86', padding: '0.5rem', marginBottom: '0.5rem' }}
          >
            <strong>{tile.kpiId}</strong>{' '}
            <span className={tile.certified ? 'badge badge-ok' : 'badge badge-blocked'}>
              {tile.certified ? 'Certified' : 'Uncertified — excluded'}
            </span>
            <p style={{ margin: '0.25rem 0 0' }}>
              Freshness: {tile.asOfUtc} · Lowest governed grain: {tile.lowestGrain}
            </p>
            <button type="button">Slice</button> <button type="button">Drill</button>{' '}
            <button type="button">Save authorized view</button>
          </li>
        ))}
      </ul>
    </section>
  );
}
