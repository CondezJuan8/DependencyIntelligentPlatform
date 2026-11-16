import { Component,inject,signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { SessionApiService,accessError } from '../../core/session-api.service';
import { EmailVerification } from './email-verification';
import { DiIcon } from '../../shared/di-icon';
@Component({selector:'app-admission-panel',imports:[MatButtonModule,EmailVerification,DiIcon],template:`<section><span class="admission-icon"><app-di-icon name="shield"/></span><h1>{{api.account()?.emailVerified?'Solicitud en revisión':'Confirma tu correo'}}</h1><p>{{api.account()?.displayName}}</p><p>{{api.account()?.email}}</p>@if(!api.account()?.emailVerified){<p>Verifica tu correo para continuar con la admisión de tu cuenta.</p><app-email-verification/>}@else if(api.account()?.admissionStatus==='REJECTED'){<p>Tu solicitud no ha sido admitida. Contacta al administrador de la plataforma.</p>}@else{<p>El administrador de la plataforma debe aprobar tu cuenta de responsable antes de que puedas crear organizaciones o acceder a repositorios.</p>}<div class="actions"><button mat-flat-button (click)="refresh()" [disabled]="pending()">Actualizar estado</button><button mat-button (click)="logout()" [disabled]="pending()">Cerrar sesión</button></div>@if(error()){<p role="alert">{{error()}}</p>}</section>`,styles:`section{font-size:14px}h1{font-size:26px;font-weight:600;letter-spacing:-.4px;margin:18px 0 12px}p{line-height:1.7;color:var(--di-text-secondary);overflow-wrap:anywhere}.admission-icon{display:grid;place-items:center;width:48px;height:48px;border-radius:12px;background:var(--di-info-background);color:var(--di-link)}.actions{display:flex;gap:8px;margin-top:20px;flex-wrap:wrap}[role=alert]{color:var(--di-critical-text)}`})
export class AdmissionPanel{
 readonly api=inject(SessionApiService);readonly pending=signal(false);readonly error=signal('');
 refresh():void{if(this.pending())return;this.pending.set(true);this.error.set('');this.api.restore().pipe(finalize(()=>this.pending.set(false))).subscribe({error:error=>this.error.set(accessError(error))});}
 logout():void{if(this.pending())return;this.pending.set(true);this.api.logout().pipe(finalize(()=>this.pending.set(false))).subscribe({error:error=>this.error.set(accessError(error))});}
}
