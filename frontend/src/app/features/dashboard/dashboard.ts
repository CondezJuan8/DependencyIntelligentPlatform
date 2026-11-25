import { Component, computed, inject, OnInit, output, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { Organization, SessionApiService, accessError } from '../../core/session-api.service';
import { forkJoin, from, of, toArray } from 'rxjs';
import { catchError, map, mergeMap } from 'rxjs/operators';
import { DiIcon } from '../../shared/di-icon';
interface DashboardRepository {id:string;organizationId:string;organizationName:string;fullName:string;defaultBranch:string;source:string;}
interface DashboardAnalysis {scanId:string;source:string;sourceSha256:string;commitSha:string|null;analyzedAt:string;manifestCount:number;unresolvedCount:number;coverageStatus:'COMPLETE'|'PARTIAL';dependencies:{coordinates:{ecosystem:string;namespace:string|null;name:string;version:string}}[];}
interface RecentAnalysis extends DashboardAnalysis {repositoryId:string;repositoryName:string;organizationName:string;ecosystem:'MAVEN'|'NPM';}
type AssessmentStatus = 'NOT_ASSESSED' | 'MATCHED' | 'NO_KNOWN_ADVISORIES' | 'INCOMPLETE' | 'UNAVAILABLE' | 'STALE';
interface DashboardAdvisory {id:string;summary:string|null;severity:string;url:string;}
interface DashboardVersionAssessment {packageVersion:{ecosystem:string;namespace:string|null;name:string;version:string};status:AssessmentStatus;checkedAt:string|null;advisories:DashboardAdvisory[];}
interface DashboardVulnerabilityAssessment {generatedAt:string;assessedVersions:number;affectedVersions:number;advisoryCount:number;unavailableVersions:number;staleVersions:number;assessments:DashboardVersionAssessment[];}
type RiskCategory = 'critical'|'high'|'warning'|'clear';
interface DashboardFinding {repositoryId:string;repositoryName:string;ecosystem:string;packageName:string;version:string;advisory:DashboardAdvisory;category:RiskCategory;}
@Component({
  selector: 'app-dashboard',
  imports: [MatButtonModule, DiIcon, DatePipe],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard implements OnInit {
  readonly api = inject(SessionApiService);
  readonly manage = output<void>();
  readonly connectRepository = output<void>();
  readonly openInventory = output<string>();
  readonly organizations = signal<Organization[]>([]);
  readonly repositories = signal<DashboardRepository[]>([]);
  readonly analyses = signal<RecentAnalysis[]>([]);
  readonly vulnerabilityAssessments = signal<{repository:DashboardRepository;assessment:DashboardVulnerabilityAssessment}[]>([]);
  readonly unavailableVulnerabilityRepositories = signal(0);
  readonly loading = signal(true);
  readonly loadingOrganizations = signal(false);
  readonly organizationsUnavailable = signal(false);
  readonly error = signal('');
  readonly isMember = computed(() => this.api.account()?.accountType === 'MEMBER');
  private loadSequence = 0;
  ngOnInit(): void {
    this.load();
  }
  load(): void {
    const sequence = ++this.loadSequence;
    this.loading.set(true);
    this.error.set('');
    this.organizations.set([]);
    this.analyses.set([]);
    this.vulnerabilityAssessments.set([]);
    this.unavailableVulnerabilityRepositories.set(0);
    if (!this.isMember()) {
      this.loadingOrganizations.set(true);
      this.organizationsUnavailable.set(false);
      this.api.get<Organization[]>('/api/organizations').subscribe({
        next: data => { if (sequence === this.loadSequence) this.organizations.set(data); },
        error: () => { if (sequence === this.loadSequence) { this.organizationsUnavailable.set(true); this.loadingOrganizations.set(false); } },
        complete: () => { if (sequence === this.loadSequence) this.loadingOrganizations.set(false); },
      });
    } else {
      this.loadingOrganizations.set(false);
    }
    this.api.get<DashboardRepository[]>('/api/account/workspace')
      .subscribe({ next: data => { if (sequence !== this.loadSequence) return; this.repositories.set(data); this.loadAnalyses(data, sequence); }, error: error => { if (sequence !== this.loadSequence) return; this.repositories.set([]); this.error.set(accessError(error)); this.loading.set(false); } });
  }
  get dependencyCount(): number | null {
    if (!this.analyses().length) return null;
    return new Set(this.analyses().flatMap(scan => scan.dependencies
      .filter(item => !!item.coordinates.version?.trim())
      .map(item => JSON.stringify([scan.repositoryId, item.coordinates.ecosystem, item.coordinates.namespace, item.coordinates.name, item.coordinates.version])))).size;
  }
  get analyzedRepositoryCount(): number { return new Set(this.analyses().map(scan => scan.repositoryId)).size; }
  get completeAnalysisCount(): number { return this.analyses().filter(scan => scan.coverageStatus === 'COMPLETE').length; }
  get partialAnalysisCount(): number { return this.analyses().filter(scan => scan.coverageStatus === 'PARTIAL').length; }
  analysisVersionCount(analysis: RecentAnalysis): number {
    return new Set(analysis.dependencies.filter(item => !!item.coordinates.version?.trim())
      .map(item => JSON.stringify([item.coordinates.ecosystem,item.coordinates.namespace,item.coordinates.name,item.coordinates.version]))).size;
  }
  get findings(): DashboardFinding[] {
    const findings = new Map<string, DashboardFinding>();
    for (const {repository, assessment} of this.vulnerabilityAssessments()) for (const version of assessment.assessments) {
      for (const advisory of version.advisories) {
        const {ecosystem,namespace,name,version:packageVersion} = version.packageVersion;
        const key = JSON.stringify([repository.id,ecosystem,namespace,name,packageVersion,advisory.id]);
        findings.set(key, {
          repositoryId: repository.id,
          repositoryName: repository.fullName,
          ecosystem,
          packageName: ecosystem === 'MAVEN' && namespace ? `${namespace}:${name}` : namespace ? `${namespace}/${name}` : name,
          version: packageVersion,
          advisory,
          category: this.riskCategory(advisory.severity),
        });
      }
    }
    return [...findings.values()].sort((a,b)=>this.riskRank(b.category)-this.riskRank(a.category));
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
  private loadAnalyses(repositories: DashboardRepository[], sequence: number): void {
    if (!repositories.length) { this.analyses.set([]); this.vulnerabilityAssessments.set([]); this.unavailableVulnerabilityRepositories.set(0); this.loading.set(false); return; }
    from(repositories).pipe(mergeMap(repository => {
      const base=`/api/organizations/${repository.organizationId}/repositories/${repository.id}`;
      return forkJoin({
        maven:this.api.get<DashboardAnalysis|null>(`${base}/maven-analysis`).pipe(catchError(()=>of(null))),
        npm:this.api.get<DashboardAnalysis|null>(`${base}/npm-analysis`).pipe(catchError(()=>of(null))),
        vulnerabilities:this.api.get<DashboardVulnerabilityAssessment>(`${base}/vulnerability-analysis`).pipe(catchError(()=>of(null))),
      }).pipe(map(result => ({repository, ...result})));
    }, 4), toArray())
      .subscribe(results => {
        if (sequence !== this.loadSequence) return;
        this.analyses.set(results.flatMap(result => ([['MAVEN',result.maven],['NPM',result.npm]] as const).flatMap(([ecosystem,scan]) => scan ? [{...scan,ecosystem,repositoryId:result.repository.id,repositoryName:result.repository.fullName,organizationName:result.repository.organizationName}] : [])).sort((a,b)=>Date.parse(b.analyzedAt)-Date.parse(a.analyzedAt)));
        this.vulnerabilityAssessments.set(results.flatMap(result=>result.vulnerabilities?[{repository:result.repository,assessment:result.vulnerabilities}]:[]));
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
