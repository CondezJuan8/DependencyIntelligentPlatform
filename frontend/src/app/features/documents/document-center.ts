import { HttpEventType } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { finalize } from 'rxjs';
import { Organization, SessionApiService, accessError } from '../../core/session-api.service';
import { PreferencesService } from '../../core/preferences.service';
import { TranslatePipe } from '../../core/translate.pipe';

interface Repository { id: string; organizationId: string; organizationName: string; fullName: string; source: string; }
interface DocumentSummary { id: string; name: string; type: string; description: string; status: string; indexingStatus: string; latestVersion: number; versionCount: number; sizeBytes: number; mimeType: string; sha256: string; uploadedAt: string; repositories: string[]; }
interface DocumentVersion { id: string; version: number; filename: string; mimeType: string; sizeBytes: number; sha256: string; indexingStatus: string; uploadedAt: string; }
interface SearchHit { documentId: string; versionId: string; documentName: string; filename: string; excerpt: string; pageStart: number | null; repositoryName: string; }

@Component({ selector: 'app-document-center', imports: [TranslatePipe], templateUrl: './document-center.html', styleUrl: './document-center.scss' })
export class DocumentCenter implements OnInit {
  private readonly api = inject(SessionApiService);
  readonly preferences = inject(PreferencesService);
  readonly organizations = signal<Organization[]>([]);
  readonly organizationId = signal('');
  readonly repositories = signal<Repository[]>([]);
  readonly selectedRepositories = signal(new Set<string>());
  readonly documents = signal<DocumentSummary[]>([]);
  readonly versions = signal<Record<string, DocumentVersion[]>>({});
  readonly query = signal('');
  readonly hits = signal<SearchHit[]>([]);
  readonly name = signal('');
  readonly description = signal('');
  readonly file = signal<File | null>(null);
  readonly pending = signal(false);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly canManage = signal(false);
  readonly searched = signal(false);
  private listRequest = 0;
  private searchRequest = 0;

  ngOnInit(): void {
    this.api.get<Organization[]>('/api/organizations').subscribe({
      next: organizations => {
        this.organizations.set(organizations);
        const selected = organizations[0];
        if (selected) { this.organizationId.set(selected.id); this.load(); }
      }, error: error => this.error.set(accessError(error)),
    });
    this.api.get<Repository[]>('/api/account/workspace').subscribe({
      next: repos => this.repositories.set(repos), error: error => this.error.set(accessError(error)),
    });
  }

  changeOrganization(event: Event): void {
    this.organizationId.set((event.target as HTMLSelectElement).value);
    this.canManage.set(false);
    this.documents.set([]);
    this.versions.set({});
    this.selectedRepositories.set(new Set());
    this.notice.set('');
    this.resetSearch();
    this.load();
  }
  visibleRepositories(): Repository[] { return this.repositories().filter(repo => repo.organizationId === this.organizationId()); }
  load(): void {
    const id = this.organizationId();
    if (!id) return;
    const request = ++this.listRequest;
    this.loading.set(true);
    this.error.set('');
    const endpoint = `/api/organizations/${id}/documents`;
    this.api.get<{ canManage: boolean }>(`${endpoint}/capabilities`).subscribe({
      next: result => { if (request === this.listRequest) this.canManage.set(result.canManage); },
      error: () => { if (request === this.listRequest) this.canManage.set(false); },
    });
    this.api.get<DocumentSummary[]>(endpoint).pipe(finalize(() => {
      if (request === this.listRequest) this.loading.set(false);
    })).subscribe({
      next: data => { if (request === this.listRequest) this.documents.set(data); },
      error: error => { if (request === this.listRequest) this.error.set(accessError(error)); },
    });
  }
  toggleRepository(id: string, checked: boolean): void {
    const next = new Set(this.selectedRepositories()); checked ? next.add(id) : next.delete(id); this.selectedRepositories.set(next);
  }
  chooseFile(event: Event): void { this.file.set((event.target as HTMLInputElement).files?.[0] ?? null); }
  upload(): void {
    const file = this.file(), org = this.organizationId();
    if (!file || !org || this.pending() || !this.canManage()) return;
    const form = new FormData(); form.append('name', this.name().trim() || file.name); form.append('description', this.description()); form.append('file', file);
    for (const repositoryId of this.selectedRepositories()) form.append('repositoryIds', repositoryId);
    this.pending.set(true); this.error.set(''); this.notice.set('');
    this.api.upload<DocumentSummary>(`/api/organizations/${org}/documents`, form).pipe(finalize(() => this.pending.set(false))).subscribe({
      next: event => { if (event.type === HttpEventType.Response && event.body) { this.notice.set(this.preferences.text('Documento agregado al centro.')); this.file.set(null); this.name.set(''); this.description.set(''); this.selectedRepositories.set(new Set()); this.load(); } },
      error: error => this.error.set(accessError(error)),
    });
  }
  resetSearch(): void {
    this.searchRequest++;
    this.hits.set([]);
    this.searched.set(false);
  }
  search(): void {
    this.resetSearch();
    const org = this.organizationId(), query = this.query().trim();
    if (!org || !query) return;
    const request = this.searchRequest;
    this.api.get<SearchHit[]>(`/api/organizations/${org}/documents/search?query=${encodeURIComponent(query)}`).subscribe({
      next: hits => {
        if (request !== this.searchRequest) return;
        this.hits.set(hits);
        this.searched.set(true);
      },
      error: error => { if (request === this.searchRequest) this.error.set(accessError(error)); },
    });
  }
  toggleVersions(document: DocumentSummary): void {
    if (this.versions()[document.id]) { const next = { ...this.versions() }; delete next[document.id]; this.versions.set(next); return; }
    this.loadVersions(document.id);
  }
  download(documentId: string, versionId?: string): void {
    const query = versionId ? `?versionId=${encodeURIComponent(versionId)}` : '';
    window.location.assign(`/api/organizations/${this.organizationId()}/documents/${documentId}/download${query}`);
  }
  addVersion(documentId: string, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    const org = this.organizationId();
    if (!file || !org || !this.canManage() || this.pending()) return;
    const form = new FormData();
    form.append('file', file);
    this.pending.set(true);
    this.error.set('');
    this.notice.set('');
    this.api.upload<DocumentSummary>(`/api/organizations/${org}/documents/${documentId}/versions`, form)
      .pipe(finalize(() => { this.pending.set(false); input.value = ''; }))
      .subscribe({
        next: event => {
          if (event.type !== HttpEventType.Response) return;
          this.notice.set(this.preferences.text('Nueva versión guardada.'));
          this.loadVersions(documentId);
          this.load();
        },
        error: error => this.error.set(accessError(error)),
      });
  }
  private loadVersions(documentId: string): void {
    const org = this.organizationId();
    this.api.get<DocumentVersion[]>(`/api/organizations/${org}/documents/${documentId}/versions`).subscribe({
      next: items => {
        if (org === this.organizationId()) this.versions.update(versions => ({ ...versions, [documentId]: items }));
      },
      error: error => { if (org === this.organizationId()) this.error.set(accessError(error)); },
    });
  }
  remove(document: DocumentSummary): void {
    if (!window.confirm(this.preferences.text('Se eliminarán el archivo original, sus versiones y el texto extraído. Esta acción no se puede deshacer.'))) return;
    this.api.delete<void>(`/api/organizations/${this.organizationId()}/documents/${document.id}`).subscribe({ next: () => { this.notice.set(this.preferences.text('Documento eliminado.')); this.load(); }, error: error => this.error.set(accessError(error)) });
  }
  formatSize(bytes: number): string { return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`; }
}
