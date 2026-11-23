import { HttpEventType } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
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
  readonly canManage = computed(() => this.api.account() !== null);

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

  changeOrganization(event: Event): void { this.organizationId.set((event.target as HTMLSelectElement).value); this.load(); }
  visibleRepositories(): Repository[] { return this.repositories().filter(repo => repo.organizationId === this.organizationId()); }
  load(): void {
    const id = this.organizationId(); if (!id) return;
    this.loading.set(true); this.error.set('');
    this.api.get<DocumentSummary[]>(`/api/organizations/${id}/documents`).pipe(finalize(() => this.loading.set(false)))
      .subscribe({ next: data => this.documents.set(data), error: error => this.error.set(accessError(error)) });
  }
  toggleRepository(id: string, checked: boolean): void {
    const next = new Set(this.selectedRepositories()); checked ? next.add(id) : next.delete(id); this.selectedRepositories.set(next);
  }
  chooseFile(event: Event): void { this.file.set((event.target as HTMLInputElement).files?.[0] ?? null); }
  upload(): void {
    const file = this.file(), org = this.organizationId();
    if (!file || !org || this.pending()) return;
    const form = new FormData(); form.append('name', this.name().trim() || file.name); form.append('description', this.description()); form.append('file', file);
    for (const repositoryId of this.selectedRepositories()) form.append('repositoryIds', repositoryId);
    this.pending.set(true); this.error.set(''); this.notice.set('');
    this.api.upload<DocumentSummary>(`/api/organizations/${org}/documents`, form).pipe(finalize(() => this.pending.set(false))).subscribe({
      next: event => { if (event.type === HttpEventType.Response && event.body) { this.notice.set(this.preferences.text('Documento agregado al centro.')); this.file.set(null); this.name.set(''); this.description.set(''); this.selectedRepositories.set(new Set()); this.load(); } },
      error: error => this.error.set(accessError(error)),
    });
  }
  search(): void {
    const org = this.organizationId(), query = this.query().trim(); if (!org || !query) { this.hits.set([]); return; }
    this.api.get<SearchHit[]>(`/api/organizations/${org}/documents/search?query=${encodeURIComponent(query)}`).subscribe({ next: hits => this.hits.set(hits), error: error => this.error.set(accessError(error)) });
  }
  toggleVersions(document: DocumentSummary): void {
    if (this.versions()[document.id]) { const next = { ...this.versions() }; delete next[document.id]; this.versions.set(next); return; }
    this.api.get<DocumentVersion[]>(`/api/organizations/${this.organizationId()}/documents/${document.id}/versions`).subscribe({ next: items => this.versions.set({ ...this.versions(), [document.id]: items }), error: error => this.error.set(accessError(error)) });
  }
  download(documentId: string, versionId?: string): void {
    const query = versionId ? `?versionId=${encodeURIComponent(versionId)}` : '';
    window.location.assign(`/api/organizations/${this.organizationId()}/documents/${documentId}/download${query}`);
  }
  addVersion(documentId: string, event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0]; if (!file) return;
    const form = new FormData(); form.append('file', file); const org = this.organizationId(); this.pending.set(true);
    this.api.upload<DocumentSummary>(`/api/organizations/${org}/documents/${documentId}/versions`, form).pipe(finalize(() => this.pending.set(false))).subscribe({ next: () => { this.notice.set(this.preferences.text('Nueva versión guardada.')); const next = { ...this.versions() }; delete next[documentId]; this.versions.set(next); this.loadVersions(documentId); this.load(); }, error: error => this.error.set(accessError(error)) });
  }
  private loadVersions(documentId: string): void { this.api.get<DocumentVersion[]>(`/api/organizations/${this.organizationId()}/documents/${documentId}/versions`).subscribe({ next: items => this.versions.set({ ...this.versions(), [documentId]: items }), error: error => this.error.set(accessError(error)) }); }
  remove(document: DocumentSummary): void {
    if (!window.confirm(this.preferences.text('Se eliminarán el archivo original, sus versiones y el texto extraído. Esta acción no se puede deshacer.'))) return;
    this.api.delete<void>(`/api/organizations/${this.organizationId()}/documents/${document.id}`).subscribe({ next: () => { this.notice.set(this.preferences.text('Documento eliminado.')); this.load(); }, error: error => this.error.set(accessError(error)) });
  }
  formatSize(bytes: number): string { return bytes < 1024 * 1024 ? `${Math.max(1, Math.round(bytes / 1024))} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`; }
}
