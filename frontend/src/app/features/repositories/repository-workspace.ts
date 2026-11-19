import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpEventType, HttpResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { Organization, SessionApiService, accessError } from '../../core/session-api.service';
import { DiIcon } from '../../shared/di-icon';
import { TranslatePipe } from '../../core/translate.pipe';

interface Repository { id: string; organizationId: string; organizationName: string; fullName: string; defaultBranch: string; source: string; }
interface Candidate { externalId: string; fullName: string; defaultBranch: string; htmlUrl: string; visibility: string; archived: boolean; }
interface Installation { id: number; accountLogin: string; accountType: 'USER' | 'ORGANIZATION'; repositories: Candidate[]; }
interface Workspace { installations: Installation[]; importedRepositoryIds: string[]; }
interface GitHubSetupStatus { configured: boolean; canManage: boolean; detail: string; }
interface LocalRepository { id: string; name: string; fileCount: number; sizeBytes: number; uploadedAt: string; }
interface LocalFolderImportResult { id: string; name: string; fileCount: number; sizeBytes: number; }
@Component({
  selector: 'app-repository-workspace', imports: [DiIcon, TranslatePipe], templateUrl: './repository-workspace.html', styleUrl: './repository-workspace.scss',
})
export class RepositoryWorkspace implements OnInit {
  readonly api = inject(SessionApiService);
  readonly organizations = signal<Organization[]>([]);
  readonly organizationId = signal('');
  readonly githubStatus = signal<GitHubSetupStatus | null>(null);
  readonly installations = signal<Installation[]>([]);
  readonly importedIds = signal(new Set<string>());
  readonly repositories = signal<Repository[]>([]);
  readonly localRepositories = signal<LocalRepository[]>([]);
  readonly localFiles = signal<File[]>([]);
  readonly localName = signal('');
  readonly existingInstallationId = signal('');
  readonly uploadProgress = signal<number | null>(null);
  readonly selectedIds = signal(new Set<string>());
  readonly loading = signal(false);
  readonly statusLoading = signal(false);
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
        if (selected) { this.organizationId.set(selected.id); this.loadSetupStatus(selected.id); }
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
    const status = this.githubStatus();
    if (!status?.configured || !status.canManage) return;
    sessionStorage.setItem('di.github.organization', organizationId);
    this.loading.set(true); this.error.set('');
    this.api.get<Workspace>(`/api/organizations/${organizationId}/github/repositories`)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({ next: data => this.applyWorkspace(data), error: error => this.error.set(accessError(error)) });
  }

  private loadSetupStatus(organizationId: string): void {
    this.githubStatus.set(null); this.installations.set([]); this.importedIds.set(new Set()); this.repositories.set([]); this.error.set('');
    this.statusLoading.set(true);
    this.api.get<GitHubSetupStatus>(`/api/organizations/${organizationId}/github/status`)
      .pipe(finalize(() => this.statusLoading.set(false)))
      .subscribe({
        next: status => {
          if (this.organizationId() !== organizationId) return;
          this.githubStatus.set(status);
            if (status.canManage) this.loadLocalRepositories(organizationId);
            if (status.canManage) this.loadAccessibleRepositories();
            if (status.configured && status.canManage) this.loadWorkspace();
        },
        error: error => this.error.set(accessError(error)),
      });
  }

  chooseOrganization(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    this.organizationId.set(id); sessionStorage.setItem('di.github.organization', id);
    this.installations.set([]); this.localRepositories.set([]); this.localFiles.set([]); this.localName.set('');
    this.selectedIds.set(new Set()); this.loadSetupStatus(id);
  }

  selectLocalFolder(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    const ignored = new Set(['.git', 'node_modules', 'target', 'dist', 'build', '.angular']);
    const included = files.filter(file => !file.webkitRelativePath.split('/').slice(1, -1).some(part => ignored.has(part.toLowerCase())));
    const root = included[0]?.webkitRelativePath.split('/')[0] ?? '';
    this.localName.set(root);
    this.error.set('');
    const bytes = included.reduce((total, file) => total + file.size, 0);
    if (!included.length) { this.localFiles.set([]); this.error.set('No se encontraron archivos para importar.'); this.message.set(''); }
    else if (included.length > 2_000 || bytes > 100 * 1024 * 1024) {
      this.localFiles.set([]); this.error.set('La carpeta supera el límite de 2.000 archivos o 100 MiB.'); this.message.set('');
    } else { this.localFiles.set(included); this.message.set(''); }
  }

  uploadLocalFolder(): void {
    const organizationId = this.organizationId();
    const files = this.localFiles();
    if (!organizationId || !files.length || this.pending() || !this.githubStatus()?.canManage) return;
    const form = new FormData();
    form.append('name', this.localName());
    for (const file of files) {
      form.append('paths', file.webkitRelativePath);
      form.append('files', file, file.name);
    }
    this.pending.set(true); this.error.set(''); this.message.set('Subiendo la carpeta al almacenamiento privado…'); this.uploadProgress.set(0);
    this.api.upload<LocalFolderImportResult>(`/api/organizations/${organizationId}/repositories/local-folder`, form)
      .pipe(finalize(() => { this.pending.set(false); this.uploadProgress.set(null); }))
      .subscribe({
        next: event => {
          if (event.type === HttpEventType.UploadProgress) {
            const total = event.total ?? 0;
            if (total > 0) this.uploadProgress.set(Math.round(100 * event.loaded / total));
          } else if (event instanceof HttpResponse && event.body) {
            this.message.set(`Carpeta «${event.body.name}» importada. ${event.body.fileCount} archivos guardados; lista para asignar al equipo.`);
            this.localFiles.set([]); this.localName.set(''); this.loadLocalRepositories(organizationId);
            this.loadAccessibleRepositories();
          }
        },
        error: error => { this.message.set(''); this.error.set(accessError(error)); },
      });
  }

  private loadLocalRepositories(organizationId: string): void {
    this.api.get<LocalRepository[]>(`/api/organizations/${organizationId}/repositories/local-folders`)
      .subscribe({ next: items => this.localRepositories.set(items), error: error => this.error.set(accessError(error)) });
  }

  formatSize(bytes: number): string {
    return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  }

  selectedFolderSize(): string {
    return this.formatSize(this.localFiles().reduce((total, file) => total + file.size, 0));
  }

  beginInstall(): void {
    const organizationId = this.organizationId(); const status = this.githubStatus();
    if (!organizationId || this.pending() || !status?.configured || !status.canManage) return;
    sessionStorage.setItem('di.github.organization', organizationId); this.pending.set(true); this.error.set('');
    this.api.post<{ url: string }>(`/api/organizations/${organizationId}/github/installations`, {})
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({ next: result => window.location.assign(result.url), error: error => this.error.set(accessError(error)) });
  }

  connectExisting(): void {
    const organizationId = this.organizationId();
    const installationId = this.existingInstallationId().trim();
    if (!organizationId || !/^\d{1,19}$/.test(installationId) || this.pending() || !this.githubStatus()?.canManage) {
      this.error.set('Copia el número de instalación desde la URL de configuración de GitHub.');
      return;
    }
    sessionStorage.setItem('di.github.organization', organizationId);
    this.pending.set(true); this.error.set('');
    this.api.post<{ url: string }>(`/api/organizations/${organizationId}/github/installations/existing`,
      { installationId })
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({ next: result => window.location.assign(result.url), error: error => this.error.set(accessError(error)) });
  }

  updateExistingInstallationId(event: Event): void {
    this.existingInstallationId.set((event.target as HTMLInputElement).value);
  }

  toggle(externalId: string, checked: boolean): void {
    this.selectedIds.update(current => { const next = new Set(current); checked ? next.add(externalId) : next.delete(externalId); return next; });
  }

  isImported(id: string): boolean { return this.importedIds().has(id); }
  isSelected(id: string): boolean { return this.selectedIds().has(id); }
  sourceLabel(source: string): string { return source === 'local' ? 'Local' : source === 'github' ? 'GitHub' : 'Otro'; }
  visibleRepositories(): Repository[] { return this.repositories().filter(repository => repository.organizationId === this.organizationId()); }
  sourceUrl(repository: Repository): string { return `/api/organizations/${repository.organizationId}/repositories/${repository.id}/source`; }

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
    this.loadAccessibleRepositories();
  }

  private loadAccessibleRepositories(): void {
    this.api.get<Repository[]>('/api/account/workspace').subscribe({
      next: items => this.repositories.set(items), error: error => this.error.set(accessError(error)),
    });
  }
}
