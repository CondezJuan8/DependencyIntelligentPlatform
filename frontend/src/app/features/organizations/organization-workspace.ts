import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { finalize } from 'rxjs';
import { accessError, Organization, SessionApiService } from '../../core/session-api.service';

@Component({
  selector: 'app-organization-workspace',
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule],
  templateUrl: './organization-workspace.html',
  styleUrl: './organization-workspace.scss',
})
export class OrganizationWorkspace implements OnInit {
  private readonly api = inject(SessionApiService);
  readonly organizations = signal<Organization[]>([]);
  readonly loading = signal(true);
  readonly pending = signal(false);
  readonly error = signal('');
  name = '';

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.get<Organization[]>('/api/organizations').pipe(finalize(() => this.loading.set(false))).subscribe({
      next: organizations => this.organizations.set(organizations),
      error: error => this.error.set(accessError(error)),
    });
  }

  create(form: NgForm): void {
    if (this.pending() || form.invalid || !this.name.trim()) return;
    this.pending.set(true);
    this.error.set('');
    this.api.post<Organization>('/api/organizations', { name: this.name }).pipe(finalize(() => this.pending.set(false))).subscribe({
      next: organization => {
        this.organizations.update(items => [...items, organization].sort((a, b) => a.name.localeCompare(b.name)));
        form.resetForm({ name: '' });
      },
      error: error => this.error.set(accessError(error)),
    });
  }
}
