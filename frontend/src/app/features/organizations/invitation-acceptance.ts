import { Component, inject, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { accessError, SessionApiService } from '../../core/session-api.service';
import { InvitationLinkService } from './invitation-link.service';

@Component({
  selector: 'app-invitation-acceptance',
  imports: [MatButtonModule],
  template: `
    @if (links.invitation()) {
      <section aria-label="Aceptar invitación" [attr.aria-busy]="pending()">
        <h2>Invitación a una organización</h2>
        <p>Para aceptar, inicia sesión con el correo invitado y verifica ese correo. El enlace no se acepta automáticamente.</p>
        @if (api.account(); as account) {
          <p>Cuenta actual: {{ account.email }}</p>
          @if (account.emailVerified) {
            <button mat-flat-button (click)="accept()" [disabled]="pending()">Aceptar invitación</button>
          } @else {
            <p>Solicita el enlace de verificación arriba. Después vuelve a abrir la invitación o actualiza tu cuenta.</p>
            <button mat-stroked-button (click)="refresh()" [disabled]="pending()">Actualizar verificación</button>
          }
        } @else {
          <p>Usa el formulario de acceso. Si inicias sesión con Google/GitHub, vuelve a abrir después el enlace del correo.</p>
        }
        <button mat-button (click)="links.invitation.set(null)" [disabled]="pending()">Cerrar invitación</button>
        @if (error()) { <p role="alert">{{ error() }}</p> }
      </section>
    }
    @if (message()) { <p role="status">{{ message() }}</p> }
  `,
  styles: `section { margin-top: 24px; padding: 24px; border: 1px solid var(--di-border); border-radius: 14px; background: var(--di-info-background); color: var(--di-text); } p { line-height: 1.5; overflow-wrap: anywhere; } button { margin: 4px; } [role="alert"] { color: var(--di-critical-text); } [role="status"] { color: var(--di-success-text); }`,
})
export class InvitationAcceptance {
  readonly api = inject(SessionApiService);
  readonly links = inject(InvitationLinkService);
  readonly accepted = output<void>();
  readonly pending = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  refresh(): void {
    if (this.pending()) return;
    this.pending.set(true); this.error.set('');
    this.api.restore().pipe(finalize(() => this.pending.set(false))).subscribe({ error: error => this.error.set(accessError(error)) });
  }
  accept(): void {
    const link = this.links.invitation();
    if (!link || this.pending()) return;
    this.pending.set(true); this.error.set('');
    this.api.post<void>(`/api/organizations/${link.organization}/invitations/${link.invitation}/accept`, { token: link.token })
      .pipe(finalize(() => this.pending.set(false))).subscribe({
        next: () => { this.links.invitation.set(null); this.message.set('Invitación aceptada. El propietario debe aprobar tu acceso antes de que puedas usar el repositorio.'); this.accepted.emit(); },
        error: error => this.error.set(accessError(error)),
      });
  }
}
