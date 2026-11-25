import { Component,inject,OnInit,signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { A11yModule } from '@angular/cdk/a11y';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { SessionApiService,accessError } from '../../core/session-api.service';
import { DiIcon } from '../../shared/di-icon';
interface User{id:string;email:string;displayName:string;accountType:string;admissionStatus:string;emailVerified:boolean;active:boolean;administrator:boolean}
interface Item{id:string;organizationId:string;name:string;organizationName:string;enabled:boolean}
interface Membership{id:string;organizationId:string;email:string;role:string;status:string}
interface Invitation{id:string;organizationId:string;email:string;status:string;owner:string;team:string;repository:string}
interface Audit{id:string;actorId:string;action:string;resourceType:string;resourceId:string;reason:string;createdAt:string}
interface Overview{statistics:Record<string,number>;users:User[];repositories:Item[];teams:Item[];organizations:Item[];memberships:Membership[];invitations:Invitation[];audit:Audit[]}
interface Action{resource:'USER'|'REPOSITORY'|'TEAM'|'ORGANIZATION'|'MEMBERSHIP';id:string;operation:'APPROVE'|'REJECT'|'ENABLE'|'DISABLE'|'REMOVE';label:string}
@Component({selector:'app-platform-administration',imports:[FormsModule,A11yModule,MatButtonModule,DiIcon],templateUrl:'./platform-administration.html',styleUrl:'./platform-administration.scss'})
export class PlatformAdministration implements OnInit{
 readonly api=inject(SessionApiService);readonly data=signal<Overview|null>(null);readonly loading=signal(false);readonly pending=signal(false);readonly error=signal('');readonly message=signal('');readonly tab=signal('requests');readonly action=signal<Action|null>(null);reason='';query='';
 readonly tabs=[{id:'requests',label:'Solicitudes'},{id:'users',label:'Cuentas'},{id:'repositories',label:'Repositorios'},{id:'teams',label:'Equipos'},{id:'organizations',label:'Organizaciones'},{id:'memberships',label:'Membresías'},{id:'invitations',label:'Invitaciones'},{id:'audit',label:'Auditoría'}];
 readonly metrics=[{key:'owners',label:'Propietarios activos'},{key:'pendingOwners',label:'Solicitudes pendientes'},{key:'members',label:'Miembros activos'},{key:'repositories',label:'Repositorios totales'},{key:'enabledRepositories',label:'Repositorios habilitados'},{key:'teams',label:'Equipos'},{key:'organizations',label:'Organizaciones'},{key:'pendingInvitations',label:'Invitaciones en curso'}];
 ngOnInit():void{this.load();}
 load():void{if(this.loading())return;this.loading.set(true);this.error.set('');this.api.get<Overview>('/api/platform/overview').pipe(finalize(()=>this.loading.set(false))).subscribe({next:data=>this.data.set(data),error:error=>this.error.set(accessError(error))});}
 users():User[]{return this.data()?.users.filter(user=>(this.tab()!=='requests'||user.admissionStatus==='PENDING_REVIEW')&&`${user.displayName} ${user.email}`.toLocaleLowerCase().includes(this.query.trim().toLocaleLowerCase()))??[];}
 items():Item[]{const data=this.data();if(!data)return[];const list=this.tab()==='repositories'?data.repositories:this.tab()==='teams'?data.teams:data.organizations;return list.filter(item=>`${item.name} ${item.organizationName}`.toLocaleLowerCase().includes(this.query.trim().toLocaleLowerCase()));}
 auditEvents(events: Audit[]): Audit[] {
  return events.filter(event => this.matches(this.label(event.action), this.label(event.resourceType), event.reason, event.actorId, event.resourceId));
 }
 memberships(items: Membership[]): Membership[] {
  return items.filter(item => this.matches(item.email, this.label(item.role), this.label(item.status), item.organizationId));
 }
 invitations(items: Invitation[]): Invitation[] {
  return items.filter(item => this.matches(item.email, item.owner, item.team, item.repository, this.label(item.status)));
 }
 private matches(...fields: Array<string | null>): boolean {
  return fields.join(' ').toLocaleLowerCase().includes(this.query.trim().toLocaleLowerCase());
 }
 select(resource:Action['resource'],id:string,operation:Action['operation'],label:string):void{this.reason='';this.error.set('');this.action.set({resource,id,operation,label});}
 toggle(item:Item):void{this.select(this.tab()==='repositories'?'REPOSITORY':this.tab()==='teams'?'TEAM':'ORGANIZATION',item.id,item.enabled?'DISABLE':'ENABLE',item.name);}
 confirm():void{const action=this.action();if(!action||this.pending()||this.reason.trim().length<3)return;this.pending.set(true);this.error.set('');this.api.post('/api/platform/actions',{resource:action.resource,id:action.id,operation:action.operation,reason:this.reason}).pipe(finalize(()=>this.pending.set(false))).subscribe({next:()=>{this.action.set(null);this.message.set('Acción administrativa registrada.');this.load();},error:error=>this.error.set(accessError(error))});}
 label(value:string):string{return ({OWNER:'Responsable',MEMBER:'Miembro',ADMIN:'Administrador de organización',VIEWER:'Lector',PENDING_REVIEW:'En revisión',APPROVED:'Aprobado',REJECTED:'Rechazado',ACTIVE:'Activo',SUSPENDED:'Suspendido',LEFT:'Retirado',PENDING:'Pendiente de aceptación',AWAITING_OWNER:'Pendiente del propietario',ACCEPTED:'Acceso aprobado',REVOKED:'Revocada',EXPIRED:'Caducada',APPROVE:'Aprobar',REJECT:'Rechazar',ENABLE:'Habilitar',DISABLE:'Deshabilitar',REMOVE:'Retirar',USER:'Cuenta',REPOSITORY:'Repositorio',TEAM:'Equipo',ORGANIZATION:'Organización',MEMBERSHIP:'Membresía'} as Record<string,string>)[value]??value;}
 close():void{if(!this.pending())this.action.set(null);}
}
