import { Component, ElementRef, OnDestroy, OnInit, ViewChild, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import cytoscape, { Core, ElementDefinition, NodeSingular } from 'cytoscape';
import { finalize } from 'rxjs';
import { SessionApiService, accessError } from '../../core/session-api.service';
import { TranslatePipe } from '../../core/translate.pipe';

interface Repository { id: string; organizationId: string; fullName: string; source: string; }
interface GraphSource { scanId: string; ecosystem: string; sha256: string; commitSha: string | null; analyzedAt: string; unresolvedCount: number; }
interface GraphNode { id: string; label: string; version: string; ecosystem: string; scope: string; dependencyType: string; manifestPath: string; project: boolean; }
interface GraphEdge { id: string; source: string; target: string; relationship: string; ecosystem: string; }
interface RepositoryGraph { repositoryId: string; repositoryName: string; sources: GraphSource[]; nodes: GraphNode[]; edges: GraphEdge[]; unresolvedCount: number; }

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
  readonly selectedNode = signal<GraphNode | null>(null);
  readonly search = signal('');
  readonly ecosystem = signal('ALL');
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
    this.selectedNode.set(null);
    this.error.set('');
    if (!selected) { this.draw(); return; }
    this.graphLoading.set(true);
    this.api.get<RepositoryGraph>(`/api/organizations/${selected.organizationId}/repositories/${selected.id}/dependency-graph`)
      .pipe(finalize(() => this.graphLoading.set(false)))
      .subscribe({
        next: graph => { this.graph.set(graph); this.draw(); },
        error: failure => { this.error.set(accessError(failure)); this.draw(); },
      });
  }

  onRepositoryChange(event: Event): void { this.selectRepository((event.target as HTMLSelectElement).value); }
  onSearch(event: Event): void { this.search.set((event.target as HTMLInputElement).value); this.highlightMatches(); }
  onEcosystemChange(event: Event): void { this.ecosystem.set((event.target as HTMLSelectElement).value); this.draw(); }

  selectNode(id: string): void {
    const node = this.graph()?.nodes.find(item => item.id === id) ?? null;
    this.selectedNode.set(node);
    if (node && this.cy) {
      this.cy.$id(id).select();
      this.cy.animate({ center: { eles: this.cy.$id(id) }, duration: 220 });
    }
  }

  zoomIn(): void { this.cy?.zoom({ level: this.cy.zoom() * 1.2, renderedPosition: { x: this.cy.width() / 2, y: this.cy.height() / 2 } }); }
  zoomOut(): void { this.cy?.zoom({ level: this.cy.zoom() / 1.2, renderedPosition: { x: this.cy.width() / 2, y: this.cy.height() / 2 } }); }
  fitGraph(): void { this.cy?.fit(undefined, 44); }

  get packageNodes(): GraphNode[] {
    return (this.graph()?.nodes ?? []).filter(node => !node.project && this.visibleEcosystem(node));
  }
  get packageCount(): number { return this.packageNodes.length; }
  get directCount(): number { return this.packageNodes.filter(node => node.dependencyType === 'DIRECT').length; }
  get transitiveCount(): number { return this.packageNodes.filter(node => node.dependencyType === 'TRANSITIVE').length; }
  get edgeCount(): number { return (this.graph()?.edges ?? []).filter(edge => this.visibleEdge(edge)).length; }
  get adjacentNodes(): GraphNode[] {
    const selected = this.selectedNode();
    if (!selected || selected.project) return [];
    const graph = this.graph();
    if (!graph) return [];
    const ids = new Set(graph.edges.filter(edge => edge.source === selected.id || edge.target === selected.id)
      .map(edge => edge.source === selected.id ? edge.target : edge.source));
    return graph.nodes.filter(node => ids.has(node.id));
  }

  private initializeGraph(): void {
    if (!this.graphHost || this.cy) return;
    this.cy = cytoscape({
      container: this.graphHost.nativeElement,
      elements: [],
      minZoom: 0.15,
      maxZoom: 3,
      wheelSensitivity: 0.18,
      style: [
        { selector: 'node', style: { 'background-color': '#2563EB', 'border-width': 2, 'border-color': '#FFFFFF', color: '#0F172A', label: 'data(label)', 'font-family': 'IBM Plex Sans, sans-serif', 'font-size': 10, 'font-weight': 600, 'text-wrap': 'wrap', 'text-max-width': '145px', width: 30, height: 30, 'text-valign': 'bottom', 'text-margin-y': 8 } },
        { selector: 'node.label-right', style: { 'text-halign': 'left', 'text-valign': 'center', 'text-margin-x': 10, 'text-margin-y': 0 } },
        { selector: 'node.label-left', style: { 'text-halign': 'right', 'text-valign': 'center', 'text-margin-x': -10, 'text-margin-y': 0 } },
        { selector: 'node.label-top', style: { 'text-halign': 'center', 'text-valign': 'top', 'text-margin-x': 0, 'text-margin-y': -10 } },
        { selector: 'node.label-bottom', style: { 'text-halign': 'center', 'text-valign': 'bottom', 'text-margin-x': 0, 'text-margin-y': 10 } },
        { selector: 'node.project', style: { 'background-color': '#101827', 'border-color': '#06B6D4', 'border-width': 4, shape: 'round-rectangle', width: 48, height: 48, color: '#0F172A', 'font-size': 14, 'font-weight': 700 } },
        { selector: 'node[ecosystem = "npm"]:not([project])', style: { 'background-color': '#06B6D4' } },
        { selector: 'node[dependencyType = "TRANSITIVE"]', style: { 'background-color': '#64748B', width: 22, height: 22, 'font-size': 11 } },
        { selector: 'node:selected', style: { 'border-color': '#F97316', 'border-width': 4, 'overlay-opacity': 0.12, 'overlay-color': '#F97316' } },
        { selector: 'edge', style: { width: 2, 'line-color': '#94A3B8', 'target-arrow-color': '#64748B', 'target-arrow-shape': 'triangle', 'curve-style': 'bezier', 'arrow-scale': 1.15 } },
        { selector: 'edge[relationship = "declares"]', style: { 'line-color': '#2563EB', 'target-arrow-color': '#2563EB', 'line-style': 'dashed' } },
        { selector: '.dimmed', style: { opacity: 0.14 } },
      ],
      layout: { name: 'preset', fit: false },
    });
    this.cy.on('tap', 'node', event => this.selectNode(event.target.id()));
    this.cy.on('tap', event => { if (event.target === this.cy) { this.cy?.$('node:selected').unselect(); this.selectedNode.set(null); } });
    this.applyGraphTheme();
    this.themeObserver = new MutationObserver(() => this.applyGraphTheme());
    this.themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    this.graphResizeObserver = new ResizeObserver(entries => {
      const size = entries[0]?.contentRect;
      if (!size || size.width === 0 || size.height === 0 || !this.cy) return;
      this.cy.resize();
      this.draw();
    });
    this.graphResizeObserver.observe(this.graphHost.nativeElement);
    requestAnimationFrame(() => {
      if (!this.cy || !this.graphHost) return;
      this.cy.resize();
      this.draw();
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
      .selector('node').style({ color: text, 'border-color': surface })
      .selector('node.project').style({ color: '#FFFFFF', 'border-color': '#06B6D4' })
      .selector('edge').style({ color: secondary, 'text-background-color': surface, 'line-color': border, 'target-arrow-color': secondary })
      .selector('edge[relationship = "declares"]').style({ 'line-color': '#2563EB', 'target-arrow-color': '#2563EB' })
      .update();
  }

  private draw(): void {
    if (!this.cy || !this.graphHost) return;
    const graph = this.graph();
    if (!graph) { this.cy.elements().remove(); return; }
    const nodes = graph.nodes.filter(node => this.visibleEcosystem(node));
    const nodeIds = new Set(nodes.map(node => node.id));
    const edges = graph.edges.filter(edge => nodeIds.has(edge.source) && nodeIds.has(edge.target));
    const elements: ElementDefinition[] = [
      ...nodes.map(node => ({ data: { id: node.id, label: this.visualLabel(node), searchLabel: node.label, project: node.project, ecosystem: node.ecosystem, dependencyType: node.dependencyType }, classes: node.project ? 'project' : '' })),
      ...edges.map(edge => ({ data: { id: edge.id, source: edge.source, target: edge.target, relationship: edge.relationship } })),
    ];
    this.cy.elements().remove();
    this.cy.add(elements);
    this.positionGraph();
    this.highlightMatches();
  }

  private positionGraph(): void {
    if (!this.cy) return;
    const width = this.cy.width();
    const height = this.cy.height();
    if (!width || !height) return;
    const center = { x: width / 2, y: height / 2 };
    const radiusLimit = Math.min(width, height);
    const directRadius = Math.min(radiusLimit * 0.41, 270);
    const transitiveRadius = Math.min(radiusLimit * 0.48, 330);
    const project = this.cy.nodes('.project');
    project.positions(center);
    const direct = this.cy.nodes('[dependencyType = "DIRECT"]:not(.project)')
      .toArray().sort((left, right) => String(left.data('searchLabel')).localeCompare(String(right.data('searchLabel'))));
    const transitive = this.cy.nodes('[dependencyType = "TRANSITIVE"]')
      .toArray().sort((left, right) => String(left.data('searchLabel')).localeCompare(String(right.data('searchLabel'))));
    this.placeOnRing(direct, directRadius, center);
    this.placeOnRing(transitive, transitiveRadius, center);
    this.cy.fit(undefined, 72);
  }

  private placeOnRing(nodes: NodeSingular[], radius: number, center: { x: number; y: number }): void {
    if (!nodes.length || !this.cy) return;
    nodes.forEach((node, index) => {
      const angle = -Math.PI / 2 + (Math.PI * 2 * index) / nodes.length;
      const x = center.x + Math.cos(angle) * radius;
      const y = center.y + Math.sin(angle) * radius;
      node.position({ x, y });
      node.removeClass('label-left label-right label-top label-bottom');
      const horizontal = Math.abs(Math.cos(angle)) > Math.abs(Math.sin(angle));
      node.addClass(horizontal ? (Math.cos(angle) > 0 ? 'label-right' : 'label-left') : (Math.sin(angle) > 0 ? 'label-bottom' : 'label-top'));
    });
  }

  private highlightMatches(): void {
    if (!this.cy) return;
    const query = this.search().trim().toLocaleLowerCase();
    this.cy.nodes().forEach(node => {
      const matches = !query || String(node.data('searchLabel') ?? node.data('label')).toLocaleLowerCase().includes(query);
      node.toggleClass('dimmed', !matches && !node.data('project'));
    });
    this.cy.edges().forEach(edge => {
      const dim = edge.source().hasClass('dimmed') && edge.target().hasClass('dimmed');
      edge.toggleClass('dimmed', dim);
    });
  }

  private visibleEcosystem(node: GraphNode): boolean {
    return this.ecosystem() === 'ALL' || node.ecosystem === this.ecosystem();
  }
  private visualLabel(node: GraphNode): string {
    if (node.ecosystem !== 'maven') return node.label;
    const separator = node.label.lastIndexOf(':');
    return separator >= 0 ? node.label.slice(separator + 1) : node.label;
  }
  private visibleEdge(edge: GraphEdge): boolean {
    const graph = this.graph();
    return !!graph && graph.edges.some(item => item.id === edge.id && this.visibleEcosystem(graph.nodes.find(node => node.id === item.target)!));
  }
}
