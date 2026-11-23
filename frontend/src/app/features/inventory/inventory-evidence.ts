export interface AssessmentIdentity {
  ecosystem: string;
  namespace: string | null;
  name: string;
  version: string;
}

export function evidenceKey(scanId: string, item: AssessmentIdentity): string {
  return JSON.stringify([scanId, item.ecosystem.toUpperCase(), item.namespace || '', item.name, item.version]);
}

export function fixesForPackage(affectedJson: string, ecosystem: string, name: string): string[] {
  try {
    const affected: unknown = JSON.parse(affectedJson);
    if (!Array.isArray(affected)) return [];
    const fixes = new Set<string>();
    for (const entry of affected) {
      if (!entry || entry.package?.name !== name
          || entry.package?.ecosystem?.toUpperCase() !== ecosystem.toUpperCase()) continue;
      if (!Array.isArray(entry.ranges)) continue;
      for (const range of entry.ranges) {
        if (!Array.isArray(range?.events)) continue;
        for (const event of range.events) {
          if (typeof event?.fixed === 'string' && event.fixed.trim()) fixes.add(event.fixed);
        }
      }
    }
    return [...fixes].sort((left, right) => left.localeCompare(right, undefined, { numeric: true }));
  } catch {
    return [];
  }
}
