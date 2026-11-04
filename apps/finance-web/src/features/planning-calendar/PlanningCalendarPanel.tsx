import type { ReactNode } from 'react';

/**
 * Task 43 planning calendar: progress board with milestones, assigned tasks,
 * review/return/approve actions, delegation, and escalation visibility. Every row
 * shows the level, owner, due time, and status; decision chronology is server-owned.
 */
export interface CalendarTaskRow {
  readonly taskId: string;
  readonly level: number;
  readonly title: string;
  readonly assignee: string;
  readonly delegatedFrom: string | null;
  readonly dueAtUtc: string;
  readonly status: 'OPEN' | 'COMPLETED' | 'CANCELLED' | 'ESCALATED';
}

export const CALENDAR_TASKS: readonly CalendarTaskRow[] = [
  {
    taskId: 'cyc-1:prep',
    level: 0,
    title: 'Prepare schedules and plan version',
    assignee: 'planner@epm',
    delegatedFrom: null,
    dueAtUtc: '2026-11-04T18:00:00Z',
    status: 'COMPLETED',
  },
  {
    taskId: 'cyc-1:lvl1',
    level: 1,
    title: 'Approval level 1',
    assignee: 'division@epm',
    delegatedFrom: null,
    dueAtUtc: '2026-11-06T18:00:00Z',
    status: 'ESCALATED',
  },
  {
    taskId: 'cyc-1:lvl2',
    level: 2,
    title: 'Approval level 2',
    assignee: 'fpa@epm',
    delegatedFrom: 'division@epm',
    dueAtUtc: '2026-11-10T18:00:00Z',
    status: 'OPEN',
  },
];

export function PlanningCalendarPanel({
  tasks,
}: {
  tasks: readonly CalendarTaskRow[];
}): ReactNode {
  return (
    <section aria-labelledby="planning-calendar-heading">
      <h3 id="planning-calendar-heading">Planning calendar &amp; approvals</h3>
      <p>
        Approvals progress level by level with independent eligible actors; reminders
        and escalations change visibility only — no timer approves, locks, publishes,
        or closes. Target cycle: five business days (measured, never assumed).
      </p>
      <table className="pack-table">
        <caption>Cycle tasks with ownership and due times</caption>
        <thead>
          <tr>
            <th scope="col">Level</th>
            <th scope="col">Task</th>
            <th scope="col">Assignee</th>
            <th scope="col">Delegated from</th>
            <th scope="col">Due (UTC)</th>
            <th scope="col">Status</th>
            <th scope="col">Actions</th>
          </tr>
        </thead>
        <tbody>
          {tasks.map((task) => (
            <tr key={task.taskId} data-testid={`calendar-${task.taskId}`}>
              <td>{task.level === 0 ? 'Prep' : `Level ${String(task.level)}`}</td>
              <th scope="row">{task.title}</th>
              <td>{task.assignee}</td>
              <td>{task.delegatedFrom ?? '—'}</td>
              <td>{task.dueAtUtc}</td>
              <td>
                <span
                  className={
                    task.status === 'COMPLETED' ? 'badge badge-ok' : 'badge badge-blocked'
                  }
                >
                  {task.status === 'ESCALATED'
                    ? 'Escalated (overdue — visibility only)'
                    : task.status === 'COMPLETED'
                      ? 'Completed'
                      : task.status === 'CANCELLED'
                        ? 'Cancelled (stale)'
                        : 'Open'}
                </span>
              </td>
              <td>
                {task.status === 'OPEN' || task.status === 'ESCALATED' ? (
                  <>
                    <button type="button">Approve</button>{' '}
                    <button type="button">Return</button>{' '}
                    <button type="button">Delegate</button>
                  </>
                ) : (
                  <span>—</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
