import { Component, inject, OnInit, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { Organization, SessionApiService, accessError } from '../../core/session-api.service';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { DiIcon } from '../../shared/di-icon';
import { WorkspaceMetrics } from './workspace-metrics';
import { AssistantPreview } from './assistant-preview';
interface DashboardRepository {id:string;organizationId:string;organizationName:string;fullName:string;defaultBranch:string;source:string;}
interface DashboardAnalysis {scanId:string;source:string;sourceSha256:string;commitSha:string|null;analyzedAt:string;manifestCount:number;unresolvedCount:number;dependencies:{coordinates:{name:string;version:string}}[];}
interface RecentAnalysis extends DashboardAnalysis {repositoryName:string;organizationName:string;}
@Component({
  selector: 'app-dashboard',
  imports: [MatButtonModule, DiIcon, WorkspaceMetrics, AssistantPreview, DatePipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  readonly api = inject(SessionApiService);
  readonly manage = output<void>();
  readonly organizations = signal<Organization[]>([]);
  readonly repositories = signal<DashboardRepository[]>([]);
  readonly analyses = signal<RecentAnalysis[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  ngOnInit(): void {
    this.load();
  }
  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.get<Organization[]>('/api/organizations').subscribe({
      next: data => this.organizations.set(data),
      error: error => this.error.set(accessError(error)),
    });
    this.api.get<DashboardRepository[]>('/api/account/workspace')
      .subscribe({ next: data => { this.repositories.set(data); this.loadAnalyses(data); }, error: error => { this.error.set(accessError(error)); this.loading.set(false); } });
  }
  get dependencyCount(): number | null { return this.analyses().length ? this.analyses().reduce((sum, scan) => sum + scan.dependencies.length, 0) : null; }
  private loadAnalyses(repositories: DashboardRepository[]): void {
    if (!repositories.length) { this.analyses.set([]); this.loading.set(false); return; }
    forkJoin(repositories.map(repository => this.api.get<DashboardAnalysis | null>(`/api/organizations/${repository.organizationId}/repositories/${repository.id}/maven-analysis`).pipe(catchError(() => of(null)))))
      .subscribe(results => {
        this.analyses.set(results.flatMap((result, index) => result ? [{...result, repositoryName: repositories[index].fullName, organizationName: repositories[index].organizationName}] : []).sort((a,b)=>Date.parse(b.analyzedAt)-Date.parse(a.analyzedAt)));
        this.loading.set(false);
      });
  }
  get firstName(): string {
    return this.api.account()?.displayName.trim().split(/\s+/)[0] ?? '';
  }
}
