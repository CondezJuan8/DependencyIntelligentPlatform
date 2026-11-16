import { Component, inject, OnInit, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { Organization, SessionApiService, accessError } from '../../core/session-api.service';
import { DiIcon } from '../../shared/di-icon';
import { WorkspaceMetrics } from './workspace-metrics';
import { AssistantPreview } from './assistant-preview';
@Component({
  selector: 'app-dashboard',
  imports: [MatButtonModule, DiIcon, WorkspaceMetrics, AssistantPreview],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  readonly api = inject(SessionApiService);
  readonly manage = output<void>();
  readonly organizations = signal<Organization[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  ngOnInit(): void {
    this.load();
  }
  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api
      .get<Organization[]>('/api/organizations')
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (data) => this.organizations.set(data),
        error: (error) => this.error.set(accessError(error)),
      });
  }
  get firstName(): string {
    return this.api.account()?.displayName.trim().split(/\s+/)[0] ?? '';
  }
}
