export interface InvitationLink { organization: string; invitation: string; token: string; }

export function parseInvitationLink(fragment: string): InvitationLink | null {
  const params = new URLSearchParams(fragment.replace(/^#/, ''));
  if (params.size !== 3) return null;
  const organization = params.get('organization') ?? '';
  const invitation = params.get('invitation') ?? '';
  const token = params.get('invite-token') ?? '';
  const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i;
  return uuid.test(organization) && uuid.test(invitation) && /^[A-Za-z0-9_-]{43}$/.test(token)
    ? { organization, invitation, token } : null;
}
