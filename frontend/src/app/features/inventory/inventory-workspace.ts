import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { catchError, finalize, forkJoin, map, of } from 'rxjs';
import { SessionApiService, accessError } from '../../core/session-api.service';
import { TranslatePipe } from '../../core/translate.pipe';

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
  status: 'complete' | 'partial' | 'unavailable' | 'error';
  message: string;
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
  packageVersion: { ecosystem: string; namespace: string | null; name: string; version: string };
  status: AssessmentStatus; lastSuccessfulStatus: AssessmentStatus | null; checkedAt: string | null;
  lastSuccessfulAt: string | null; sourceModifiedAt: string | null; failureCode: string | null; advisories: Advisory[];
}
interface InventoryAssessment {
  generatedAt: string; assessedVersions: number; affectedVersions: number; advisoryCount: number;
  unavailableVersions: number; staleVersions: number; assessments: VersionAssessment[];
}
interface RepositoryCodeEvidence {
  advisoryId: string; status: 'POTENTIAL_MATCHES'|'NO_MATCH'|'LIMIT_REACHED'; sourceProvider: string; sourceSha256: string;
  sourceCommit: string|null; scannedFiles: number; matches: {filePath:string;lineNumber:number;excerpt:string;reason:string}[];
}

@Component({
  selector: 'app-inventory-workspace',
  imports: [DatePipe, TranslatePipe],
  templateUrl: './inventory-workspace.html',
  styleUrls: ['./inventory-workspace.scss', './inventory-catalog.scss', './inventory-advisories.scss'],
})
export class InventoryWorkspace implements OnInit {
  private readonly api = inject(SessionApiService);
  private scanLoadSequence = 0;

  readonly repositories = signal<Repository[]>([]);
  readonly repository = signal<Repository | null>(null);
  readonly occurrences = signal<Occurrence[]>([]);
  readonly scanResults = signal<ScanResult[]>([]);
  readonly vulnerabilityAssessment = signal<InventoryAssessment | null>(null);
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
  readonly loading = signal(true);
  readonly loadingScans = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');

  get groups(): PackageGroup[] {
    const filtered = this.occurrences().filter((occurrence) => {
      const dependency = occurrence.dependency;
      const matchesEcosystem = this.ecosystemFilter() === 'ALL' || occurrence.ecosystem === this.ecosystemFilter();
      const matchesKind = this.dependencyFilter() === 'ALL' || dependency.dependencyType === this.dependencyFilter();
      const matchesScope = this.scopeFilter() === 'ALL' || dependency.scope === this.scopeFilter();
      const query = this.search().trim().toLocaleLowerCase();
      const identity = `${dependency.coordinates.namespace ?? ''} ${dependency.coordinates.name} ${dependency.coordinates.version} ${dependency.manifestPath} ${dependency.scope}`.toLocaleLowerCase();
      return matchesEcosystem && matchesKind && matchesScope && (!query || identity.includes(query));
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

  get unresolvedCount(): number {
    return this.scanResults().reduce((sum, result) => sum + (result.scan?.unresolvedCount ?? 0), 0);
  }

  get hasCompletedScan(): boolean {
    return this.scanResults().some((result) => result.status === 'complete' || result.status === 'partial');
  }

  ngOnInit(): void {
    this.api.get<Repository[]>('/api/account/workspace').pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (items) => {
        this.repositories.set(items);
        this.setRepository(items[0] ?? null);
      },
      error: (failure) => this.error.set(accessError(failure)),
    });
  }

  setRepository(repository: Repository | null): void {
    const requestSequence = ++this.scanLoadSequence;
    this.repository.set(repository);
    this.occurrences.set([]);
    this.scanResults.set([]);
    this.vulnerabilityAssessment.set(null);
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
  }

  updateEcosystem(event: Event): void {
    this.ecosystemFilter.set((event.target as HTMLSelectElement).value as 'ALL' | Ecosystem);
  }

  updateDependencyKind(event: Event): void {
    this.dependencyFilter.set((event.target as HTMLSelectElement).value as 'ALL' | 'DIRECT' | 'TRANSITIVE' | 'UNKNOWN');
  }

  updateScope(event: Event): void {
    this.scopeFilter.set((event.target as HTMLSelectElement).value);
  }

  updateSecurityFilter(event: Event): void {
    this.securityFilter.set((event.target as HTMLSelectElement).value as 'ALL' | 'VULNERABLE' | 'NO_KNOWN' | 'REVIEW');
  }

  private matchesSecurityFilter(group: PackageGroup, version: VersionGroup): boolean {
    const filter = this.securityFilter();
    if (filter === 'ALL') return true;
    const status = this.assessmentFor(group, version)?.status;
    if (filter === 'VULNERABLE') return status === 'MATCHED';
    if (filter === 'NO_KNOWN') return status === 'NO_KNOWN_ADVISORIES';
    return status !== 'MATCHED' && status !== 'NO_KNOWN_ADVISORIES';
  }

  clearFilters(): void {
    this.search.set('');
    this.ecosystemFilter.set('ALL');
    this.dependencyFilter.set('ALL');
    this.scopeFilter.set('ALL');
    this.securityFilter.set('ALL');
  }

  analyzeRepository(): void {
    const repository = this.repository();
    if (!repository || this.busy() || this.loadingScans()) return;
    this.busy.set(true);
    this.error.set('');
    this.notice.set('');

    forkJoin([
      this.runScanner(repository, 'MAVEN'),
      this.runScanner(repository, 'NPM'),
    ]).subscribe({ next: (results) => {
      this.scanResults.set(results);
      this.occurrences.set(this.toOccurrences(results));
      const available = results.filter((result) => result.scan !== null).length;
      if (available === 2 && results.some((result) => result.status === 'partial')) this.notice.set('Análisis completado con cobertura parcial; revisa qué dependencias quedaron por resolver.');
      else if (available === 2) this.notice.set('Análisis completado para Maven y npm.');
      else if (available === 1) this.notice.set('El inventario se actualizó parcialmente; revisa el estado de cada ecosistema.');
      else this.error.set(results.find((result) => result.status === 'error')?.message ?? 'No encontramos manifiestos compatibles para Maven ni npm en este repositorio.');
      if (available > 0) this.assessVulnerabilities(repository);
      else this.busy.set(false);
    }, error: (failure) => { this.error.set(accessError(failure)); this.busy.set(false); } });
  }

  private loadScans(repository: Repository, requestSequence: number): void {
    forkJoin([
      this.loadScanner(repository, 'MAVEN'),
      this.loadScanner(repository, 'NPM'),
      this.loadVulnerabilityAssessment(repository),
    ]).pipe(finalize(() => {
      if (requestSequence === this.scanLoadSequence) this.loadingScans.set(false);
    })).subscribe(([maven, npm, assessment]) => {
      if (requestSequence !== this.scanLoadSequence) return;
      const results = [maven, npm];
      const found = results.filter((result) => result.scan !== null);
      this.scanResults.set(results);
      this.occurrences.set(this.toOccurrences(found));
      this.vulnerabilityAssessment.set(assessment);
      const scanError = results.find((result) => result.status === 'error');
      if (scanError) this.error.set(scanError.message);
    });
  }

  assessmentFor(group: PackageGroup, version: VersionGroup): VersionAssessment | undefined {
    return this.vulnerabilityAssessment()?.assessments.find((item) =>
      item.packageVersion.ecosystem.toUpperCase() === group.ecosystem
      && item.packageVersion.namespace === group.namespace && item.packageVersion.name === group.name
      && item.packageVersion.version === version.version);
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

  fixedVersions(advisory: Advisory): string[] {
    const knownFixes: Record<string, string[]> = {
      'GHSA-WJGM-6HV5-3CVF': ['2.18.10', '2.21.6', '2.22.2', '3.1.6', '3.2.2'],
      'GHSA-WV8Q-QHHJ-9H54': ['2.18.11', '2.21.7', '2.22.3', '3.1.7', '3.2.3'],
    };
    const curated = knownFixes[advisory.id.toUpperCase()];
    if (curated) return curated;
    try {
      const parsed: unknown = JSON.parse(advisory.affectedJson);
      const affected = Array.isArray(parsed) ? parsed : [parsed];
      const fixed = affected.flatMap((entry) => {
        if (!entry || typeof entry !== 'object' || !('ranges' in entry) || !Array.isArray(entry.ranges)) return [];
        return entry.ranges.flatMap((range: unknown) => {
          if (!range || typeof range !== 'object' || !('events' in range) || !Array.isArray(range.events)) return [];
          return range.events.flatMap((event: unknown) => event && typeof event === 'object' && 'fixed' in event && typeof event.fixed === 'string' ? [event.fixed] : []);
        });
      });
      return [...new Set(fixed)].sort((a,b)=>a.localeCompare(b,undefined,{numeric:true}));
    } catch {
      return [];
    }
  }

  loadCodeEvidence(advisoryId: string): void {
    const repository = this.repository();
    const id = advisoryId.toUpperCase();
    if (!repository || this.codeEvidence()[id] || this.codeEvidenceLoading().has(id)) return;
    this.codeEvidenceLoading.update(current => new Set(current).add(id));
    this.codeEvidenceErrors.update(current => { const next={...current};delete next[id];return next; });
    this.api.get<RepositoryCodeEvidence>(`/api/organizations/${repository.organizationId}/repositories/${repository.id}/vulnerability-analysis/${id}/code-evidence`)
      .pipe(finalize(()=>this.codeEvidenceLoading.update(current=>{const next=new Set(current);next.delete(id);return next;})))
      .subscribe({
        next: result => this.codeEvidence.update(current=>({...current,[id]:result})),
        error: failure => this.codeEvidenceErrors.update(current=>({...current,[id]:accessError(failure)})),
      });
  }

  private assessVulnerabilities(repository: Repository): void {
    this.assessingVulnerabilities.set(true);
    this.api.post<InventoryAssessment>(this.vulnerabilityEndpoint(repository), {}).pipe(
      finalize(() => { this.assessingVulnerabilities.set(false); this.busy.set(false); }),
    ).subscribe({
      next: (assessment) => { this.vulnerabilityAssessment.set(assessment); this.assessmentError.set(''); },
      error: (failure: HttpErrorResponse) => this.assessmentError.set(accessError(failure)),
    });
  }

  private loadVulnerabilityAssessment(repository: Repository) {
    return this.api.get<InventoryAssessment>(this.vulnerabilityEndpoint(repository)).pipe(catchError((failure: HttpErrorResponse) => {
      this.assessmentError.set(accessError(failure));
      return of(null);
    }));
  }

  assessCurrentRepository(): void {
    const repository = this.repository();
    if (!repository || this.assessingVulnerabilities()) return;
    this.assessmentError.set('');
    this.assessVulnerabilities(repository);
  }

  private vulnerabilityEndpoint(repository: Repository): string {
    return `/api/organizations/${repository.organizationId}/repositories/${repository.id}/vulnerability-analysis`;
  }

  private loadScanner(repository: Repository, ecosystem: Ecosystem) {
    return this.api.get<Scan | null>(this.endpoint(repository, ecosystem)).pipe(
      map((scan) => ({ ecosystem, scan, status: !scan ? 'unavailable' as const : scan.coverageStatus === 'PARTIAL' ? 'partial' as const : 'complete' as const, message: !scan ? 'Aún sin analizar' : scan.coverageMessage ?? 'Análisis disponible' })),
      catchError((failure: HttpErrorResponse) => of({
        ecosystem,
        scan: null,
        status: failure.status === 404 ? 'unavailable' as const : 'error' as const,
        message: failure.status === 404 ? 'Aún sin analizar' : accessError(failure),
      })),
    );
  }

  private runScanner(repository: Repository, ecosystem: Ecosystem) {
    return this.api.post<Scan>(this.endpoint(repository, ecosystem), {}).pipe(
      map((scan): ScanResult => ({ ecosystem, scan, status: scan.coverageStatus === 'PARTIAL' ? 'partial' : 'complete', message: scan.coverageMessage ?? 'Analizado' })),
      catchError((failure: HttpErrorResponse) => {
        const detail = typeof failure.error?.detail === 'string' ? failure.error.detail : '';
        const noManifest = failure.status === 404 || (failure.status === 400 && /manifest|package-lock|pom\.xml|proyecto Maven/i.test(detail));
        return of({
          ecosystem,
          scan: null,
          status: noManifest ? 'unavailable' as const : 'error' as const,
          message: detail || (noManifest ? 'No se encontró un manifiesto compatible' : accessError(failure)),
        });
      }),
    );
  }

  private endpoint(repository: Repository, ecosystem: Ecosystem): string {
    const scanner = ecosystem === 'MAVEN' ? 'maven-analysis' : 'npm-analysis';
    return `/api/organizations/${repository.organizationId}/repositories/${repository.id}/${scanner}`;
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
