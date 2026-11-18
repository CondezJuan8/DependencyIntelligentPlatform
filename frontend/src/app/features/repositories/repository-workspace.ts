import { Component, inject, OnInit, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { Organization, SessionApiService, accessError } from '../../core/session-api.service';
import { DiIcon } from '../../shared/di-icon';

interface Repository { id: string; organizationId: string; organizationName: string; fullName: string; defaultBranch: string; }
interface Candidate { externalId: string; fullName: string; defaultBranch: string; htmlUrl: string; visibility: string; archived: boolean; }
interface Installation { id: number; accountLogin: string; accountType: 'USER' | 'ORGANIZATION'; repositories: Candidate[]; }
interface Workspace { installations: Installation[]; importedRepositoryIds: string[]; }
@Component({
  selector: 'app-repository-workspace', imports: [DiIcon], templateUrl: './repository-workspace.html', styleUrl: './repository-workspace.scss',
})
export class RepositoryWorkspace implements OnInit {
  readonly api = inject(SessionApiService);
  readonly organizations = signal<Organization[]>([]);
  readonly organizationId = signal('');
  readonly installations = signal<Installation[]>([]);
  readonly importedIds = signal(new Set<string>());
  readonly repositories = signal<Repository[]>([]);
  readonly selectedIds = signal(new Set<string>());
  readonly loading = signal(false);
  readonly pending = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  readonly isOwner = this.api.account()?.accountType === 'OWNER';

  ngOnInit(): void {
    if (!this.isOwner) { this.loadMemberRepositories(); return; }
    this.consumeInstallCallback();
    this.api.get<Organization[]>('/api/organizations').subscribe({
      next: items => {
        this.organizations.set(items);
        const saved = sessionStorage.getItem('di.github.organization');
        const selected = items.find(item => item.id === saved) ?? items[0];
        if (selected) { this.organizationId.set(selected.id); this.loadWorkspace(); }
      },
      error: error => this.error.set(accessError(error)),
    });
  }

  private consumeInstallCallback(): void {
    const url = new URL(window.location.href);
    const connection = url.searchParams.get('github_connection');
    const installationId = url.searchParams.get('installation_id');
    const state = url.searchParams.get('state');
    const action = url.searchParams.get('setup_action');
    if (connection === 'connected') this.message.set('La instalación de GitHub quedó verificada y conectada.');
    if (connection === 'cancelled') this.message.set('Cancelaste la autorización de GitHub. Puedes intentarlo otra vez.');
    if (connection === 'error') this.error.set('No se pudo verificar la instalación con la identidad de GitHub autorizada. Revisa el acceso e inténtalo otra vez.');
    if (connection) url.searchParams.delete('github_connection');
    if (action === 'request') {
      this.message.set('La instalación quedó pendiente de aprobación en GitHub.');
      url.searchParams.delete('installation_id'); url.searchParams.delete('setup_action'); url.searchParams.delete('state');
      window.history.replaceState(null, '', url.pathname + url.search + url.hash);
      return;
    }
    if (!installationId || !state || !['install', 'update'].includes(action ?? '')) {
      if (connection) window.history.replaceState(null, '', url.pathname + url.search + url.hash);
      return;
    }
    const organizationId = sessionStorage.getItem('di.github.organization');
    url.searchParams.delete('installation_id'); url.searchParams.delete('setup_action'); url.searchParams.delete('state');
    window.history.replaceState(null, '', url.pathname + url.search + url.hash);
    if (!organizationId || !/^\d{1,20}$/.test(installationId)) {
      this.error.set('No encontramos la organización o la instalación que inició este retorno. Vuelve a conectarla.');
      return;
    }
    this.organizationId.set(organizationId); this.pending.set(true); this.message.set('Confirma la instalación con tu identidad de GitHub…');
    this.api.post<{ url: string }>(`/api/organizations/${organizationId}/github/installations/authorize`, { state, installationId })
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({ next: result => window.location.assign(result.url),
        error: error => { this.message.set(''); this.error.set(accessError(error)); } });
  }

  loadMemberRepositories(): void {
    this.loading.set(true); this.error.set('');
    this.api.get<Repository[]>('/api/account/workspace').pipe(finalize(() => this.loading.set(false)))
      .subscribe({ next: data => this.repositories.set(data), error: error => this.error.set(accessError(error)) });
  }

  loadWorkspace(): void {
    const organizationId = this.organizationId(); if (!organizationId || this.loading()) return;
    sessionStorage.setItem('di.github.organization', organizationId);
    this.loading.set(true); this.error.set('');
    this.api.get<Workspace>(`/api/organizations/${organizationId}/github/repositories`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({ next: data => this.applyWorkspace(data), error: error => this.error.set(accessError(error)) });
  }

  chooseOrganization(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    this.organizationId.set(id); sessionStorage.setItem('di.github.organization', id);
    this.installations.set([]); this.selectedIds.set(new Set()); this.loadWorkspace();
  }

  beginInstall(): void {
    const organizationId = this.organizationId(); if (!organizationId || this.pending()) return;
    sessionStorage.setItem('di.github.organization', organizationId); this.pending.set(true); this.error.set('');
    this.api.post<{ url: string }>(`/api/organizations/${organizationId}/github/installations`, {})
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({ next: result => window.location.assign(result.url), error: error => this.error.set(accessError(error)) });
  }

  toggle(externalId: string, checked: boolean): void {
    this.selectedIds.update(current => { const next = new Set(current); checked ? next.add(externalId) : next.delete(externalId); return next; });
  }

  isImported(id: string): boolean { return this.importedIds().has(id); }
  isSelected(id: string): boolean { return this.selectedIds().has(id); }

  importSelected(): void {
    const organizationId = this.organizationId(); const repositoryIds = [...this.selectedIds()];
    if (!organizationId || !repositoryIds.length || this.pending()) return;
    this.pending.set(true); this.error.set(''); this.message.set('');
    this.api.post<{ importedCount: number }>(`/api/organizations/${organizationId}/github/repositories/import`, { repositoryIds })
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({ next: result => { this.message.set(`${result.importedCount} repositorios sincronizados con la organización.`); this.selectedIds.set(new Set()); this.loadWorkspace(); },
        error: error => this.error.set(accessError(error)) });
  }

  private applyWorkspace(data: Workspace): void {
    const imported = new Set(data.importedRepositoryIds);
    this.installations.set(data.installations); this.importedIds.set(imported);
    const organization = this.organizations().find(item => item.id === this.organizationId());
    this.repositories.set(data.installations.flatMap(installation => installation.repositories)
      .filter(repository => imported.has(repository.externalId))
      .map(repository => ({ id: repository.externalId, organizationId: this.organizationId(), organizationName: organization?.name ?? '',
        fullName: repository.fullName, defaultBranch: repository.defaultBranch })));
  }
}
