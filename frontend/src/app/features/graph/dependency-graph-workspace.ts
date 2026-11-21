import { Component, ElementRef, OnDestroy, OnInit, ViewChild, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import cytoscape, { Core, ElementDefinition, NodeSingular } from 'cytoscape';
import { finalize } from 'rxjs';
import { SessionApiService, accessError } from '../../core/session-api.service';
import { TranslatePipe } from '../../core/translate.pipe';

interface Repository { id: string; organizationId: string; fullName: string; source: string; }
interface GraphSource { scanId: string; ecosystem: string; sha256: string; commitSha: string | null; analyzedAt: string; unresolvedCount: number; }
interface GraphNode { id: string; label: string; version: string; ecosystem: string; scope: string; dependencyType: string; manifestPath: string; project: boolean; }
interface DisplayNode extends GraphNode { occurrences: number; manifestPaths: string[]; scopes: string[]; level: number; }
interface GraphEdge { id: string; source: string; target: string; relationship: string; ecosystem: string; }
interface RepositoryGraph { repositoryId: string; repositoryName: string; sources: GraphSource[]; nodes: GraphNode[]; edges: GraphEdge[]; unresolvedCount: number; }
interface DisplayGraph { nodes: DisplayNode[]; edges: GraphEdge[]; }
const MAX_MAP_NODES = 1000;
const MAX_NETWORK_NODES = 500;
const PACKAGE_LIST_PAGE_SIZE = 100;

@Component({
  selector: 'app-dependency-graph-workspace',
  imports: [DatePipe, TranslatePipe],
  templateUrl: './dependency-graph-workspace.html',
  styleUrl: './dependency-graph-workspace.scss',
})
export class DependencyGraphWorkspace implements OnInit, OnDestroy {
  private readonly api = inject(SessionApiService);
  private graphHost?: ElementRef<HTMLDivElement>;
  private cy?: Core;
  private themeObserver?: MutationObserver;
  private graphResizeObserver?: ResizeObserver;
  private highlightFrame = 0;
  private hoveredNodeId: string | null = null;
  private readonly networkPositions = new Map<string, { x: number; y: number }>();
  private readonly listLimit = signal(PACKAGE_LIST_PAGE_SIZE);

  @ViewChild('graphHost')
  set graphCanvas(host: ElementRef<HTMLDivElement> | undefined) {
    if (this.graphHost?.nativeElement === host?.nativeElement) return;
    this.themeObserver?.disconnect();
    this.graphResizeObserver?.disconnect();
    this.cy?.destroy();
    this.cy = undefined;
    this.graphHost = host;
    if (host) this.initializeGraph();
  }

  readonly repositories = signal<Repository[]>([]);
  readonly repository = signal<Repository | null>(null);
  readonly graph = signal<RepositoryGraph | null>(null);
  readonly displayGraph = signal<DisplayGraph | null>(null);
  readonly selectedNode = signal<DisplayNode | null>(null);
  readonly search = signal('');
  readonly ecosystem = signal('ALL');
  readonly layoutMode = signal<'LAYERS' | 'NETWORK'>('NETWORK');
  readonly viewMode = signal<'OVERVIEW' | 'LOCAL'>('OVERVIEW');
  readonly localDepth = signal(1);
  readonly localDirection = signal<'BOTH' | 'OUTGOING' | 'INCOMING'>('BOTH');
  readonly dependencyTypeFilter = signal<'ALL' | 'DIRECT' | 'TRANSITIVE' | 'UNKNOWN'>('ALL');
  readonly moduleFilter = signal('ALL');
  readonly scopeFilter = signal('ALL');
  readonly loading = signal(true);
  readonly graphLoading = signal(false);
  readonly error = signal('');

  ngOnInit(): void {
    this.api.get<Repository[]>('/api/account/workspace').pipe(finalize(() => this.loading.set(false))).subscribe({
      next: repositories => {
        this.repositories.set(repositories);
        this.selectRepository(repositories[0]?.id ?? '');
      },
      error: failure => this.error.set(accessError(failure)),
    });
  }

  ngOnDestroy(): void { this.themeObserver?.disconnect(); this.graphResizeObserver?.disconnect(); this.cy?.destroy(); }

  selectRepository(id: string): void {
    const selected = this.repositories().find(repository => repository.id === id) ?? null;
    this.repository.set(selected);
    this.graph.set(null);
    this.displayGraph.set(null);
    this.selectedNode.set(null);
    this.networkPositions.clear();
    this.hoveredNodeId = null;
    this.viewMode.set('OVERVIEW');
    this.moduleFilter.set('ALL');
    this.scopeFilter.set('ALL');
    this.dependencyTypeFilter.set('ALL');
    this.ecosystem.set('ALL');
    this.search.set('');
    this.localDepth.set(1);
    this.localDirection.set('BOTH');
    this.listLimit.set(PACKAGE_LIST_PAGE_SIZE);
    this.error.set('');
    if (!selected) { this.draw(); return; }
    this.graphLoading.set(true);
    this.api.get<RepositoryGraph>(`/api/organizations/${selected.organizationId}/repositories/${selected.id}/dependency-graph`)
      .pipe(finalize(() => this.graphLoading.set(false)))
      .subscribe({
        next: graph => { this.graph.set(graph); this.displayGraph.set(this.toDisplayGraph(graph)); this.draw(); },
        error: failure => { this.error.set(accessError(failure)); this.draw(); },
      });
  }

  onRepositoryChange(event: Event): void { this.selectRepository((event.target as HTMLSelectElement).value); }
  onSearch(event: Event): void {
    this.search.set((event.target as HTMLInputElement).value);
    this.listLimit.set(PACKAGE_LIST_PAGE_SIZE);
    this.scheduleHighlightMatches();
  }
  onEcosystemChange(event: Event): void {
    this.ecosystem.set((event.target as HTMLSelectElement).value);
    this.listLimit.set(PACKAGE_LIST_PAGE_SIZE);
    if (!this.moduleGroups().some(group => group.name === this.moduleFilter())) this.moduleFilter.set('ALL');
    if (!this.availableScopes().includes(this.scopeFilter())) this.scopeFilter.set('ALL');
    const selected = this.selectedNode();
    if (selected && !this.visibleEcosystem(selected)) this.selectedNode.set(null);
    this.draw();
  }

  onDependencyTypeChange(event: Event): void { this.dependencyTypeFilter.set((event.target as HTMLSelectElement).value as 'ALL' | 'DIRECT' | 'TRANSITIVE' | 'UNKNOWN'); this.clearHiddenSelection(); this.draw(); }
  onModuleChange(event: Event): void { this.moduleFilter.set((event.target as HTMLSelectElement).value); this.clearHiddenSelection(); this.draw(); }
  onScopeChange(event: Event): void { this.scopeFilter.set((event.target as HTMLSelectElement).value); this.clearHiddenSelection(); this.draw(); }
  onDepthChange(event: Event): void { this.localDepth.set(Number((event.target as HTMLSelectElement).value) || 1); this.draw(); }
  onDirectionChange(event: Event): void { this.localDirection.set((event.target as HTMLSelectElement).value as 'BOTH' | 'OUTGOING' | 'INCOMING'); this.draw(); }

  setModuleFilter(module: string): void { this.moduleFilter.set(module); this.clearHiddenSelection(); this.draw(); }
  clearFilters(): void {
    this.ecosystem.set('ALL');
    this.dependencyTypeFilter.set('ALL');
    this.moduleFilter.set('ALL');
    this.scopeFilter.set('ALL');
    this.search.set('');
    this.draw();
  }

  showOverview(): void { this.viewMode.set('OVERVIEW'); this.draw(); }
  showLocalView(): void {
    const node = this.selectedNode();
    if (!node || node.project) return;
    this.viewMode.set('LOCAL');
    this.localDepth.set(1);
    this.localDirection.set('BOTH');
    this.draw();
  }

  private clearHiddenSelection(): void {
    const selected = this.selectedNode();
    if (selected && !this.filteredGraph()?.nodes.some(node => node.id === selected.id)) this.selectedNode.set(null);
  }

  setLayoutMode(mode: 'LAYERS' | 'NETWORK'): void {
    if (this.layoutMode() === mode) return;
    this.layoutMode.set(mode);
    this.draw();
  }

  selectNode(id: string): void {
    const node = this.displayGraph()?.nodes.find(item => item.id === id) ?? null;
    this.selectedNode.set(node);
    if (node && this.cy) {
      if (this.cy.$id(id).length === 0 && this.renderLimited()) this.draw();
      this.cy.$('node:selected').unselect();
      const target = this.cy.$id(id);
      if (target.length > 0) {
        target.select();
        if (this.cy.nodes().length < 250 && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
          this.cy.animate({ center: { eles: target }, duration: 160 });
        } else {
          this.cy.center(target);
        }
      }
      this.highlightMatches();
    }
  }

  zoomIn(): void { this.cy?.zoom({ level: this.cy.zoom() * 1.2, renderedPosition: { x: this.cy.width() / 2, y: this.cy.height() / 2 } }); }
  zoomOut(): void { this.cy?.zoom({ level: this.cy.zoom() / 1.2, renderedPosition: { x: this.cy.width() / 2, y: this.cy.height() / 2 } }); }
  fitGraph(): void { this.cy?.fit(undefined, 44); }
  loadMorePackages(): void { this.listLimit.update(limit => limit + PACKAGE_LIST_PAGE_SIZE); }

  readonly candidatePackageNodes = computed(() => (this.displayGraph()?.nodes ?? [])
    .filter(node => !node.project && this.visibleEcosystem(node)));
  readonly packageNodes = computed(() => (this.filteredGraph()?.nodes ?? []).filter(node => !node.project));
  readonly moduleGroups = computed(() => {
    const groups = new Map<string, number>();
    for (const node of this.candidatePackageNodes()) {
      const module = this.moduleOf(node);
      groups.set(module, (groups.get(module) ?? 0) + 1);
    }
    return [...groups].map(([name, count]) => ({ name, count })).sort((left, right) => right.count - left.count || left.name.localeCompare(right.name));
  });
  readonly availableScopes = computed(() => [...new Set(this.candidatePackageNodes().flatMap(node => node.scopes))].filter(Boolean).sort());
  readonly filteredGraph = computed(() => this.createFilteredGraph());
  readonly filteredPackageCount = computed(() => (this.filteredGraph()?.nodes ?? []).filter(node => !node.project).length);
  readonly activeFilters = computed(() => [
    this.ecosystem() !== 'ALL' ? this.ecosystem() : '',
    this.dependencyTypeFilter() !== 'ALL' ? (this.dependencyTypeFilter() === 'DIRECT' ? 'Directas' : this.dependencyTypeFilter() === 'TRANSITIVE' ? 'Transitivas' : 'Relación sin resolver') : '',
    this.moduleFilter() !== 'ALL' ? this.moduleFilter() : '',
    this.scopeFilter() !== 'ALL' ? this.scopeFilter() : '',
    this.search().trim() ? `«${this.search().trim()}»` : '',
  ].filter(Boolean));
  readonly filteredPackageNodes = computed(() => {
    const query = this.search().trim().toLocaleLowerCase();
    if (!query) return this.packageNodes();
    return this.packageNodes().filter(node => `${node.label} ${node.version} ${node.manifestPaths.join(' ')}`.toLocaleLowerCase().includes(query));
  });
  readonly visiblePackageNodes = computed(() => this.filteredPackageNodes().slice(0, this.listLimit()));
  readonly hasMorePackages = computed(() => this.filteredPackageNodes().length > this.visiblePackageNodes().length);
  readonly packageCount = computed(() => this.candidatePackageNodes().length);
  readonly renderLimit = computed(() => this.layoutMode() === 'NETWORK' ? MAX_NETWORK_NODES : MAX_MAP_NODES);
  readonly renderLimited = computed(() => this.filteredPackageCount() + 1 > this.renderLimit());
  readonly directCount = computed(() => this.packageNodes().filter(node => node.dependencyType === 'DIRECT').length);
  readonly transitiveCount = computed(() => this.packageNodes().filter(node => node.dependencyType === 'TRANSITIVE').length);
  readonly unknownCount = computed(() => this.packageNodes().filter(node => node.dependencyType === 'UNKNOWN').length);
  readonly edgeCount = computed(() => this.filteredGraph()?.edges.length ?? 0);
  readonly outgoingNodes = computed(() => {
    const selected = this.selectedNode();
    const graph = this.filteredGraph();
    if (!selected || !graph) return [];
    const ids = new Set(graph.edges.filter(edge => edge.source === selected.id).map(edge => edge.target));
    return graph.nodes.filter(node => ids.has(node.id));
  });
  readonly incomingNodes = computed(() => {
    const selected = this.selectedNode();
    const graph = this.filteredGraph();
    if (!selected || !graph) return [];
    const ids = new Set(graph.edges.filter(edge => edge.target === selected.id).map(edge => edge.source));
    return graph.nodes.filter(node => ids.has(node.id));
  });

  private initializeGraph(): void {
    if (!this.graphHost || this.cy) return;
    this.cy = cytoscape({
      container: this.graphHost.nativeElement,
      elements: [],
      minZoom: 0.15,
      maxZoom: 3,
      wheelSensitivity: 0.18,
      style: [
        { selector: 'node', style: { 'background-color': '#FFFFFF', 'background-opacity': 1, 'border-width': 1, 'border-color': '#CBD5E1', color: '#0F172A', label: 'data(label)', 'font-family': 'IBM Plex Sans, sans-serif', 'font-size': 11, 'font-weight': 600, 'text-wrap': 'wrap', 'text-max-width': '194px', width: 224, height: 62, shape: 'round-rectangle', 'text-valign': 'center', 'text-halign': 'center', 'text-margin-y': 0, 'text-outline-width': 0 } },
        { selector: 'node.direct', style: { 'border-color': '#2563EB', 'border-width': 2 } },
        { selector: 'node.transitive', style: { 'border-color': '#06B6D4', 'border-width': 1.5 } },
        { selector: 'node.unknown', style: { 'border-color': '#EAB308', 'border-width': 2, 'border-style': 'dashed' } },
        { selector: 'node.project', style: { 'background-color': '#101827', 'border-color': '#06B6D4', 'border-width': 2, color: '#FFFFFF', 'font-size': 12, 'font-weight': 700, width: 232, height: 72 } },
        { selector: 'node.network', style: { width: 'data(size)', height: 'data(size)', shape: 'ellipse', label: '', 'background-opacity': 0.94, 'border-width': 1.5, 'text-halign': 'left', 'text-valign': 'center', 'text-margin-x': 11, 'font-size': 10, 'font-weight': 600, 'text-wrap': 'ellipsis', 'text-max-width': '180px', 'text-background-opacity': 0.9, 'text-background-padding': '4px', 'text-background-shape': 'roundrectangle', 'overlay-padding': 4, 'overlay-opacity': 0.1, 'overlay-color': '#2563EB' } },
        { selector: 'node.network.direct', style: { 'background-color': '#2563EB', 'border-color': '#93C5FD', 'border-width': 2, 'overlay-color': '#2563EB', 'overlay-opacity': 0.18 } },
        { selector: 'node.network.transitive', style: { 'background-color': '#06B6D4', 'border-color': '#A5F3FC', 'border-width': 1.5, 'overlay-color': '#06B6D4', 'overlay-opacity': 0.13 } },
        { selector: 'node.network.unknown', style: { 'background-color': '#EAB308', 'border-color': '#FEF08A', 'border-width': 2, 'overlay-color': '#EAB308', 'overlay-opacity': 0.16 } },
        { selector: 'node.network.project', style: { width: 26, height: 26, shape: 'ellipse', 'background-color': '#101827', 'border-color': '#06B6D4', 'border-width': 3, label: 'data(label)', 'font-size': 11, 'font-weight': 700, color: '#FFFFFF', 'overlay-color': '#06B6D4', 'overlay-padding': 9, 'overlay-opacity': 0.32, 'text-background-opacity': 0 } },
        { selector: 'node.network.label-visible', style: { label: 'data(label)' } },
        { selector: 'node:selected', style: { 'border-color': '#06B6D4', 'border-width': 3, 'overlay-opacity': 0.08, 'overlay-color': '#2563EB' } },
        { selector: 'edge', style: { width: 1.5, opacity: 0.6, 'line-color': '#94A3B8', 'target-arrow-color': '#64748B', 'target-arrow-shape': 'triangle', 'curve-style': 'bezier', 'arrow-scale': 0.85 } },
        { selector: 'edge[relationship = "declares"]', style: { 'line-color': '#2563EB', 'target-arrow-color': '#2563EB', 'line-style': 'solid' } },
        { selector: 'edge.path-active', style: { width: 2.5, opacity: 1, 'line-color': '#06B6D4', 'target-arrow-color': '#06B6D4', 'z-index': 2 } },
        { selector: 'edge.network', style: { width: 1, opacity: 0.24, 'curve-style': 'haystack', 'haystack-radius': 0.5, 'target-arrow-shape': 'none', 'line-color': '#94A3B8' } },
        { selector: 'edge.network.path-active', style: { width: 2, opacity: 0.95, 'curve-style': 'bezier', 'target-arrow-shape': 'triangle', 'target-arrow-color': '#06B6D4', 'line-color': '#06B6D4' } },
        { selector: '.dimmed', style: { opacity: 0.12 } },
      ],
      layout: { name: 'preset', fit: false },
    });
    this.cy.on('tap', 'node', event => this.selectNode(event.target.id()));
    this.cy.on('mouseover', 'node', event => {
      if (this.layoutMode() !== 'NETWORK') return;
      this.hoveredNodeId = event.target.id();
      this.highlightMatches();
    });
    this.cy.on('mouseout', 'node', () => {
      if (!this.hoveredNodeId) return;
      this.hoveredNodeId = null;
      this.highlightMatches();
    });
    this.cy.on('zoom', () => this.updateNetworkLabels());
    this.cy.on('tap', event => { if (event.target === this.cy) { this.cy?.$('node:selected').unselect(); this.selectedNode.set(null); } });
    this.applyGraphTheme();
    this.themeObserver = new MutationObserver(() => this.applyGraphTheme());
    this.themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    this.graphResizeObserver = new ResizeObserver(entries => {
      const size = entries[0]?.contentRect;
      if (!size || size.width === 0 || size.height === 0 || !this.cy) return;
      this.cy.resize();
    });
    this.graphResizeObserver.observe(this.graphHost.nativeElement);
    requestAnimationFrame(() => {
      if (!this.cy || !this.graphHost) return;
      this.cy.resize();
      this.cy.fit(undefined, 44);
    });
    this.draw();
  }

  private applyGraphTheme(): void {
    if (!this.cy) return;
    const theme = getComputedStyle(document.documentElement);
    const text = theme.getPropertyValue('--di-text').trim() || '#0F172A';
    const surface = theme.getPropertyValue('--di-surface').trim() || '#FFFFFF';
    const border = theme.getPropertyValue('--di-border').trim() || '#E2E8F0';
    const secondary = theme.getPropertyValue('--di-text-secondary').trim() || '#536784';
    this.cy.style()
      .selector('node').style({ color: text, 'background-color': surface, 'border-color': border })
      .selector('node.direct').style({ 'border-color': '#2563EB' })
      .selector('node.transitive').style({ 'border-color': '#06B6D4' })
      .selector('node.network').style({ 'text-background-color': surface })
      .selector('node.project').style({ color: '#FFFFFF', 'background-color': '#101827', 'border-color': '#06B6D4' })
      .selector('edge').style({ color: secondary, 'text-background-color': surface, 'line-color': border, 'target-arrow-color': secondary })
      .selector('edge[relationship = "declares"]').style({ 'line-color': '#2563EB', 'target-arrow-color': '#2563EB' })
      .selector('edge.path-active').style({ 'line-color': '#06B6D4', 'target-arrow-color': '#06B6D4' })
      .update();
  }

  private draw(): void {
    if (!this.cy || !this.graphHost) return;
    const graph = this.filteredGraph();
    if (!graph) { this.cy.elements().remove(); return; }
    const nodes = this.nodesForCanvas(graph);
    const nodeIds = new Set(nodes.map(node => node.id));
    const edges = graph.edges.filter(edge => nodeIds.has(edge.source) && nodeIds.has(edge.target));
    if (this.layoutMode() === 'NETWORK') this.cy.nodes().forEach(node => { this.networkPositions.set(node.id(), node.position()); });
    const degrees = new Map<string, number>();
    for (const edge of edges) {
      degrees.set(edge.source, (degrees.get(edge.source) ?? 0) + 1);
      degrees.set(edge.target, (degrees.get(edge.target) ?? 0) + 1);
    }
    const elements: ElementDefinition[] = [
      ...nodes.map(node => ({ data: { id: node.id, label: this.visualLabel(node), searchLabel: `${node.label} ${node.version} ${node.manifestPaths.join(' ')}`, project: node.project, ecosystem: node.ecosystem, dependencyType: node.dependencyType, level: node.level, size: this.networkNodeSize(node, degrees.get(node.id) ?? 0) }, classes: `${node.project ? 'project' : node.dependencyType === 'DIRECT' ? 'direct' : node.dependencyType === 'TRANSITIVE' ? 'transitive' : 'unknown'}${this.layoutMode() === 'NETWORK' ? ' network' : ''}` })),
      ...edges.map(edge => ({ data: { id: edge.id, source: edge.source, target: edge.target, relationship: edge.relationship }, classes: this.layoutMode() === 'NETWORK' ? 'network' : '' })),
    ];
    this.cy.elements().remove();
    this.cy.add(elements);
    this.positionGraph();
    const selected = this.selectedNode();
    if (selected && this.cy.$id(selected.id).length > 0) this.cy.$id(selected.id).select();
    this.highlightMatches();
    this.updateNetworkLabels();
  }

  private positionGraph(): void {
    if (!this.cy) return;
    const nodes = this.cy.nodes().toArray();
    const project = nodes.find(node => node.data('project'));
    if (!project) return;
    if (this.layoutMode() === 'NETWORK') {
      const hasSavedPositions = nodes.some(node => this.networkPositions.has(node.id()));
      nodes.forEach(node => {
        const position = this.networkPositions.get(node.id());
        if (position) node.position(position);
      });
      if (nodes.length > 0 && nodes.every(node => this.networkPositions.has(node.id()))) {
        this.cy.elements().unselect();
        this.cy.fit(undefined, 52);
        return;
      }
      this.cy.layout({ name: 'cose', animate: false, randomize: !hasSavedPositions, fit: false,
        numIter: hasSavedPositions ? 80 : nodes.length > 120 ? 120 : 220,
        nodeRepulsion: 6200, idealEdgeLength: 104, edgeElasticity: 82, nestingFactor: 1.15,
        gravity: 0.12, componentSpacing: 116, padding: 56, initialTemp: 150, coolingFactor: 0.94, minTemp: 1 }).run();
      this.cy.nodes().forEach(node => { this.networkPositions.set(node.id(), node.position()); });
      this.cy.elements().unselect();
      this.cy.fit(undefined, 52);
      return;
    }
    const layers = new Map<number, NodeSingular[]>();
    for (const node of nodes) {
      const level = Number(node.data('level')) || 0;
      const layer = layers.get(level) ?? [];
      layer.push(node);
      layers.set(level, layer);
    }
    for (const [level, layer] of layers) {
      layer.sort((left, right) => {
        const ecosystem = String(left.data('ecosystem')).localeCompare(String(right.data('ecosystem')));
        return ecosystem || String(left.data('searchLabel')).localeCompare(String(right.data('searchLabel')));
      });
      layer.forEach((node, index) => node.position({ x: 132 + level * 276, y: 86 + index * 86 }));
    }
    this.cy.elements().unselect();
    this.cy.fit(undefined, 72);
  }

  private nodesForCanvas(graph: DisplayGraph): DisplayNode[] {
    const visible = graph.nodes;
    const maxNodes = this.layoutMode() === 'NETWORK' ? MAX_NETWORK_NODES : MAX_MAP_NODES;
    if (visible.length <= maxNodes) return visible;
    const root = visible.find(node => node.project);
    const packages = visible.filter(node => !node.project);
    const ranked = packages.slice().sort((left, right) => left.level - right.level || left.label.localeCompare(right.label));
    const rootIds = new Set(root ? [root.id] : []);
    const priority = new Set<string>();
    const query = this.search().trim().toLocaleLowerCase();
    const focused = this.selectedNode()?.id;
    const matches = query
      ? packages.filter(node => `${node.label} ${node.version} ${node.manifestPaths.join(' ')}`.toLocaleLowerCase().includes(query))
      : [];
    if (focused && packages.some(node => node.id === focused)) matches.push(packages.find(node => node.id === focused)!);
    if (matches.length) {
      const visibleIds = new Set(visible.map(node => node.id));
      const incoming = new Map<string, string[]>();
      for (const edge of graph.edges) {
        if (!visibleIds.has(edge.source) || !visibleIds.has(edge.target)) continue;
        const parents = incoming.get(edge.target) ?? [];
        parents.push(edge.source);
        incoming.set(edge.target, parents);
      }
      const pending = matches.map(node => node.id);
      for (const id of pending) priority.add(id);
      for (let cursor = 0; cursor < pending.length; cursor++) {
        for (const parent of incoming.get(pending[cursor]) ?? []) {
          if (rootIds.has(parent) || priority.has(parent)) continue;
          priority.add(parent);
          pending.push(parent);
        }
      }
    }
    const selected = new Set(rootIds);
    const prioritize = ranked.filter(node => priority.has(node.id));
    for (const node of [...prioritize, ...ranked]) {
      if (selected.size >= maxNodes) break;
      selected.add(node.id);
    }
    return visible.filter(node => selected.has(node.id));
  }

  private createFilteredGraph(): DisplayGraph | null {
    const graph = this.displayGraph();
    if (!graph) return null;
    const ecosystemNodes = graph.nodes.filter(node => this.visibleEcosystem(node));
    const ecosystemIds = new Set(ecosystemNodes.map(node => node.id));
    const ecosystemEdges = graph.edges.filter(edge => ecosystemIds.has(edge.source) && ecosystemIds.has(edge.target));
    let allowedIds = new Set(ecosystemNodes.map(node => node.id));

    if (this.viewMode() === 'LOCAL') {
      const anchor = this.selectedNode();
      if (!anchor || anchor.project || !ecosystemIds.has(anchor.id)) return { nodes: [], edges: [] };
      allowedIds = new Set([anchor.id]);
      const projectIds = new Set(ecosystemNodes.filter(node => node.project).map(node => node.id));
      let frontier = new Set([anchor.id]);
      for (let level = 0; level < this.localDepth(); level++) {
        const next = new Set<string>();
        for (const edge of ecosystemEdges) {
          if (projectIds.has(edge.source) || projectIds.has(edge.target)) continue;
          if (this.localDirection() !== 'INCOMING' && frontier.has(edge.source) && !allowedIds.has(edge.target)) next.add(edge.target);
          if (this.localDirection() !== 'OUTGOING' && frontier.has(edge.target) && !allowedIds.has(edge.source)) next.add(edge.source);
        }
        next.forEach(id => allowedIds.add(id));
        frontier = next;
        if (!frontier.size) break;
      }
    }

    const module = this.moduleFilter();
    const type = this.dependencyTypeFilter();
    const scope = this.scopeFilter();
    const anchorId = this.viewMode() === 'LOCAL' ? this.selectedNode()?.id : null;
    const filteredNodes = ecosystemNodes.filter(node => {
      if (!allowedIds.has(node.id)) return false;
      if (node.project) return this.viewMode() === 'OVERVIEW';
      if (anchorId === node.id) return true;
      if (type !== 'ALL' && node.dependencyType !== type) return false;
      if (module !== 'ALL' && this.moduleOf(node) !== module) return false;
      if (scope !== 'ALL' && !node.scopes.includes(scope)) return false;
      return true;
    });
    const filteredIds = new Set(filteredNodes.map(node => node.id));
    // Keep only observed edges whose two endpoints remain visible; never bridge a hidden node.
    const filteredEdges = ecosystemEdges.filter(edge => filteredIds.has(edge.source) && filteredIds.has(edge.target));
    return { nodes: filteredNodes, edges: filteredEdges };
  }

  private moduleOf(node: DisplayNode): string {
    const identity = node.label.endsWith(`@${node.version}`) ? node.label.slice(0, -(node.version.length + 1)) : node.label;
    if (node.ecosystem === 'maven') return identity.includes(':') ? identity.slice(0, identity.lastIndexOf(':')) : 'Maven · sin namespace';
    if (identity.startsWith('@')) return identity.split('/')[0] || 'npm · sin namespace';
    return 'npm · sin scope';
  }

  private toDisplayGraph(graph: RepositoryGraph): DisplayGraph {
    const grouped = new Map<string, DisplayNode>();
    const oldToNew = new Map<string, string>();
    const projectId = 'project:root';
    for (const node of graph.nodes) {
      if (node.project) {
        oldToNew.set(node.id, projectId);
        continue;
      }
      const key = `${node.ecosystem}|${node.label}|${node.version}`;
      let display = grouped.get(key);
      if (!display) {
        display = { ...node, id: `package:${encodeURIComponent(key)}`, occurrences: 0, manifestPaths: [], scopes: [], level: 0 };
        grouped.set(key, display);
      }
      display.occurrences++;
      if (node.dependencyType === 'DIRECT') display.dependencyType = 'DIRECT';
      else if (node.dependencyType === 'TRANSITIVE' && display.dependencyType !== 'DIRECT') display.dependencyType = 'TRANSITIVE';
      if (node.manifestPath && !display.manifestPaths.includes(node.manifestPath)) display.manifestPaths.push(node.manifestPath);
      if (node.scope && !display.scopes.includes(node.scope)) display.scopes.push(node.scope);
      display.manifestPath = display.manifestPaths.join(', ');
      display.scope = display.scopes.join(', ');
      oldToNew.set(node.id, display.id);
    }
    const nodes = [...grouped.values()];
    const roots = graph.nodes.filter(node => node.project);
    if (roots.length) {
      nodes.unshift({ id: projectId, label: graph.repositoryName, version: '', ecosystem: 'project', scope: '',
        dependencyType: 'PROJECT', manifestPath: '', project: true, occurrences: 1, manifestPaths: [], scopes: [], level: 0 });
    }
    const edgeMap = new Map<string, GraphEdge>();
    for (const edge of graph.edges) {
      const source = oldToNew.get(edge.source);
      const target = oldToNew.get(edge.target);
      if (!source || !target || source === target) continue;
      const key = `${source}->${target}`;
      if (!edgeMap.has(key)) edgeMap.set(key, { ...edge, id: key, source, target });
    }
    const edges = [...edgeMap.values()];
    const levels = new Map<string, number>();
    const outgoing = new Map<string, string[]>();
    for (const edge of edges) {
      const targets = outgoing.get(edge.source) ?? [];
      targets.push(edge.target);
      outgoing.set(edge.source, targets);
    }
    const root = nodes.find(node => node.project);
    if (root) {
      levels.set(root.id, 0);
      const pending = [root.id];
      for (let cursor = 0; cursor < pending.length; cursor++) {
        const source = pending[cursor];
        const nextLevel = (levels.get(source) ?? 0) + 1;
        for (const target of outgoing.get(source) ?? []) {
          if (levels.has(target)) continue;
          levels.set(target, nextLevel);
          pending.push(target);
        }
      }
    }
    let maxLevel = 1;
    for (const level of levels.values()) maxLevel = Math.max(maxLevel, level);
    const layeredNodes = nodes.map(node => ({ ...node, level: levels.get(node.id) ?? (node.project ? 0 : maxLevel + 1) }));
    return { nodes: layeredNodes, edges };
  }

  private scheduleHighlightMatches(): void {
    if (this.highlightFrame) cancelAnimationFrame(this.highlightFrame);
    this.highlightFrame = requestAnimationFrame(() => {
      this.highlightFrame = 0;
      if (this.renderLimited()) this.draw();
      else this.highlightMatches();
      this.centerSearchMatch();
    });
  }

  private centerSearchMatch(): void {
    const query = this.search().trim().toLocaleLowerCase();
    if (!query || !this.cy) return;
    const nodes = this.filteredGraph()?.nodes.filter(node => !node.project && `${node.label} ${node.version} ${node.manifestPaths.join(' ')}`.toLocaleLowerCase().includes(query)) ?? [];
    if (!nodes.length) return;
    const exact = nodes.find(node => node.label.toLocaleLowerCase() === query || node.label.toLocaleLowerCase().startsWith(`${query}@`));
    const target = this.cy.$id((exact ?? nodes[0]).id);
    if (target.length) this.cy.center(target);
  }

  private highlightMatches(): void {
    if (!this.cy) return;
    const query = this.search().trim().toLocaleLowerCase();
    const contextId = this.layoutMode() === 'NETWORK' ? (this.hoveredNodeId ?? this.selectedNode()?.id ?? null) : null;
    const matched = new Set<string>();
    const incoming = new Map<string, string[]>();
    this.cy.edges().forEach(edge => {
      const parents = incoming.get(edge.target().id()) ?? [];
      parents.push(edge.source().id());
      incoming.set(edge.target().id(), parents);
    });
    this.cy.nodes().forEach(node => {
      if (node.data('project') || !query || String(node.data('searchLabel') ?? node.data('label')).toLocaleLowerCase().includes(query)) matched.add(node.id());
    });
    const active = new Set(matched);
    const pending = [...matched].filter(id => !this.cy?.$id(id).data('project'));
    for (let cursor = 0; cursor < pending.length; cursor++) {
      const target = pending[cursor];
      for (const source of incoming.get(target) ?? []) {
        if (!active.has(source)) { active.add(source); pending.push(source); }
      }
    }
    if (!query && contextId) {
      this.cy.$id(contextId).closedNeighborhood().nodes().forEach(node => { active.add(node.id()); });
    }
    this.cy.nodes().forEach(node => {
      node.toggleClass('dimmed', (!!query || !!contextId) && !active.has(node.id()));
    });
    this.cy.edges().forEach(edge => {
      const inRoute = active.has(edge.source().id()) && active.has(edge.target().id());
      const inNeighborhood = !!contextId && (edge.source().id() === contextId || edge.target().id() === contextId);
      edge.toggleClass('path-active', (!!query && inRoute) || inNeighborhood);
      edge.toggleClass('dimmed', (!!query || !!contextId) && !(!!query && inRoute) && !inNeighborhood);
    });
    this.updateNetworkLabels();
  }

  private updateNetworkLabels(): void {
    if (!this.cy || this.layoutMode() !== 'NETWORK') return;
    const zoom = this.cy.zoom();
    const smallGraph = this.filteredPackageCount() <= 36;
    const fewDirect = this.directCount() <= 50;
    const query = this.search().trim().toLocaleLowerCase();
    const contextId = this.hoveredNodeId ?? this.selectedNode()?.id ?? null;
    const neighbors = contextId ? new Set(this.cy.$id(contextId).closedNeighborhood().nodes().map(node => node.id())) : new Set<string>();
    this.cy.nodes().forEach(node => {
      const matches = !!query && String(node.data('searchLabel') ?? '').toLocaleLowerCase().includes(query);
      const visible = node.data('project') || smallGraph || node.selected() || matches || neighbors.has(node.id())
        || (node.data('dependencyType') === 'DIRECT' && (fewDirect || zoom >= 1.05))
        || (node.data('dependencyType') === 'TRANSITIVE' && zoom >= 1.35);
      node.toggleClass('label-visible', visible);
    });
  }

  private networkNodeSize(node: DisplayNode, degree: number): number {
    if (node.project) return 26;
    const base = node.dependencyType === 'DIRECT' ? 12 : 9;
    return Math.min(23, Math.round(base + Math.sqrt(degree) * 3));
  }

  private visibleEcosystem(node: GraphNode): boolean {
    return node.project || this.ecosystem() === 'ALL' || node.ecosystem === this.ecosystem();
  }
  private visualLabel(node: DisplayNode): string {
    if (node.project) return node.label;
    const withoutVersion = node.label.endsWith(`@${node.version}`) ? node.label.slice(0, -(node.version.length + 1)) : node.label;
    const separator = node.ecosystem === 'maven' ? withoutVersion.lastIndexOf(':') : -1;
    const packageName = separator >= 0 ? withoutVersion.slice(separator + 1) : withoutVersion;
    return `${packageName}\n${node.version}`;
  }
}
