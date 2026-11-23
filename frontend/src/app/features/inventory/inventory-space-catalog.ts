import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, map, of, switchMap, timer } from 'rxjs';
import { accessError, SessionApiService } from '../../core/session-api.service';
import { TranslatePipe } from '../../core/translate.pipe';

export interface InventoryRepository {
  id: string;
  organizationId: string;
  organizationName: string;
  fullName: string;
  source: string;
}

export interface InventoryLocation {
  repositoryId: string;
  ecosystem: 'MAVEN' | 'NPM';
  packageName: string;
  version: string;
}

type Filter = 'repositoryId' | 'query' | 'ecosystem' | 'version' | 'kind' | 'scope' | 'security' | 'severity' | 'riskFirst';
interface PackageKey { ecosystem: InventoryLocation['ecosystem']; namespace: string; name: string; }
interface PackageSummary {
  key: PackageKey; versions: number; repositories: number; occurrences: number; severity: string | null;
  withAdvisories: number; pending: number;
}
interface CatalogPage { packages: PackageSummary[]; totalPackages: number; totalVersions: number; }
interface Occurrence {
  id: string; repositoryId: string; repositoryName: string; source: string; version: string; manifestPath: string;
  scope: string | null; kind: string; scanId: string; analyzedAt: string | null; commit: string | null;
  sourceSha256: string | null; assessment: string; checkedAt: string | null; advisoryCount: number; severity: string | null;
}
interface OccurrencePage { occurrences: Occurrence[]; totalOccurrences: number; }

@Component({
  selector: 'app-inventory-space-catalog',
  imports: [DatePipe, TranslatePipe],
  templateUrl: './inventory-space-catalog.html',
  styleUrl: './inventory-space-catalog.scss',
})
export class InventorySpaceCatalog implements OnInit {
  readonly repositories = input.required<readonly InventoryRepository[]>();
  readonly openRepository = output<InventoryLocation>();
  private readonly api = inject(SessionApiService);
  private readonly destroy = inject(DestroyRef);
  private readonly listRequests = new Subject<string>();
  private readonly detailRequests = new Subject<string | null>();
  readonly organizationId = signal('');
  readonly filters = signal<Partial<Record<Filter, string>>>({});
  readonly page = signal(0);
  readonly detailPage = signal(0);
  readonly pageSize = 25;
  readonly result = signal<CatalogPage | null>(null);
  readonly detail = signal<OccurrencePage | null>(null);
  readonly expanded = signal<PackageSummary | null>(null);
  readonly loading = signal(false);
  readonly detailLoading = signal(false);
  readonly error = signal('');
  readonly detailError = signal('');
  readonly organizations = computed(() => [...new Map(this.repositories()
    .map(repo => [repo.organizationId, repo.organizationName])).entries()]);
  readonly availableRepositories = computed(() => this.repositories().filter(repo => repo.organizationId === this.organizationId()));
  readonly filterActive = computed(() => Object.entries(this.filters()).some(([key, value]) => key !== 'riskFirst' && !!value?.trim()));
  readonly severities = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN'];
  readonly scopes = ['runtime', 'development', 'test', 'provided', 'optional'];

  ngOnInit(): void {
    this.listRequests.pipe(switchMap(path => timer(180).pipe(switchMap(() => this.api.get<CatalogPage>(path)),
      map(result => ({ result, error: '' })),
      catchError(failure => of({ result: null, error: accessError(failure) })),
    )), takeUntilDestroyed(this.destroy)).subscribe(({ result, error }) => {
      this.loading.set(false);
      this.error.set(error);
      if (result && !result.packages.length && this.page() > 0) {
        this.page.set(0);
        this.load();
      } else this.result.set(result);
    });
    this.detailRequests.pipe(switchMap(path => path ? this.api.get<OccurrencePage>(path).pipe(
      map(result => ({ result, error: '' })),
      catchError(failure => of({ result: null, error: accessError(failure) })),
    ) : of({ result: null, error: '' })), takeUntilDestroyed(this.destroy)).subscribe(({ result, error }) => {
      this.detailLoading.set(false);
      this.detail.set(result);
      this.detailError.set(error);
    });
    this.organizationId.set(this.organizations()[0]?.[0] ?? '');
    if (this.organizationId()) this.load();
  }

  chooseOrganization(event: Event): void {
    this.organizationId.set(this.value(event));
    this.filters.update(filters => ({ ...filters, repositoryId: '' }));
    this.page.set(0);
    this.load();
  }

  filter(key: Filter, event: Event): void {
    this.setFilter(key, this.value(event));
  }

  setFilter(key: Filter, value: string): void {
    this.filters.update(filters => ({ ...filters, [key]: value }));
    this.page.set(0);
    this.load();
  }

  clear(): void {
    this.filters.set({});
    this.page.set(0);
    this.load();
  }

  load(): void {
    this.expanded.set(null);
    this.detailRequests.next(null);
    this.loading.set(true);
    this.error.set('');
    this.result.set(null);
    this.listRequests.next(this.path('', this.page()));
  }

  goToPage(page: number): void {
    this.page.set(page);
    this.load();
  }

  toggle(packageItem: PackageSummary): void {
    if (this.expanded() === packageItem) {
      this.expanded.set(null);
      this.detailRequests.next(null);
      return;
    }
    this.expanded.set(packageItem);
    this.showOccurrences(0);
  }

  showOccurrences(page: number): void {
    const selection = this.expanded();
    if (!selection) return;
    this.detailPage.set(page);
    this.detailLoading.set(true);
    this.detail.set(null);
    this.detailError.set('');
    this.detailRequests.next(this.path('/occurrences', page, selection.key));
  }

  showRepository(key: PackageKey, occurrence: Occurrence): void {
    this.openRepository.emit({ repositoryId: occurrence.repositoryId, ecosystem: key.ecosystem,
      packageName: this.name(key), version: occurrence.version });
  }

  graphUrl(key: PackageKey, occurrence: Occurrence): string {
    const params = new URLSearchParams({ view: 'dependencies', repository: occurrence.repositoryId,
      package: `${this.name(key)}@${occurrence.version}` });
    return '?' + params;
  }

  name(key: PackageKey): string {
    return key.ecosystem === 'MAVEN' && key.namespace ? `${key.namespace}:${key.name}` : key.name;
  }

  severityLabel(severity: string): string {
    switch (severity) {
      case 'CRITICAL': return 'Crítica';
      case 'HIGH': return 'Alta';
      case 'MEDIUM': return 'Media';
      case 'LOW': return 'Baja';
      default: return 'Severidad desconocida';
    }
  }

  assessmentLabel(state: string): string {
    switch (state) {
      case 'MATCHED': return 'Avisos encontrados';
      case 'NO_KNOWN_ADVISORIES': return 'Sin avisos conocidos';
      case 'INCOMPLETE': return 'Resultado parcial';
      case 'UNAVAILABLE': return 'No se pudo consultar OSV';
      case 'STALE': return 'Resultado desactualizado';
      default: return 'Aún sin evaluar';
    }
  }

  private path(suffix: string, page: number, key?: PackageKey): string {
    const params = new URLSearchParams({ page: String(page), size: String(this.pageSize) });
    for (const [name, value] of Object.entries(this.filters())) if (value?.trim()) params.set(name, value.trim());
    if (key) {
      params.set('packageEcosystem', key.ecosystem);
      params.set('namespace', key.namespace);
      params.set('name', key.name);
    }
    return `/api/organizations/${this.organizationId()}/inventory${suffix}?${params}`;
  }

  private value(event: Event): string {
    return (event.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
