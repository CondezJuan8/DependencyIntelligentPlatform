import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { finalize, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SessionApiService, accessError } from '../../core/session-api.service';
import { EmailVerification } from './email-verification';
import { DiIcon } from '../../shared/di-icon';

@Component({
  selector: 'app-admission-panel',
  imports: [MatButtonModule, DiIcon, EmailVerification],
  template: `
    <section aria-labelledby="admission-title">
      @if (api.account(); as account) {
        <h1 id="admission-title">Estado de la cuenta</h1>
        <p class="account-email">{{ account.email }}</p>
        <div class="status-board">
          <h2>Tu solicitud</h2>
          <dl>
            <div><dt><app-di-icon name="mail" />Correo electrónico</dt>
              <dd [class.confirmed]="account.emailVerified">{{ account.emailVerified ? 'Verificado' : 'Por verificar' }}</dd>
            </div>
            <div><dt><app-di-icon name="shield" />Admisión a la plataforma</dt>
              <dd [class.confirmed]="account.admissionStatus === 'APPROVED'" [class.rejected]="account.admissionStatus === 'REJECTED'">
                {{ account.admissionStatus === 'APPROVED' ? 'Aprobada' : account.admissionStatus === 'REJECTED' ? 'Rechazada' : 'Pendiente' }}
              </dd>
            </div>
            <div><dt><app-di-icon name="lock" />Acceso al espacio</dt><dd>Sin habilitar</dd></div>
          </dl>
          @if (account.admissionStatus === 'PENDING_REVIEW') {
            <p>Tu solicitud quedó guardada. Puedes volver a iniciar sesión para consultar la decisión de la administración.</p>
          } @else if (account.admissionStatus === 'REJECTED') {
            <p>Contacta a la administración de la plataforma si necesitas información sobre la decisión.</p>
          } @else if (!account.emailVerified) {
            <p>La verificación del correo sigue pendiente. El acceso se habilitará cuando se confirme.</p>
          }
          <div class="actions">
            <button mat-flat-button (click)="refresh()" [disabled]="pending()">{{ pending() ? 'Consultando?' : 'Volver a consultar' }}</button>
            <button mat-button (click)="logout()" [disabled]="pending()">Cerrar sesión</button>
          </div>
        </div>
        @if (!account.emailVerified && account.admissionStatus !== 'REJECTED') { <app-email-verification /> }
      }
      @if (message()) { <p class="message" role="status">{{ message() }}</p> }
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    </section>
  `,
  styles: `
    h1 { margin: 0; font-size: clamp(26px, 4vw, 36px); line-height: 1.2; }
    h2 { margin: 0 0 8px; font-size: 18px; }
    p { color: var(--di-text-secondary); overflow-wrap: anywhere; }
    .account-email { margin: 8px 0 18px; }
    .status-board { padding: clamp(14px, 3vw, 23px); border: 1px solid var(--di-border); border-radius: var(--di-radius-panel); background: var(--di-surface); }
    dl { margin: 0; }
    dl > div { display: flex; justify-content: space-between; align-items: center; gap: 9px; padding: 11px 0; border-bottom: 1px solid var(--di-border); }
    dt { display: flex; align-items: center; gap: 8px; font-size: 13px; }
    dt app-di-icon { flex: none; width: 16px; height: 16px; color: var(--di-text-secondary); }
    dd { margin: 0; padding: 3px 8px; border-radius: 5px; background: var(--di-warning-background); color: var(--di-warning-text); font-size: 12px; font-weight: 700; }
    dd.confirmed { background: var(--di-success-background); color: var(--di-success-text); }
    dd.rejected { background: var(--di-critical-background); color: var(--di-critical-text); }
    .actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 16px; }
    .message { padding: 10px 12px; border-radius: var(--di-radius-control); background: var(--di-info-background); color: var(--di-info-text); }
    [role=alert]{color:var(--di-critical-text)}
  `,
})
export class AdmissionPanel implements OnInit {
  readonly api = inject(SessionApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly pending = signal(false);
  readonly error = signal('');
  readonly message = signal('');

  ngOnInit(): void {
    timer(15_000, 15_000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      if (this.api.account()?.admissionStatus === 'PENDING_REVIEW') this.refresh(false);
    });
    document.addEventListener('visibilitychange', this.onVisibilityChange);
    this.destroyRef.onDestroy(() => document.removeEventListener('visibilitychange', this.onVisibilityChange));
  }

  private readonly onVisibilityChange = (): void => {
    if (!document.hidden && this.api.account()?.admissionStatus === 'PENDING_REVIEW') this.refresh(false);
  };

  refresh(showFeedback = true): void {
    if (this.pending()) return;
    this.pending.set(true);
    if (showFeedback) { this.error.set(''); this.message.set(''); }
    this.api.restore().pipe(finalize(() => this.pending.set(false))).subscribe({
      next: (account) => {
        if (!showFeedback) return;
        if (account?.admissionStatus === 'REJECTED') {
          this.message.set('La administración rechazó la solicitud.');
        } else if (account?.admissionStatus === 'APPROVED') {
          this.message.set(account.emailVerified ? 'Tu cuenta está habilitada.' : 'La admisión está aprobada. Falta verificar el correo.');
        } else {
          this.message.set('La solicitud sigue pendiente de revisión administrativa.');
        }
      },
      error: (error) => { if (showFeedback) this.error.set(accessError(error)); },
    });
  }

  logout(): void {
    if (this.pending()) return;
    this.pending.set(true);
    this.error.set('');
    this.api.logout().pipe(finalize(() => this.pending.set(false))).subscribe({
      error: (error) => this.error.set(accessError(error)),
    });
  }
}
