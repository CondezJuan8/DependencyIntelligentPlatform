import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { SessionApiService } from '../core/session-api.service';
import { navigation, Page, readPage } from '../core/navigation';
import { DiIcon } from './di-icon';
import { Dashboard } from '../features/dashboard/dashboard';
import { OrganizationWorkspace } from '../features/organizations/organization-workspace';
import { InvitationAcceptance } from '../features/organizations/invitation-acceptance';
import { EmailVerification } from '../features/identity/email-verification';
import { ConnectedIdentities } from '../features/identity/connected-identities';
import { InvitationLinkService } from '../features/organizations/invitation-link.service';
import { WorkspaceSidebar } from './workspace-sidebar';
import { WorkspaceTopbar } from './workspace-topbar';
@Component({
  selector: 'app-application-shell',
  imports: [
    WorkspaceSidebar,
    WorkspaceTopbar,
    MatButtonModule,
    DiIcon,
    Dashboard,
    OrganizationWorkspace,
    InvitationAcceptance,
    EmailVerification,
    ConnectedIdentities,
  ],
  templateUrl: './application-shell.html',
  styleUrl: './application-shell.scss',
})
export class ApplicationShell {
  readonly api = inject(SessionApiService);
  readonly page = signal<Page>(readPage(window.location.search));
  readonly sidebar = signal(false);
  constructor() {
    const invitationLinks = inject(InvitationLinkService);
    effect(() => {
      if (invitationLinks.invitation()) this.go('organization');
    });
    const back = () => {
      this.page.set(readPage(window.location.search));
      this.sidebar.set(false);
    };
    window.addEventListener('popstate', back);
    inject(DestroyRef).onDestroy(() => window.removeEventListener('popstate', back));
  }
  get current() {
    return navigation.find((item) => item.id === this.page())!;
  }
  get initials(): string {
    return (
      this.api
        .account()
        ?.displayName.trim()
        .split(/\s+/)
        .slice(0, 2)
        .map((part) => part[0])
        .join('') ?? 'DI'
    ).toUpperCase();
  }
  go(page: Page): void {
    this.page.set(page);
    this.sidebar.set(false);
    const url = new URL(window.location.href);
    url.searchParams.set('view', page);
    if (url.href !== window.location.href)
      window.history.pushState(null, '', url.pathname + url.search);
  }
}
