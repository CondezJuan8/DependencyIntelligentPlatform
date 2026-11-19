import { Component, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { finalize } from 'rxjs';
import { Organization, SessionApiService, accessError } from '../../core/session-api.service';
import { TranslatePipe } from '../../core/translate.pipe';
interface Repo {id:string;organizationId:string;organizationName:string;fullName:string;source:string;}
interface Inventory {scanId:string;source:string;sourceSha256:string;commitSha:string|null;analyzedAt:string;manifestCount:number;unresolvedCount:number;dependencies:{manifestPath:string;coordinates:{namespace:string|null;name:string;version:string};scope:string;dependencyType:string}[];}
@Component({selector:'app-inventory-workspace',imports:[TranslatePipe,DatePipe],template:`
<header class="page-heading"><div><p class="eyebrow">INVENTARIO</p><h1>Dependencias detectadas</h1><p>Resultados de manifiestos Maven y lockfiles npm.</p></div></header>
@if(error()){<p class="notice error" role="alert">{{error()}}</p>}
<section class="repo-section"><label>Repositorio <select [value]="repo()?.id ?? ''" (change)="select($event)">@for(r of repositories();track r.id){<option [value]="r.id">{{r.fullName}} · {{r.source}}</option>}</select></label><div class="actions"><button class="primary" [disabled]="busy() || !repo()" (click)="run('maven')">{{busy()?'Analizando…':'Analizar Maven'}}</button><button class="primary" [disabled]="busy() || !repo()" (click)="run('npm')">{{busy()?'Analizando…':'Analizar npm'}}</button></div></section>
@if(inventory();as i){<section class="repo-section"><h2>{{i.dependencies.length}} dependencias ({{directCount}} directas, {{transitiveCount}} transitivas)</h2><p>{{i.manifestCount}} manifiesto(s) · {{i.unresolvedCount}} sin resolver · fuente {{i.source}} · {{i.analyzedAt | date:'short'}}</p><p>SHA-256: {{i.sourceSha256}}</p>@for(d of i.dependencies;track d.manifestPath+d.coordinates.name+d.coordinates.version){<article class="repo-row"><div><h3>{{d.coordinates.namespace ? d.coordinates.namespace+':' : ''}}{{d.coordinates.name}}</h3><p>{{d.coordinates.version}} · {{d.dependencyType}} · {{d.scope}} · {{d.manifestPath}}</p></div></article>}</section>}
`,styles:[`.page-heading{margin-bottom:1.5rem}.repo-section{display:grid;gap:1rem;background:var(--di-surface);border:1px solid var(--di-border);border-radius:16px;padding:1.25rem;margin-bottom:1rem}.repo-row{padding:.8rem;border-bottom:1px solid var(--di-border)}select{margin:.5rem;padding:.6rem}button{width:max-content}`]})
export class InventoryWorkspace implements OnInit{
private api=inject(SessionApiService);repositories=signal<Repo[]>([]);repo=signal<Repo|null>(null);inventory=signal<Inventory|null>(null);error=signal('');busy=signal(false);
get directCount(){return this.inventory()?.dependencies.filter(d=>d.dependencyType==='DIRECT').length??0;} get transitiveCount(){return this.inventory()?.dependencies.filter(d=>d.dependencyType==='TRANSITIVE').length??0;}
ngOnInit(){this.api.get<Repo[]>('/api/account/workspace').subscribe({next:r=>{this.repositories.set(r);this.repo.set(r[0]??null);if(r[0])this.load(r[0]);},error:e=>this.error.set(accessError(e))});}
select(e:Event){const r=this.repositories().find(x=>x.id===(e.target as HTMLSelectElement).value)??null;this.repo.set(r);this.inventory.set(null);if(r)this.load(r);}
load(r:Repo){this.api.get<Inventory|null>(`/api/organizations/${r.organizationId}/repositories/${r.id}/maven-analysis`).subscribe({next:i=>i?this.inventory.set(i):this.api.get<Inventory|null>(`/api/organizations/${r.organizationId}/repositories/${r.id}/npm-analysis`).subscribe({next:n=>this.inventory.set(n),error:e=>this.error.set(accessError(e))}),error:e=>this.error.set(accessError(e))});}
run(ecosystem:'npm'|'maven'){const r=this.repo();if(!r)return;this.busy.set(true);this.error.set('');this.api.post<Inventory>(`/api/organizations/${r.organizationId}/repositories/${r.id}/${ecosystem}-analysis`,{}).pipe(finalize(()=>this.busy.set(false))).subscribe({next:i=>this.inventory.set(i),error:e=>this.error.set(accessError(e))});}
}
