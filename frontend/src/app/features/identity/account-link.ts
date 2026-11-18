export interface AccountLink { kind: 'verify-email' | 'reset-password' | 'admission-decision'; token: string; }

export function parseAccountLink(fragment: string): AccountLink | null {
  const params = new URLSearchParams(fragment.replace(/^#/, ''));
  const verification = params.get('verify-email');
  const reset = params.get('reset-password');
  const admission = params.get('admission-decision');
  const present = [verification, reset, admission].filter(value => value !== null);
  if (present.length !== 1) return null;
  if (admission !== null) return { kind: 'admission-decision', token: admission };
  return verification !== null ? { kind: 'verify-email', token: verification } : { kind: 'reset-password', token: reset! };
}
