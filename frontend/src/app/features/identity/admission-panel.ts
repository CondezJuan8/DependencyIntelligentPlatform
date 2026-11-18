import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { finalize, timer } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SessionApiService, accessError } from '../../core/session-api.service';
import { DiIcon } from '../../shared/di-icon';

@Component({
  selector: 'app-admission-panel',
  imports: [MatButtonModule, DiIcon],
  template: `
    <section aria-labelledby="admission-title">
      <span class="admission-icon"><app-di-icon name="shield" /></span>
      @if (api.account(); as account) {
        <h1 id="admission-title">{{ account.admissionStatus === 'PENDING_REVIEW' ? 'Esperando confirmación del propietario' : 'Estado de tu cuenta' }}</h1>
        <p><strong>{{ account.displayName }}</strong></p>
        <p>{{ account.email }}</p>
        <p class="account-status">{{ account.emailVerified ? 'Correo confirmado' : 'Correo pendiente de confirmación administrativa' }}</p>
        @if (account.admissionStatus === 'PENDING_REVIEW') {
          <p>Tu registro quedó guardado. El propietario de la plataforma confirmará el correo y aprobará tu cuenta.</p>
          <p>Puedes cerrar sesión o salir de esta página. Cuando vuelvas, inicia sesión para consultar el estado; tu solicitud seguirá guardada.</p>
        } @else if (account.admissionStatus === 'REJECTED') {
          <p>Tu solicitud no fue aprobada. Contacta al propietario de la plataforma si necesitas más información.</p>
        } @else if (!account.emailVerified) {
          <p>La administración debe confirmar tu correo antes de habilitar el espacio de trabajo.</p>
        }
      }
      <div class="actions">
        <button mat-flat-button (click)="refresh()" [disabled]="pending()">{{ pending() ? 'Consultando…' : 'Comprobar aprobación' }}</button>
        <button mat-button (click)="logout()" [disabled]="pending()">Cerrar sesión</button>
      </div>
      @if (message()) { <p class="message" role="status">{{ message() }}</p> }
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    </section>
  `,
  styles: `
    section{font-size:14px}h1{font-size:26px;font-weight:600;letter-spacing:-.4px;margin:18px 0 12px}
    p{line-height:1.7;color:var(--di-text-secondary);overflow-wrap:anywhere}.account-status{font-size:12px;color:var(--di-text-subtle)}
    .admission-icon{display:grid;place-items:center;width:48px;height:48px;border-radius:12px;background:var(--di-info-background);color:var(--di-link)}
    .actions{display:flex;gap:8px;margin-top:20px;flex-wrap:wrap}.message{padding:10px 12px;border-radius:8px;background:var(--di-info-background);color:var(--di-info-text)}
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
        this.message.set(account?.admissionStatus === 'APPROVED'
          ? 'Tu cuenta ya fue aprobada; abriendo el espacio de trabajo.'
          : 'La solicitud sigue pendiente. Vuelve a comprobarla después de la revisión administrativa.');
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
