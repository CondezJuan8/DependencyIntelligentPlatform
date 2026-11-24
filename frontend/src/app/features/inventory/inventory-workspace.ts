import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';
import { catchError, finalize, forkJoin, map, of } from 'rxjs';
import { SessionApiService, accessError } from '../../core/session-api.service';
import { TranslatePipe } from '../../core/translate.pipe';
import { evidenceKey, fixesForPackage } from './inventory-evidence';
import { InventorySpaceCatalog, type InventoryLocation } from './inventory-space-catalog';

type Ecosystem = 'MAVEN' | 'NPM';
type DependencyKind = 'DIRECT' | 'TRANSITIVE' | 'UNKNOWN' | string;

interface Repository {
  id: string;
  organizationId: string;
  organizationName: string;
  fullName: string;
  source: string;
}

interface Dependency {
  manifestPath: string;
  coordinates: { namespace: string | null; name: string; version: string };
  scope: string;
  dependencyType: DependencyKind;
}

interface Scan {
  scanId: string;
  source: string;
  sourceSha256: string;
  commitSha: string | null;
  analyzedAt: string;
  manifestCount: number;
  unresolvedCount: number;
  coverageStatus: 'COMPLETE' | 'PARTIAL';
  coverageMessage: string | null;
  dependencies: Dependency[];
}

interface ScanResult {
  ecosystem: Ecosystem;
  scan: Scan | null;
  status: 'complete' | 'partial' | 'not-analyzed' | 'no-manifest' | 'error';
  message: string;
}

interface AnalysisResponse {
  ecosystem: Ecosystem;
  scan: Scan | null;
  status: 'COMPLETED' | 'NO_MANIFEST' | 'FAILED';
  message: string | null;
}

interface Occurrence {
  ecosystem: Ecosystem;
  scanId: string;
  source: string;
  sourceSha256: string;
  commitSha: string | null;
  analyzedAt: string;
  manifestCount: number;
  unresolvedCount: number;
  dependency: Dependency;
}

interface VersionGroup {
  version: string;
  directCount: number;
  transitiveCount: number;
  unknownCount: number;
  occurrences: Occurrence[];
}

interface PackageGroup {
  key: string;
  ecosystem: Ecosystem;
  namespace: string | null;
  name: string;
  versions: VersionGroup[];
  directCount: number;
  transitiveCount: number;
  unknownCount: number;
}

type AssessmentStatus = 'NOT_ASSESSED' | 'MATCHED' | 'NO_KNOWN_ADVISORIES' | 'INCOMPLETE' | 'UNAVAILABLE' | 'STALE';
interface Advisory {
  id: string; summary: string | null; details: string | null; severity: string; cvssVector: string | null;
  publishedAt: string | null; modifiedAt: string | null; url: string; aliases: string[]; references: string[]; affectedJson: string;
}
interface VersionAssessment {
  scanId: string;
  scanComponentId: string;
  packageVersion: { ecosystem: string; namespace: string | null; name: string; version: string };
  status: AssessmentStatus; lastSuccessfulStatus: AssessmentStatus | null; checkedAt: string | null;
  lastSuccessfulAt: string | null; sourceModifiedAt: string | null; failureCode: string | null; advisories: Advisory[];
}
interface InventoryAssessment {
  generatedAt: string; assessedVersions: number; affectedVersions: number; advisoryCount: number;
  unavailableVersions: number; staleVersions: number; assessments: VersionAssessment[];
}
interface RepositoryCodeEvidence {
  scanId: string; scanComponentId: string; advisoryId: string;
  status: 'VERSION_AFFECTED'|'USAGE_DETECTED'|'REACHABLE'|'NO_PATH_FOUND'|'INCONCLUSIVE';
  states: string[]; versionAffected: boolean; osvStatus: string; sourceProvider: string;
  expectedSourceSha256: string; sourceSha256: string|null; sourceCommit: string|null;
  ecosystem: string; namespace: string|null; packageName: string; packageVersion: string;
  manifestPath: string|null; explanation: string; analysisComplete: boolean;
  matches: {filePath:string;lineNumber:number;endLine:number|null;symbol:string;evidenceType:string;excerpt:string;reason:string}[];
}

@Component({
  selector: 'app-inventory-workspace',
  imports: [DatePipe, TranslatePipe, InventorySpaceCatalog],
  templateUrl: './inventory-workspace.html',
  styleUrls: ['./inventory-workspace.scss', './inventory-catalog.scss', './inventory-advisories.scss'],
})
export class InventoryWorkspace implements OnInit {
  private readonly api = inject(SessionApiService);
  private readonly catalog = viewChild(InventorySpaceCatalog);
  private scanLoadSequence = 0;
  private catalogDirty = false;

  readonly mode = signal<'catalog' | 'repository'>('catalog');
  readonly focusedPackage = signal('');
  readonly packagePage = signal(0);
  readonly packagePageSize = 25;
  readonly repositories = signal<Repository[]>([]);
  readonly repository = signal<Repository | null>(null);
  readonly occurrences = signal<Occurrence[]>([]);
  readonly scanResults = signal<ScanResult[]>([]);
  private readonly osvEvidence = signal<VersionAssessment[]>([]);
  private readonly assessmentsByKey = computed(() => {
    const active = new Set(this.scanResults().flatMap(result => result.scan ? [result.scan.scanId] : []));
    return new Map(this.osvEvidence().filter(item => active.has(item.scanId))
      .map(item => [evidenceKey(item.scanId, item.packageVersion), item]));
  });
  readonly vulnerabilityAssessment = computed(() => {
    const assessments = [...this.assessmentsByKey().values()];
    if (!assessments.length) return null;
    return {
      affectedVersions: assessments.filter(item => item.advisories.length > 0).length,
      advisoryCount: new Set(assessments.flatMap(item => item.advisories.map(advisory => advisory.id))).size,
      unavailableVersions: assessments.filter(item => !['MATCHED', 'NO_KNOWN_ADVISORIES'].includes(item.status)).length,
      staleVersions: assessments.filter(item => item.status === 'STALE').length,
    };
  });
  readonly assessmentError = signal('');
  readonly assessingVulnerabilities = signal(false);
  readonly codeEvidence = signal<Record<string, RepositoryCodeEvidence>>({});
  readonly codeEvidenceLoading = signal(new Set<string>());
  readonly codeEvidenceErrors = signal<Record<string, string>>({});
  readonly search = signal('');
  readonly ecosystemFilter = signal<'ALL' | Ecosystem>('ALL');
  readonly dependencyFilter = signal<'ALL' | 'DIRECT' | 'TRANSITIVE' | 'UNKNOWN'>('ALL');
  readonly securityFilter = signal<'ALL' | 'VULNERABLE' | 'NO_KNOWN' | 'REVIEW'>('ALL');
  readonly scopeFilter = signal('ALL');
  readonly versionFilter = signal('');
  readonly severityFilter = signal('');
  readonly loading = signal(true);
  readonly loadingScans = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');

  private readonly matchingGroups = computed(() => {
    const filtered = this.occurrences().filter((occurrence) => {
      const dependency = occurrence.dependency;
      const matchesEcosystem = this.ecosystemFilter() === 'ALL' || occurrence.ecosystem === this.ecosystemFilter();
      const matchesKind = this.dependencyFilter() === 'ALL' || dependency.dependencyType === this.dependencyFilter();
      const matchesScope = this.scopeFilter() === 'ALL' || dependency.scope === this.scopeFilter();
      const query = this.search().trim().toLocaleLowerCase();
      const identity = `${dependency.coordinates.namespace ?? ''}:${dependency.coordinates.name} ${dependency.coordinates.version} ${dependency.manifestPath} ${dependency.scope}`.toLocaleLowerCase();
      const version = this.versionFilter().trim();
      return matchesEcosystem && matchesKind && matchesScope && (!version || dependency.coordinates.version === version)
        && (!query || identity.includes(query));
    });

    const packages = new Map<string, PackageGroup>();
    for (const occurrence of filtered) {
      const { namespace, name, version } = occurrence.dependency.coordinates;
      const key = JSON.stringify([occurrence.ecosystem, namespace, name]);
      let group = packages.get(key);
      if (!group) {
        group = { key, ecosystem: occurrence.ecosystem, namespace, name, versions: [], directCount: 0, transitiveCount: 0, unknownCount: 0 };
        packages.set(key, group);
      }
      let versionGroup = group.versions.find((candidate) => candidate.version === version);
      if (!versionGroup) {
        versionGroup = { version, directCount: 0, transitiveCount: 0, unknownCount: 0, occurrences: [] };
        group.versions.push(versionGroup);
      }
      versionGroup.occurrences.push(occurrence);
      if (occurrence.dependency.dependencyType === 'DIRECT') {
        versionGroup.directCount++;
        group.directCount++;
      } else if (occurrence.dependency.dependencyType === 'TRANSITIVE') {
        versionGroup.transitiveCount++;
        group.transitiveCount++;
      } else {
        versionGroup.unknownCount++;
        group.unknownCount++;
      }
    }

    return [...packages.values()]
      .map((group) => {
        const versions = group.versions.filter(version => this.matchesSecurityFilter(group, version));
        return {
          ...group,
          versions: versions.sort((left, right) => left.version.localeCompare(right.version, undefined, { numeric: true })),
          directCount: versions.reduce((sum, version) => sum + version.directCount, 0),
          transitiveCount: versions.reduce((sum, version) => sum + version.transitiveCount, 0),
          unknownCount: versions.reduce((sum, version) => sum + version.unknownCount, 0),
        };
      })
      .filter(group => group.versions.length > 0)
      .sort((left, right) => left.name.localeCompare(right.name));
  });

  get groups(): PackageGroup[] {
    return this.matchingGroups();
  }

  get visibleGroups(): PackageGroup[] {
    const start = this.packagePage() * this.packagePageSize;
    return this.groups.slice(start, start + this.packagePageSize);
  }

  get uniquePackageCount(): number {
    return new Set(this.occurrences().map((occurrence) => JSON.stringify([occurrence.ecosystem, occurrence.dependency.coordinates.namespace, occurrence.dependency.coordinates.name]))).size;
  }

  get versionCount(): number {
    return new Set(this.occurrences().map((occurrence) => JSON.stringify([occurrence.ecosystem, occurrence.dependency.coordinates.namespace, occurrence.dependency.coordinates.name, occurrence.dependency.coordinates.version]))).size;
  }

  get directCount(): number {
    return this.occurrences().filter((occurrence) => occurrence.dependency.dependencyType === 'DIRECT').length;
  }

  get transitiveCount(): number {
    return this.occurrences().filter((occurrence) => occurrence.dependency.dependencyType === 'TRANSITIVE').length;
  }

  get unknownCount(): number {
    return this.occurrences().filter((occurrence) => occurrence.dependency.dependencyType === 'UNKNOWN').length;
  }

  get scopes(): string[] {
    return [...new Set(this.occurrences().map((occurrence) => occurrence.dependency.scope).filter(Boolean))].sort();
  }

  get visibleVersionCount(): number {
    return this.groups.reduce((sum, group) => sum + group.versions.length, 0);
  }

  get hasActiveFilters(): boolean {
    return !!this.search().trim() || this.ecosystemFilter() !== 'ALL' || this.dependencyFilter() !== 'ALL'
      || this.scopeFilter() !== 'ALL' || this.securityFilter() !== 'ALL' || !!this.versionFilter() || !!this.severityFilter();
  }

  setQuickSecurityFilter(filter: 'ALL' | 'VULNERABLE' | 'NO_KNOWN' | 'REVIEW'): void {
    this.securityFilter.set(filter);
    this.packagePage.set(0);
  }

  get hasCompletedScan(): boolean {
    return this.scanResults().some((result) => result.status === 'complete' || result.status === 'partial');
  }

  get hasNoCompatibleManifest(): boolean {
    return this.scanResults().length > 0 && this.scanResults().every(result => result.status === 'no-manifest');
  }

  ngOnInit(): void {
    this.api.get<Repository[]>('/api/account/workspace').pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (items) => {
        this.repositories.set(items);
        const requestedRepositoryId = new URLSearchParams(window.location.search).get('repositoryId');
        const requestedRepository = requestedRepositoryId ? items.find(item => item.id === requestedRepositoryId) : undefined;
        if (requestedRepository) {
          this.mode.set('repository');
          this.setRepository(requestedRepository);
          const url = new URL(window.location.href);
          url.searchParams.delete('repositoryId');
          window.history.replaceState(null, '', url.pathname + url.search);
        } else if (this.mode() === 'repository') this.setRepository(items[0] ?? null);
      },
      error: (failure) => this.error.set(accessError(failure)),
    });
  }

  showMode(mode: 'catalog' | 'repository'): void {
    this.mode.set(mode);
    if (mode === 'repository' && !this.repository()) this.setRepository(this.repositories()[0] ?? null);
    if (mode === 'catalog' && this.catalogDirty) {
      this.catalog()?.load();
      this.catalogDirty = false;
    }
  }

  openRepositoryLocation(location: InventoryLocation): void {
    const repository = this.repositories().find(item => item.id === location.repositoryId);
    if (!repository) return;
    this.mode.set('repository');
    this.setRepository(repository);
    this.ecosystemFilter.set(location.ecosystem);
    this.search.set(location.packageName);
    this.versionFilter.set(location.version);
    this.focusedPackage.set(location.packageName);
  }

  packageName(group: PackageGroup): string {
    return group.ecosystem === 'MAVEN' && group.namespace ? `${group.namespace}:${group.name}` : group.name;
  }

  setRepository(repository: Repository | null): void {
    const requestSequence = ++this.scanLoadSequence;
    this.repository.set(repository);
    this.occurrences.set([]);
    this.scanResults.set([]);
    this.osvEvidence.set([]);
    this.codeEvidence.set({}); this.codeEvidenceErrors.set({}); this.codeEvidenceLoading.set(new Set());
    this.assessmentError.set('');
    this.clearFilters();
    this.error.set('');
    this.notice.set('');
    this.loadingScans.set(!!repository);
    if (repository) this.loadScans(repository, requestSequence);
  }

  selectRepository(event: Event): void {
    const id = (event.target as HTMLSelectElement).value;
    this.setRepository(this.repositories().find((item) => item.id === id) ?? null);
  }

  updateSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
    this.packagePage.set(0);
  }

  updateEcosystem(event: Event): void {
    this.ecosystemFilter.set((event.target as HTMLSelectElement).value as 'ALL' | Ecosystem);
    this.packagePage.set(0);
  }

  updateDependencyKind(event: Event): void {
    this.dependencyFilter.set((event.target as HTMLSelectElement).value as 'ALL' | 'DIRECT' | 'TRANSITIVE' | 'UNKNOWN');
    this.packagePage.set(0);
  }

  updateScope(event: Event): void {
    this.scopeFilter.set((event.target as HTMLSelectElement).value);
    this.packagePage.set(0);
  }

  updateSecurityFilter(event: Event): void {
    this.setQuickSecurityFilter((event.target as HTMLSelectElement).value as 'ALL' | 'VULNERABLE' | 'NO_KNOWN' | 'REVIEW');
  }

  updateVersion(event: Event): void {
    this.versionFilter.set((event.target as HTMLInputElement).value);
    this.packagePage.set(0);
  }

  updateSeverity(event: Event): void {
    this.severityFilter.set((event.target as HTMLSelectElement).value);
    this.packagePage.set(0);
  }

  private matchesSecurityFilter(group: PackageGroup, version: VersionGroup): boolean {
    const filter = this.securityFilter();
    const assessment = this.assessmentFor(group, version);
    if (this.severityFilter() && !assessment?.advisories.some(advisory => advisory.severity === this.severityFilter())) return false;
    if (filter === 'ALL') return true;
    const status = assessment?.status;
    if (filter === 'VULNERABLE') return !!assessment?.advisories.length;
    if (filter === 'NO_KNOWN') return status === 'NO_KNOWN_ADVISORIES';
    return status !== 'MATCHED' && status !== 'NO_KNOWN_ADVISORIES';
  }

  clearFilters(): void {
    this.packagePage.set(0);
    this.focusedPackage.set('');
    this.search.set('');
    this.ecosystemFilter.set('ALL');
    this.dependencyFilter.set('ALL');
    this.scopeFilter.set('ALL');
    this.securityFilter.set('ALL');
    this.versionFilter.set('');
    this.severityFilter.set('');
  }

  analyzeRepository(): void {
    const repository = this.repository();
    if (!repository || this.busy() || this.loadingScans() || this.assessingVulnerabilities()) return;
    this.busy.set(true);
    this.packagePage.set(0);
    this.error.set('');
    this.notice.set('');

    this.assessmentError.set('');

    this.api.post<AnalysisResponse[]>(this.repositoryEndpoint(repository, 'dependency-analysis'), {})
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (response) => {
          const previous = new Map(this.scanResults().map(result => [result.ecosystem, result.scan]));
          const results = response.map((result): ScanResult => {
            if (result.status === 'FAILED') {
              return { ecosystem: result.ecosystem, scan: previous.get(result.ecosystem) ?? null,
                status: 'error', message: result.message ?? 'No se pudo completar el análisis' };
            }
            return this.scanResult(result.ecosystem, result.scan, result.status === 'NO_MANIFEST');
          });
          this.scanResults.set(results);
          this.occurrences.set(this.toOccurrences(results));
          const visible = new Set(results.flatMap(result => result.scan ? [result.scan.scanId] : []));
          this.osvEvidence.update(items => items.filter(item => visible.has(item.scanId)));
          const completed = response.filter(result => result.status === 'COMPLETED');
          this.catalogDirty ||= response.some(result => result.status !== 'FAILED');
          if (completed.length > 0)
            this.notice.set('Análisis actualizado. Consulta el estado de cada ecosistema para ver su alcance.');
          const newScans = completed.flatMap(result => result.scan?.dependencies.length ? [result.scan.scanId] : []);
          if (newScans.length) this.assessVulnerabilities(repository, newScans);
        },
        error: failure => this.error.set(accessError(failure)),
      });
  }

  private loadScans(repository: Repository, requestSequence: number): void {
    forkJoin([
      this.loadScanner(repository, 'MAVEN'),
      this.loadScanner(repository, 'NPM'),
      this.loadVulnerabilityAssessment(repository, requestSequence),
    ]).pipe(finalize(() => {
      if (requestSequence === this.scanLoadSequence) this.loadingScans.set(false);
    })).subscribe(([maven, npm, assessment]) => {
      if (requestSequence !== this.scanLoadSequence) return;
      const results = [maven, npm];
      const found = results.filter((result) => result.scan !== null);
      this.scanResults.set(results);
      this.occurrences.set(this.toOccurrences(found));
      this.osvEvidence.set(assessment?.assessments ?? []);
      const scanError = results.find((result) => result.status === 'error');
      if (scanError) this.error.set(scanError.message);
    });
  }

  assessmentFor(group: PackageGroup, version: VersionGroup): VersionAssessment | undefined {
    const scanId = version.occurrences[0]?.scanId;
    if (!scanId) return undefined;
    return this.assessmentsByKey().get(evidenceKey(scanId, {
      ecosystem: group.ecosystem, namespace: group.namespace, name: group.name, version: version.version,
    }));
  }

  assessmentLabel(status: AssessmentStatus): string {
    switch (status) {
      case 'MATCHED': return 'Avisos encontrados';
      case 'NO_KNOWN_ADVISORIES': return 'OSV no reporta avisos para esta versión';
      case 'INCOMPLETE': return 'Resultado parcial; revisa los detalles';
      case 'UNAVAILABLE': return 'No se pudo consultar OSV';
      case 'STALE': return 'Resultado desactualizado';
      default: return 'Aún sin evaluar';
    }
  }

  advisoryExplanation(advisory: Advisory): string {
    switch (advisory.id.toUpperCase()) {
      case 'GHSA-WJGM-6HV5-3CVF': return 'Si una aplicación convierte JSON no confiable directamente en una ruta de Java, una URI podría seleccionar un proveedor de archivos de terceros. El efecto depende de que ese proveedor tenga acciones externas; los proveedores incluidos en Java no producen por sí solos ese efecto.';
      case 'GHSA-WV8Q-QHHJ-9H54': return 'Con una configuración concreta de Jackson, cada identificador de tipo desconocido puede quedar guardado en memoria. Si se reciben muchos identificadores diferentes durante mucho tiempo, el proceso puede consumir memoria y dejar de responder.';
      default: return 'OSV relaciona esta versión exacta con un aviso de seguridad. El impacto depende de cómo el proyecto use el paquete y de si cumple las condiciones descritas en la fuente oficial.';
    }
  }

  advisoryAction(advisory: Advisory): string {
    switch (advisory.id.toUpperCase()) {
      case 'GHSA-WJGM-6HV5-3CVF': return 'Actualiza a una versión corregida compatible y evita convertir rutas desde datos externos sin validar los esquemas permitidos.';
      case 'GHSA-WV8Q-QHHJ-9H54': return 'Actualiza a una versión corregida. Revisa además si el proyecto usa tipos polimórficos con una implementación alternativa y procesa identificadores externos.';
      default: return 'Revisa el rango afectado y las versiones corregidas que publica OSV antes de planificar la actualización.';
    }
  }

  fixedVersions(advisory: Advisory, group: PackageGroup): string[] {
    const name = group.ecosystem === 'MAVEN' ? `${group.namespace}:${group.name}` : group.name;
    return fixesForPackage(advisory.affectedJson, group.ecosystem, name);
  }

  codeEvidenceKey(advisoryId: string, assessment: VersionAssessment): string {
    return `${assessment.scanId}:${assessment.scanComponentId}:${advisoryId.toUpperCase()}`;
  }

  loadCodeEvidence(advisoryId: string, assessment: VersionAssessment): void {
    const repository = this.repository();
    const id = this.codeEvidenceKey(advisoryId, assessment);
    if (!repository || this.codeEvidence()[id] || this.codeEvidenceLoading().has(id)) return;
    this.codeEvidenceLoading.update(current => new Set(current).add(id));
    this.codeEvidenceErrors.update(current => { const next={...current};delete next[id];return next; });
    const advisory = advisoryId.toUpperCase();
    const params = new URLSearchParams({ scanId: assessment.scanId, scanComponentId: assessment.scanComponentId });
    this.api.get<RepositoryCodeEvidence>(`/api/organizations/${repository.organizationId}/repositories/${repository.id}/vulnerability-analysis/${advisory}/code-evidence?${params}`)
      .pipe(finalize(()=>this.codeEvidenceLoading.update(current=>{const next=new Set(current);next.delete(id);return next;})))
      .subscribe({
        next: result => this.codeEvidence.update(current=>({...current,[id]:result})),
        error: failure => this.codeEvidenceErrors.update(current=>({...current,[id]:accessError(failure)})),
      });
  }

  private assessVulnerabilities(repository: Repository, scanIds: string[]): void {
    const requestSequence = this.scanLoadSequence;
    const requested = new Set(scanIds);
    this.assessingVulnerabilities.set(true);
    this.api.post<InventoryAssessment>(this.vulnerabilityEndpoint(repository), { scanIds }).pipe(
      finalize(() => { if (requestSequence === this.scanLoadSequence) this.assessingVulnerabilities.set(false); }),
    ).subscribe({
      next: (assessment) => {
        if (requestSequence !== this.scanLoadSequence) return;
        this.osvEvidence.update(items => [
          ...items.filter(item => !requested.has(item.scanId)),
          ...assessment.assessments.filter(item => requested.has(item.scanId)),
        ]);
        this.catalogDirty = true;
        this.packagePage.set(0);
        this.assessmentError.set('');
      },
      error: (failure: HttpErrorResponse) => {
        if (requestSequence !== this.scanLoadSequence) return;
        this.osvEvidence.update(items => items.map(item => requested.has(item.scanId)
          ? { ...item, status: item.advisories.length ? 'STALE' : 'UNAVAILABLE' } : item));
        this.assessmentError.set(accessError(failure));
      },
    });
  }

  private loadVulnerabilityAssessment(repository: Repository, requestSequence: number) {
    return this.api.get<InventoryAssessment>(this.vulnerabilityEndpoint(repository)).pipe(catchError((failure: HttpErrorResponse) => {
      if (requestSequence === this.scanLoadSequence) this.assessmentError.set(accessError(failure));
      return of(null);
    }));
  }

  assessCurrentRepository(): void {
    const repository = this.repository();
    if (!repository || this.assessingVulnerabilities() || this.busy()) return;
    const scanIds = this.scanResults().flatMap(result => result.scan?.dependencies.length ? [result.scan.scanId] : []);
    if (!scanIds.length) return;
    this.assessmentError.set('');
    this.assessVulnerabilities(repository, scanIds);
  }

  private vulnerabilityEndpoint(repository: Repository): string {
    return this.repositoryEndpoint(repository, 'vulnerability-analysis');
  }

  private loadScanner(repository: Repository, ecosystem: Ecosystem) {
    const endpoint = this.repositoryEndpoint(repository, ecosystem === 'MAVEN' ? 'maven-analysis' : 'npm-analysis');
    return this.api.get<Scan | null>(endpoint).pipe(
      map((scan) => this.scanResult(ecosystem, scan, scan?.manifestCount === 0)),
      catchError((failure: HttpErrorResponse) => of<ScanResult>({
        ecosystem,
        scan: null,
        status: failure.status === 404 ? 'not-analyzed' : 'error',
        message: failure.status === 404 ? 'Aún sin analizar' : accessError(failure),
      })),
    );
  }

  private scanResult(ecosystem: Ecosystem, scan: Scan | null, noManifest: boolean): ScanResult {
    if (noManifest) return { ecosystem, scan: null, status: 'no-manifest', message: 'No se encontró un manifiesto compatible' };
    if (!scan) return { ecosystem, scan: null, status: 'not-analyzed', message: 'Aún sin analizar' };
    return { ecosystem, scan, status: scan.coverageStatus === 'PARTIAL' ? 'partial' : 'complete',
      message: scan.coverageMessage ?? 'Análisis disponible' };
  }

  private repositoryEndpoint(repository: Repository, path: string): string {
    return `/api/organizations/${repository.organizationId}/repositories/${repository.id}/${path}`;
  }

  private toOccurrences(results: ScanResult[]): Occurrence[] {
    return results.flatMap(({ ecosystem, scan }) => scan
      ? scan.dependencies.map((dependency) => ({
        ecosystem,
        scanId: scan.scanId,
        source: scan.source,
        sourceSha256: scan.sourceSha256,
        commitSha: scan.commitSha,
        analyzedAt: scan.analyzedAt,
        manifestCount: scan.manifestCount,
        unresolvedCount: scan.unresolvedCount,
        dependency,
      }))
      : []);
  }
}
