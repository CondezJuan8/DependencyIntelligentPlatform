import { PlatformAdministration } from '../features/administration/platform-administration';
import { RepositoryWorkspace } from '../features/repositories/repository-workspace';
import { InventoryWorkspace } from '../features/inventory/inventory-workspace';
import { VulnerabilitiesWorkspace } from '../features/vulnerabilities/vulnerabilities-workspace';
import { AnalysisWorkspace } from '../features/scans/analysis-workspace';
import { AccountSettings } from '../features/identity/account-settings';
import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { SessionApiService } from '../core/session-api.service';
import { navigation, Page, initialPage, availableNavigation } from '../core/navigation';
import { DiIcon } from './di-icon';
import { Dashboard } from '../features/dashboard/dashboard';
import { OrganizationWorkspace } from '../features/organizations/organization-workspace';
import { InvitationAcceptance } from '../features/organizations/invitation-acceptance';
import { ConnectedIdentities } from '../features/identity/connected-identities';
import { InvitationLinkService } from '../features/organizations/invitation-link.service';
import { WorkspaceSidebar } from './workspace-sidebar';
import { WorkspaceTopbar } from './workspace-topbar';
import { WorkspaceSettingsService } from '../core/workspace-settings.service';
import { DocumentCenter } from '../features/documents/document-center';
import { AssistantWorkspace } from '../features/assistant/assistant-workspace';
import { AssistantPanel } from './assistant-panel';
import { ReportsWorkspace } from '../features/reports/reports-workspace';
@Component({
  selector: 'app-application-shell',
  imports: [PlatformAdministration,RepositoryWorkspace,InventoryWorkspace,VulnerabilitiesWorkspace,AnalysisWorkspace,AccountSettings,DocumentCenter,AssistantWorkspace,AssistantPanel,ReportsWorkspace,
    WorkspaceSidebar,
    WorkspaceTopbar,
    MatButtonModule,
    DiIcon,
    Dashboard,
    OrganizationWorkspace,
    InvitationAcceptance,
    ConnectedIdentities,
  ],
  templateUrl: './application-shell.html',
  styleUrl: './application-shell.scss',
})
export class ApplicationShell {
  readonly api = inject(SessionApiService);
  readonly page = signal<Page>(initialPage(window.location.search,this.api.account()));
  readonly sidebar = signal(false);
  readonly assistantOpen = signal(false);
  constructor() {
    const workspaceSettings = inject(WorkspaceSettingsService);
    effect(() => {
      if (this.api.account()) workspaceSettings.initializeDefault();
      else workspaceSettings.clear();
    });
    const invitationLinks = inject(InvitationLinkService);
    effect(() => {
      if (invitationLinks.invitation()) this.go(this.api.account()?.accountType === 'MEMBER' ? 'repositories' : 'organization');
    });
    const back = () => {
      this.page.set(initialPage(window.location.search,this.api.account()));
      this.closeNavigation(false);
    };
    const desktop = window.matchMedia('(min-width: 980px)');
    const closeOnDesktop = () => { if (desktop.matches) this.closeNavigation(false); };
    window.addEventListener('popstate', back);
    desktop.addEventListener('change', closeOnDesktop);
    inject(DestroyRef).onDestroy(() => {
      window.removeEventListener('popstate', back);
      desktop.removeEventListener('change', closeOnDesktop);
    });
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
  go(page: Page, repositoryId?: string): void {
if(!availableNavigation(this.api.account()).some(item=>item.id===page))page=initialPage('',this.api.account());
    this.page.set(page);
    this.closeNavigation(false);
    requestAnimationFrame(() => document.getElementById('workspace-main')?.focus({ preventScroll: true }));
    const url = new URL(window.location.href);
    url.searchParams.set('view', page);
    if (repositoryId) url.searchParams.set('repositoryId', repositoryId);
    else url.searchParams.delete('repositoryId');
    url.hash = '';
    if (url.href !== window.location.href)
      window.history.pushState(null, '', url.pathname + url.search);
  }
  toggleNavigation(): void {
    if (this.sidebar()) {
      this.closeNavigation();
      return;
    }
    this.sidebar.set(true);
    requestAnimationFrame(() => document.getElementById('workspace-navigation-close')?.focus());
  }
  closeNavigation(restoreFocus = true): void {
    if (!this.sidebar()) return;
    this.sidebar.set(false);
    if (restoreFocus) requestAnimationFrame(() => document.getElementById('workspace-menu-trigger')?.focus());
  }
  openInventory(repositoryId: string): void {
    this.go('inventory', repositoryId);
  }
  closeAssistant():void { this.assistantOpen.set(false); }
}
