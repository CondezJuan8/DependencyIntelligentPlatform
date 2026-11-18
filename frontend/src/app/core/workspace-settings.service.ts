import { Injectable, inject, signal } from '@angular/core';
import { catchError, EMPTY, finalize, Observable, tap } from 'rxjs';
import { Language, PreferencesService, Theme } from './preferences.service';
import { Organization, SessionApiService, WorkspaceLanguage, WorkspaceSettings, WorkspaceTheme } from './session-api.service';

@Injectable({ providedIn: 'root' })
export class WorkspaceSettingsService {
  private readonly api = inject(SessionApiService);
  private readonly preferences = inject(PreferencesService);
  private request = 0;
  readonly organizations = signal<Organization[]>([]);
  readonly activeOrganizationId = signal<string | null>(null);
  readonly settings = signal<WorkspaceSettings | null>(null);
  readonly loading = signal(false);

  initializeDefault(): void {
    if (!this.api.account() || this.organizations().length || this.loading()) return;
    this.loading.set(true);
    const request = ++this.request;
    this.api.get<Organization[]>('/api/organizations').pipe(finalize(() => {
      if (request === this.request) this.loading.set(false);
    })).subscribe({
      next: organizations => {
        if (request !== this.request) return;
        this.organizations.set(organizations);
        const id = organizations.find(item => item.id === this.activeOrganizationId())?.id ?? organizations[0]?.id;
        if (id) this.activate(id);
      },
      error: () => this.preferences.clearWorkspace(),
    });
  }

  setOrganizations(organizations: Organization[]): void {
    this.organizations.set(organizations);
    const selected = organizations.find(item => item.id === this.activeOrganizationId()) ?? organizations[0];
    if (selected) this.activate(selected.id);
    else this.clear();
  }

  activate(organizationId: string): void {
    if (this.activeOrganizationId() === organizationId && this.settings()) return;
    this.activeOrganizationId.set(organizationId);
    this.settings.set(null);
    this.preferences.clearWorkspace();
    const request = ++this.request;
    this.loading.set(true);
    this.api.get<WorkspaceSettings>(`/api/organizations/${organizationId}/settings`).pipe(finalize(() => {
      if (request === this.request) this.loading.set(false);
    })).subscribe({
      next: settings => {
        if (request !== this.request || settings.organizationId !== this.activeOrganizationId()) return;
        this.apply(settings);
      },
      error: () => { if (request === this.request) this.preferences.clearWorkspace(); },
    });
  }

  save(theme: WorkspaceTheme, language: WorkspaceLanguage): Observable<WorkspaceSettings> {
    const organizationId = this.activeOrganizationId();
    if (!organizationId) throw new Error('Selecciona una organización para guardar sus preferencias.');
    return this.api.post<WorkspaceSettings>(`/api/organizations/${organizationId}/settings`, { theme, language })
      .pipe(tap(settings => this.apply(settings)));
  }

  clear(): void {
    this.request++;
    this.organizations.set([]);
    this.activeOrganizationId.set(null);
    this.settings.set(null);
    this.loading.set(false);
    this.preferences.clearWorkspace();
  }

  private apply(settings: WorkspaceSettings): void {
    this.settings.set(settings);
    this.preferences.setWorkspace(this.theme(settings.theme), this.language(settings.language));
  }

  private theme(value: WorkspaceTheme): Theme { return value.toLowerCase() as Theme; }
  private language(value: WorkspaceLanguage): Language { return value.toLowerCase() as Language; }
}
