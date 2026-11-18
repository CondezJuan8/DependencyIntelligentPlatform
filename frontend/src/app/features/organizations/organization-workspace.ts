import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { accessError, Organization, SessionApiService } from '../../core/session-api.service';
import { OrganizationAccess } from './organization-access';
import { DiIcon } from '../../shared/di-icon';
import { WorkspaceSettingsService } from '../../core/workspace-settings.service';

@Component({
  selector: 'app-organization-workspace',
  imports: [
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    OrganizationAccess,
    DiIcon,
  ],
  templateUrl: './organization-workspace.html',
  styleUrl: './organization-workspace.scss',
})
export class OrganizationWorkspace implements OnInit {
  private readonly api = inject(SessionApiService);
  private readonly workspaceSettings = inject(WorkspaceSettingsService);
  readonly organizations = signal<Organization[]>([]);
  readonly loading = signal(true);
  readonly pending = signal(false);
  readonly error = signal('');
  readonly selected = signal<Organization | null>(null);
  readonly creating = signal(false);
  name = '';

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set('');
    this.api
      .get<Organization[]>('/api/organizations')
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (organizations) => {
          this.organizations.set(organizations);
          this.workspaceSettings.setOrganizations(organizations);
          this.selected.set(
            organizations.find((item) => item.id === this.workspaceSettings.activeOrganizationId()) ??
              organizations.find((item) => item.id === this.selected()?.id) ??
              organizations[0] ??
              null,
          );
        },
        error: (error) => this.error.set(accessError(error)),
      });
  }

  create(form: NgForm): void {
    if (this.pending() || form.invalid || !this.name.trim()) return;
    this.pending.set(true);
    this.error.set('');
    this.api
      .post<Organization>('/api/organizations', { name: this.name })
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({
        next: (organization) => {
          this.organizations.update((items) =>
            [...items, organization].sort((a, b) => a.name.localeCompare(b.name)),
          );
          form.resetForm({ name: '' });
          this.selected.set(organization);
          this.workspaceSettings.setOrganizations(this.organizations());
          this.workspaceSettings.activate(organization.id);
          this.creating.set(false);
        },
        error: (error) => this.error.set(accessError(error)),
      });
  }
  choose(event: Event): void {
    this.selected.set(
      this.organizations().find((item) => item.id === (event.target as HTMLSelectElement).value) ??
        null,
    );
    if (this.selected()) this.workspaceSettings.activate(this.selected()!.id);
  }
}
