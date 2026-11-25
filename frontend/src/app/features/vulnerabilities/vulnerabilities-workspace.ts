import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { catchError, finalize, forkJoin, from, map, mergeMap, of, toArray } from 'rxjs';
import { SessionApiService, accessError } from '../../core/session-api.service';
import { TranslatePipe } from '../../core/translate.pipe';
import { DiIcon } from '../../shared/di-icon';
import { fixesForPackage } from '../inventory/inventory-evidence';

type Risk = 'CRITICAL' | 'HIGH' | 'WARNING';
type ReviewStatus = 'OPEN' | 'IN_REVIEW' | 'RISK_ACCEPTED' | 'RESOLVED';
interface Repository { id: string; organizationId: string; organizationName: string; fullName: string; source: string; }
interface Advisory { id: string; summary: string | null; details: string | null; severity: string; cvssVector: string | null; publishedAt: string | null; modifiedAt: string | null; url: string; aliases: string[]; references: string[]; affectedJson: string; }
interface Assessment { scanId: string; scanComponentId: string; packageVersion: { ecosystem: string; namespace: string | null; name: string; version: string }; status: string; checkedAt: string | null; advisories: Advisory[]; }
interface AssessmentResponse { generatedAt: string; assessments: Assessment[]; }
interface Review { ecosystem: string; namespace: string; packageName: string; packageVersion: string; advisoryId: string; status: ReviewStatus; reviewedBy: string; updatedAt: string; }
interface FindingOccurrence { key: string; repository: Repository; ecosystem: string; namespace: string | null; packageName: string; displayPackage: string; version: string; advisory: Advisory; risk: Risk; reviewStatus: ReviewStatus; }
interface FindingGroup { id: string; advisory: Advisory; risk: Risk; occurrences: FindingOccurrence[]; }

@Component({
  selector: 'app-vulnerabilities-workspace',
  imports: [DatePipe, TranslatePipe, DiIcon],
  templateUrl: './vulnerabilities-workspace.html',
  styleUrl: './vulnerabilities-workspace.scss',
})
export class VulnerabilitiesWorkspace implements OnInit {
  private readonly api = inject(SessionApiService);
  readonly repositories = signal<Repository[]>([]);
  readonly findings = signal<FindingOccurrence[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly unavailableRepositories = signal(0);
  readonly unresolvedAssessments = signal(0);
  readonly notice = signal('');
  readonly search = signal('');
  readonly severityFilter = signal('ALL');
  readonly ecosystemFilter = signal('ALL');
  readonly repositoryFilter = signal('ALL');
  readonly reviewFilter = signal('ALL');
  readonly saving = signal(new Set<string>());
  readonly expanded = signal(new Set<string>());

  readonly filteredOccurrences = computed(() => {
    const query = this.search().trim().toLocaleLowerCase();
    const severity = this.severityFilter();
    const ecosystem = this.ecosystemFilter();
    const repositoryId = this.repositoryFilter();
    const reviewStatus = this.reviewFilter();
    return this.findings().filter(item => {
      const haystack = `${item.advisory.id} ${item.advisory.summary ?? ''} ${item.advisory.details ?? ''} ${item.packageName} ${item.version} ${item.repository.fullName}`.toLocaleLowerCase();
      return (!query || haystack.includes(query))
        && (severity === 'ALL' || item.risk === severity)
        && (ecosystem === 'ALL' || item.ecosystem === ecosystem)
        && (repositoryId === 'ALL' || item.repository.id === repositoryId)
        && (reviewStatus === 'ALL' || item.reviewStatus === reviewStatus);
    });
  });
  readonly visibleFindings = computed(() => this.groupFindings(this.filteredOccurrences()));
  readonly affectedCount = computed(() => new Set(this.filteredOccurrences().map(item => this.packageIdentity(item))).size);
  readonly packageCount = computed(() => new Set(this.filteredOccurrences().map(item => this.packageIdentity(item))).size);
  readonly severityCounts = computed(() => ({
    CRITICAL: this.filteredOccurrences().filter(item => item.risk === 'CRITICAL').length,
    HIGH: this.filteredOccurrences().filter(item => item.risk === 'HIGH').length,
    WARNING: this.filteredOccurrences().filter(item => item.risk === 'WARNING').length,
  }));

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true); this.error.set(''); this.notice.set(''); this.findings.set([]);
    this.api.get<Repository[]>('/api/account/workspace').subscribe({
      next: repositories => {
        this.repositories.set(repositories);
        if (!repositories.length) { this.loading.set(false); return; }
        from(repositories).pipe(mergeMap(repository => {
          const base = `/api/organizations/${repository.organizationId}/repositories/${repository.id}/vulnerability-analysis`;
          return forkJoin({
            assessment: this.api.get<AssessmentResponse>(base).pipe(catchError(() => of(null))),
            reviews: this.api.get<{ reviews: Review[] }>(`${base}/reviews`).pipe(catchError(() => of({ reviews: [] }))),
          }).pipe(map(result => ({ repository, ...result })));
        }, 4), toArray()).subscribe(results => {
          const reviewsByKey = new Map<string, Review>();
          for (const result of results) for (const review of result.reviews.reviews) {
            reviewsByKey.set(this.identity(result.repository.id, review.ecosystem, review.namespace, review.packageName, review.packageVersion, review.advisoryId), review);
          }
          this.unavailableRepositories.set(results.filter(result => result.assessment === null).length);
          this.unresolvedAssessments.set(results.reduce((count, result) => count + (result.assessment?.assessments ?? []).filter(item => !['MATCHED', 'NO_KNOWN_ADVISORIES'].includes(item.status)).length, 0));
          const rows: FindingOccurrence[] = [];
          for (const { repository, assessment } of results) for (const item of assessment?.assessments ?? []) {
            for (const advisory of item.advisories) {
              const pkg = item.packageVersion;
              const ecosystem = pkg.ecosystem.toUpperCase();
              const displayPackage = ecosystem === 'MAVEN' && pkg.namespace ? `${pkg.namespace}:${pkg.name}` : pkg.namespace ? `${pkg.namespace}/${pkg.name}` : pkg.name;
              const key = this.identity(repository.id, ecosystem, pkg.namespace, pkg.name, pkg.version, advisory.id);
              rows.push({ key, repository, ecosystem, namespace: pkg.namespace, packageName: pkg.name,
                displayPackage, version: pkg.version, advisory, risk: this.risk(advisory.severity),
                reviewStatus: reviewsByKey.get(key)?.status ?? 'OPEN' });
            }
          }
          this.findings.set(rows.sort((a, b) => this.rank(b.risk) - this.rank(a.risk) || a.advisory.id.localeCompare(b.advisory.id)));
          this.loading.set(false);
        });
      },
      error: error => { this.error.set(accessError(error)); this.loading.set(false); },
    });
  }

  groupFindings(rows: FindingOccurrence[]): FindingGroup[] {
    const groups = new Map<string, FindingGroup>();
    for (const item of rows) {
      let group = groups.get(item.advisory.id);
      if (!group) { group = { id: item.advisory.id, advisory: item.advisory, risk: item.risk, occurrences: [] }; groups.set(item.advisory.id, group); }
      group.occurrences.push(item);
      if (this.rank(item.risk) > this.rank(group.risk)) group.risk = item.risk;
    }
    return [...groups.values()].sort((a, b) => this.rank(b.risk) - this.rank(a.risk) || b.occurrences.length - a.occurrences.length || a.id.localeCompare(b.id));
  }

  fixedVersions(item: FindingOccurrence): string[] {
    const packageName = item.ecosystem === 'MAVEN' && item.namespace ? `${item.namespace}:${item.packageName}` : item.packageName;
    return fixesForPackage(item.advisory.affectedJson, item.ecosystem, packageName);
  }

  plainDescription(details: string): string {
    return details
      .replace(/<details\b[^>]*>[\s\S]*?<\/details\s*>/gi, '')
      .replace(/<\/?(?:summary|h[1-6]|p|div|br|ul|ol|li|pre|code|blockquote)\b[^>]*>/gi, '\n')
      .replace(/\r\n?/g, '\n')
      .replace(/^[ \t]{0,3}#{1,6}[ \t]+/gm, '')
      .replace(/^[ \t]{0,3}(?:[-*+]|\d+\.)[ \t]+/gm, '')
      .replace(/^[ \t]{0,3}>[ \t]?/gm, '')
      .replace(/!?\[([^\]]+)\]\([^)]+\)/g, '$1')
      .replace(/`{1,3}|\*\*|__/g, '')
      .replace(/&(?:amp|lt|gt|quot|apos|nbsp);/gi, entity => ({
        '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&apos;': "'", '&nbsp;': ' ',
      })[entity.toLowerCase()] ?? entity)
      .replace(/[ \t]+\n/g, '\n')
      .replace(/\n{3,}/g, '\n\n')
      .trim();
  }

  setReview(item: FindingOccurrence, status: ReviewStatus): void {
    const key = item.key;
    const nextSaving = new Set(this.saving()); nextSaving.add(key); this.saving.set(nextSaving); this.notice.set('');
    const repository = item.repository;
    const base = `/api/organizations/${repository.organizationId}/repositories/${repository.id}/vulnerability-analysis/reviews`;
    this.api.put<Review>(base, { ecosystem: item.ecosystem, namespace: item.namespace, packageName: item.packageName,
      packageVersion: item.version, advisoryId: item.advisory.id, status }).pipe(finalize(() => {
        const pending = new Set(this.saving()); pending.delete(key); this.saving.set(pending);
      })).subscribe({
      next: review => {
        this.findings.update(rows => rows.map(row => row.key === key ? { ...row, reviewStatus: review.status } : row));
        this.notice.set('Se guardó el estado de revisión.');
      },
      error: error => { this.error.set(accessError(error)); },
    });
  }

  toggle(id: string): void { const next = new Set(this.expanded()); next.has(id) ? next.delete(id) : next.add(id); this.expanded.set(next); }
  isExpanded(id: string): boolean { return this.expanded().has(id); }
  hasAnyFilter(): boolean { return this.search() !== '' || this.severityFilter() !== 'ALL' || this.ecosystemFilter() !== 'ALL' || this.repositoryFilter() !== 'ALL' || this.reviewFilter() !== 'ALL'; }
  clearFilters(): void { this.search.set(''); this.severityFilter.set('ALL'); this.ecosystemFilter.set('ALL'); this.repositoryFilter.set('ALL'); this.reviewFilter.set('ALL'); }
  reviewLabel(status: ReviewStatus): string { return ({ OPEN: 'Pendiente', IN_REVIEW: 'En revisión', RISK_ACCEPTED: 'Riesgo aceptado', RESOLVED: 'Resuelto' })[status]; }
  private identity(repoId: string, ecosystem: string, namespace: string | null, name: string, version: string, advisory: string): string { return JSON.stringify([repoId, ecosystem, namespace ?? '', name, version, advisory]); }
  private packageIdentity(item: FindingOccurrence): string { return JSON.stringify([item.repository.id, item.ecosystem, item.namespace ?? '', item.packageName, item.version]); }
  private risk(value: string): Risk { const severity = value.toUpperCase(); return severity === 'CRITICAL' ? 'CRITICAL' : severity === 'HIGH' ? 'HIGH' : 'WARNING'; }
  private rank(value: Risk): number { return value === 'CRITICAL' ? 3 : value === 'HIGH' ? 2 : 1; }
}
