import { describe, expect, it } from 'vitest';
import { latestDocumentOfType } from './storeDocuments';

describe('latestDocumentOfType', () => {
  const docs = [
    { id: 1, documentType: 'RUT', uploadedAt: '2026-01-01T10:00:00' },
    { id: 2, documentType: 'RUT', uploadedAt: '2026-03-01T10:00:00' },
    { id: 3, documentType: 'ID_CARD', uploadedAt: '2026-02-01T10:00:00' },
  ];

  it('returns the most recently uploaded document of the type', () => {
    expect(latestDocumentOfType(docs, 'RUT').id).toBe(2);
    expect(latestDocumentOfType(docs, 'ID_CARD').id).toBe(3);
  });

  it('returns null when the type was never uploaded or there is no list', () => {
    expect(latestDocumentOfType(docs, 'OTHER')).toBeNull();
    expect(latestDocumentOfType(null, 'RUT')).toBeNull();
  });
});
