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
type AssessmentStatus = 'NOT_ASSESSED' | 'MATCHED' | 'NO_KNOWN_ADVISORIES' | 'INCOMPLETE' | 'UNAVAILABLE' | 'STALE';
interface DashboardAdvisory {id:string;summary:string|null;severity:string;url:string;}
interface DashboardVersionAssessment {packageVersion:{ecosystem:string;namespace:string|null;name:string;version:string};status:AssessmentStatus;checkedAt:string|null;advisories:DashboardAdvisory[];}
interface DashboardVulnerabilityAssessment {generatedAt:string;assessedVersions:number;affectedVersions:number;advisoryCount:number;unavailableVersions:number;staleVersions:number;assessments:DashboardVersionAssessment[];}
type RiskCategory = 'critical'|'high'|'warning'|'clear';
interface DashboardFinding {repositoryName:string;ecosystem:string;packageName:string;version:string;advisory:DashboardAdvisory;category:RiskCategory;}
@Component({
  selector: 'app-dashboard',
  imports: [MatButtonModule, DiIcon, WorkspaceMetrics, AssistantPreview, DatePipe],
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.scss', './dashboard-security.scss'],
})
export class Dashboard implements OnInit {
  readonly api = inject(SessionApiService);
  readonly manage = output<void>();
  readonly organizations = signal<Organization[]>([]);
  readonly repositories = signal<DashboardRepository[]>([]);
  readonly analyses = signal<RecentAnalysis[]>([]);
  readonly vulnerabilityAssessments = signal<{repository:DashboardRepository;assessment:DashboardVulnerabilityAssessment}[]>([]);
  readonly unavailableVulnerabilityRepositories = signal(0);
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
  get findings(): DashboardFinding[] {
    return this.vulnerabilityAssessments().flatMap(({repository, assessment}) => assessment.assessments.flatMap(version => version.advisories.map(advisory => ({
      repositoryName: repository.fullName,
      ecosystem: version.packageVersion.ecosystem,
      packageName: `${version.packageVersion.namespace ? `${version.packageVersion.namespace}:` : ''}${version.packageVersion.name}`,
      version: version.packageVersion.version,
      advisory,
      category: this.riskCategory(advisory.severity),
    })))).sort((a,b)=>this.riskRank(b.category)-this.riskRank(a.category));
  }
  get riskCounts(): Record<RiskCategory, number> {
    const highestByVersion = new Map<string, RiskCategory>();
    for (const {repository,assessment} of this.vulnerabilityAssessments()) for (const item of assessment.assessments) {
      const key = JSON.stringify([repository.id,item.packageVersion.ecosystem,item.packageVersion.namespace,item.packageVersion.name,item.packageVersion.version]);
      const category = item.advisories.length ? item.advisories.map(advisory => this.riskCategory(advisory.severity)).sort((a,b)=>this.riskRank(b)-this.riskRank(a))[0] : item.status === 'NO_KNOWN_ADVISORIES' ? 'clear' : null;
      if (category && (!highestByVersion.has(key) || this.riskRank(category) > this.riskRank(highestByVersion.get(key)!))) highestByVersion.set(key, category);
    }
    return {critical:[...highestByVersion.values()].filter(value=>value==='critical').length,high:[...highestByVersion.values()].filter(value=>value==='high').length,warning:[...highestByVersion.values()].filter(value=>value==='warning').length,clear:[...highestByVersion.values()].filter(value=>value==='clear').length};
  }
  get pendingAssessmentCount(): number { return this.vulnerabilityAssessments().reduce((sum,{assessment})=>sum+assessment.assessments.filter(item=>item.status==='NOT_ASSESSED'||item.status==='INCOMPLETE'||item.status==='UNAVAILABLE'||item.status==='STALE').length,0); }
  get hasAssessedVersions(): boolean { return this.vulnerabilityAssessments().some(({assessment})=>assessment.assessedVersions>0); }
  private loadAnalyses(repositories: DashboardRepository[]): void {
    if (!repositories.length) { this.analyses.set([]); this.vulnerabilityAssessments.set([]); this.unavailableVulnerabilityRepositories.set(0); this.loading.set(false); return; }
    forkJoin(repositories.map(repository => {
      const base=`/api/organizations/${repository.organizationId}/repositories/${repository.id}`;
      return forkJoin({
        maven:this.api.get<DashboardAnalysis|null>(`${base}/maven-analysis`).pipe(catchError(()=>of(null))),
        npm:this.api.get<DashboardAnalysis|null>(`${base}/npm-analysis`).pipe(catchError(()=>of(null))),
        vulnerabilities:this.api.get<DashboardVulnerabilityAssessment>(`${base}/vulnerability-analysis`).pipe(catchError(()=>of(null))),
      });
    }))
      .subscribe(results => {
        this.analyses.set(results.flatMap((result, index) => [result.maven,result.npm].flatMap(scan => scan ? [{...scan, repositoryName: repositories[index].fullName, organizationName: repositories[index].organizationName}] : [])).sort((a,b)=>Date.parse(b.analyzedAt)-Date.parse(a.analyzedAt)));
        this.vulnerabilityAssessments.set(results.flatMap((result,index)=>result.vulnerabilities?[{repository:repositories[index],assessment:result.vulnerabilities}]:[]));
        this.unavailableVulnerabilityRepositories.set(results.filter(result=>result.vulnerabilities===null).length);
        this.loading.set(false);
      });
  }
  private riskCategory(severity:string):RiskCategory { const value=severity.toUpperCase();return value==='CRITICAL'?'critical':value==='HIGH'?'high':value==='MEDIUM'||value==='LOW'||value==='UNKNOWN'?'warning':'warning'; }
  private riskRank(category:RiskCategory):number { return category==='critical'?4:category==='high'?3:category==='warning'?2:1; }
  get firstName(): string {
    return this.api.account()?.displayName.trim().split(/\s+/)[0] ?? '';
  }
}
