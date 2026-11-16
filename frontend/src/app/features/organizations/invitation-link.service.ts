import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { InvitationLink, parseInvitationLink } from './invitation-link';

@Injectable({ providedIn: 'root' })
export class InvitationLinkService {
  readonly invitation = signal<InvitationLink | null>(null);
  constructor() {
    const capture = () => {
      const invitation = parseInvitationLink(window.location.hash);
      if (!invitation) return;
      this.invitation.set(invitation);
      window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search);
    };
    capture();
    window.addEventListener('hashchange', capture);
    inject(DestroyRef).onDestroy(() => window.removeEventListener('hashchange', capture));
  }
}
