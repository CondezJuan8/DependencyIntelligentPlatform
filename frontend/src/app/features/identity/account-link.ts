export interface AccountLink { kind: 'verify-email' | 'reset-password'; token: string; }

export function parseAccountLink(fragment: string): AccountLink | null {
  const params = new URLSearchParams(fragment.replace(/^#/, ''));
  const verification = params.get('verify-email');
  const reset = params.get('reset-password');
  if ((verification === null) === (reset === null)) return null;
  return verification !== null ? { kind: 'verify-email', token: verification } : { kind: 'reset-password', token: reset! };
}
