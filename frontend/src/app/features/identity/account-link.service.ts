import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { AccountLink, parseAccountLink } from './account-link';

@Injectable({ providedIn: 'root' })
export class AccountLinkService {
  readonly action = signal<AccountLink | null>(parseAccountLink(window.location.hash));
  readonly notice = signal('');

  constructor() {
    if (this.action()) {
      window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search);
    }
    const refreshLink = () => {
      if (parseAccountLink(window.location.hash)) window.location.reload();
    };
    window.addEventListener('hashchange', refreshLink);
    inject(DestroyRef).onDestroy(() => window.removeEventListener('hashchange', refreshLink));
  }
}
