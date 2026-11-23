import { describe, expect, it } from 'vitest';
import { additionalBranches, branchStatusLabel, branchStatusTone, canAddBranch, fillDailySeries } from './business';

describe('fillDailySeries', () => {
  it('has one point per day, including the first and last, with 0 on days without sales', () => {
    const series = fillDailySeries(
      [{ date: '2026-03-10', amount: '150.00' }, { date: '2026-03-12', amount: 80 }],
      '2026-03-10', '2026-03-13',
    );

    expect(series.map((p) => p.key)).toEqual(['2026-03-10', '2026-03-11', '2026-03-12', '2026-03-13']);
    expect(series.map((p) => p.value)).toEqual([150, 0, 80, 0]);
    expect(series[0]).toMatchObject({ label: '10', detail: '10 mar 2026' });
  });

  it('crosses month and year boundaries', () => {
    const series = fillDailySeries([], '2025-12-30', '2026-01-02');
    expect(series.map((p) => p.key)).toEqual(['2025-12-30', '2025-12-31', '2026-01-01', '2026-01-02']);
  });

  it('is a single point for one day and empty without a period', () => {
    expect(fillDailySeries(null, '2026-03-10', '2026-03-10')).toHaveLength(1);
    expect(fillDailySeries([], '', '2026-03-10')).toEqual([]);
    expect(fillDailySeries([], '2026-03-12', '2026-03-10')).toEqual([]);
  });
});

describe('branch helpers', () => {
  it('labels and tones the store statuses, and passes unknown ones through', () => {
    expect(branchStatusLabel('PENDING_VERIFICATION')).toBe('En revisión');
    expect(branchStatusTone('ACTIVE')).toBe('success');
    expect(branchStatusTone('REJECTED')).toBe('danger');
    expect(branchStatusLabel('NEW_ONE')).toBe('NEW_ONE');
    expect(branchStatusTone('NEW_ONE')).toBe('neutral');
  });

  it('does not count the main store against the branch limit', () => {
    expect(canAddBranch({ branches: [{}], maxBranches: 1 })).toBe(true); // main store only, one branch allowed
    expect(canAddBranch({ branches: [{}, {}], maxBranches: 1 })).toBe(false);
    expect(canAddBranch({ branches: [{}, {}], maxBranches: 2 })).toBe(true);
    expect(canAddBranch(null)).toBe(false);
    expect(additionalBranches({ branches: [{}, {}, {}] })).toBe(2);
    expect(additionalBranches({ branches: [{}] })).toBe(0);
  });
});
