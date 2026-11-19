import { Component, inject, OnInit, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Organization, SessionApiService, accessError } from '../../core/session-api.service';
import { DiIcon } from '../../shared/di-icon';
import { WorkspaceMetrics } from './workspace-metrics';
import { AssistantPreview } from './assistant-preview';
interface DashboardRepository {id:string;organizationId:string;organizationName:string;fullName:string;defaultBranch:string;source:string;}
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
  readonly repositories = signal<DashboardRepository[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  ngOnInit(): void {
    this.load();
  }
  load(): void {
    this.loading.set(true);
    this.error.set('');
    let completed = 0;
    const done = () => { if (++completed === 2) this.loading.set(false); };
    this.api.get<Organization[]>('/api/organizations').subscribe({
      next: data => { this.organizations.set(data); done(); },
      error: error => { this.error.set(accessError(error)); done(); },
    });
    this.api.get<DashboardRepository[]>('/api/account/workspace')
      .subscribe({ next: data => { this.repositories.set(data); done(); }, error: error => { this.error.set(accessError(error)); done(); } });
  }
  get dependencyCount(): number | null { return null; }
  get firstName(): string {
    return this.api.account()?.displayName.trim().split(/\s+/)[0] ?? '';
  }
}
