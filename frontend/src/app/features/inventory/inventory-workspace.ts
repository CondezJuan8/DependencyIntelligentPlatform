import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnInit, signal } from '@angular/core';
import { catchError, finalize, forkJoin, map, of } from 'rxjs';
import { SessionApiService, accessError } from '../../core/session-api.service';

type Ecosystem = 'MAVEN' | 'NPM';
type DependencyKind = 'DIRECT' | 'TRANSITIVE' | string;

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
  dependencies: Dependency[];
}

interface ScanResult {
  ecosystem: Ecosystem;
  scan: Scan | null;
  status: 'complete' | 'unavailable' | 'error';
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
}

@Component({
  selector: 'app-inventory-workspace',
  imports: [DatePipe],
  templateUrl: './inventory-workspace.html',
  styleUrls: ['./inventory-workspace.scss', './inventory-catalog.scss'],
})
export class InventoryWorkspace implements OnInit {
  private readonly api = inject(SessionApiService);
  private scanLoadSequence = 0;

  readonly repositories = signal<Repository[]>([]);
  readonly repository = signal<Repository | null>(null);
  readonly occurrences = signal<Occurrence[]>([]);
  readonly scanResults = signal<ScanResult[]>([]);
  readonly search = signal('');
  readonly ecosystemFilter = signal<'ALL' | Ecosystem>('ALL');
  readonly dependencyFilter = signal<'ALL' | 'DIRECT' | 'TRANSITIVE'>('ALL');
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
        group = { key, ecosystem: occurrence.ecosystem, namespace, name, versions: [], directCount: 0, transitiveCount: 0 };
        packages.set(key, group);
      }
      let versionGroup = group.versions.find((candidate) => candidate.version === version);
      if (!versionGroup) {
        versionGroup = { version, directCount: 0, transitiveCount: 0, occurrences: [] };
        group.versions.push(versionGroup);
      }
      versionGroup.occurrences.push(occurrence);
      if (occurrence.dependency.dependencyType === 'DIRECT') {
        versionGroup.directCount++;
        group.directCount++;
      } else {
        versionGroup.transitiveCount++;
        group.transitiveCount++;
      }
    }

    return [...packages.values()]
      .map((group) => ({ ...group, versions: group.versions.sort((left, right) => left.version.localeCompare(right.version, undefined, { numeric: true })) }))
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

  get scopes(): string[] {
    return [...new Set(this.occurrences().map((occurrence) => occurrence.dependency.scope).filter(Boolean))].sort();
  }

  get unresolvedCount(): number {
    return this.scanResults().reduce((sum, result) => sum + (result.scan?.unresolvedCount ?? 0), 0);
  }

  get hasCompletedScan(): boolean {
    return this.scanResults().some((result) => result.status === 'complete');
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
    this.dependencyFilter.set((event.target as HTMLSelectElement).value as 'ALL' | 'DIRECT' | 'TRANSITIVE');
  }

  updateScope(event: Event): void {
    this.scopeFilter.set((event.target as HTMLSelectElement).value);
  }

  clearFilters(): void {
    this.search.set('');
    this.ecosystemFilter.set('ALL');
    this.dependencyFilter.set('ALL');
    this.scopeFilter.set('ALL');
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
    ]).pipe(finalize(() => this.busy.set(false))).subscribe((results) => {
      this.scanResults.set(results);
      this.occurrences.set(this.toOccurrences(results));
      const complete = results.filter((result) => result.status === 'complete').length;
      if (complete === 2) this.notice.set('Análisis completado para Maven y npm.');
      else if (complete === 1) this.notice.set('El inventario se actualizó parcialmente; revisa el estado de cada ecosistema.');
      else this.error.set(results.find((result) => result.status === 'error')?.message ?? 'No encontramos manifiestos compatibles para Maven ni npm en este repositorio.');
    });
  }

  private loadScans(repository: Repository, requestSequence: number): void {
    forkJoin([
      this.loadScanner(repository, 'MAVEN'),
      this.loadScanner(repository, 'NPM'),
    ]).pipe(finalize(() => {
      if (requestSequence === this.scanLoadSequence) this.loadingScans.set(false);
    })).subscribe((results) => {
      if (requestSequence !== this.scanLoadSequence) return;
      const found = results.filter((result) => result.scan !== null);
      this.scanResults.set(results);
      this.occurrences.set(this.toOccurrences(found));
      const scanError = results.find((result) => result.status === 'error');
      if (scanError) this.error.set(scanError.message);
    });
  }

  private loadScanner(repository: Repository, ecosystem: Ecosystem) {
    return this.api.get<Scan | null>(this.endpoint(repository, ecosystem)).pipe(
      map((scan) => ({ ecosystem, scan, status: scan ? 'complete' as const : 'unavailable' as const, message: scan ? 'Análisis disponible' : 'Aún sin analizar' })),
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
      map((scan): ScanResult => ({ ecosystem, scan, status: 'complete', message: 'Analizado' })),
      catchError((failure: HttpErrorResponse) => {
        const unavailable = failure.status === 400 || failure.status === 404;
        return of({
          ecosystem,
          scan: null,
          status: unavailable ? 'unavailable' as const : 'error' as const,
          message: unavailable ? 'No se encontró un manifiesto compatible' : accessError(failure),
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
