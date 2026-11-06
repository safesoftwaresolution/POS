// Un tipo de documento KYC puede tener varias versiones subidas en el tiempo; la que cuenta es la más reciente.
export function latestDocumentOfType(documents, type) {
  const matches = (documents || []).filter(d => d.documentType === type);
  if (matches.length === 0) return null;
  return matches.reduce((latest, d) => (new Date(d.uploadedAt) > new Date(latest.uploadedAt) ? d : latest));
}
