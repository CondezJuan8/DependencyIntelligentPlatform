import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { accessError, SessionApiService } from '../../core/session-api.service';

@Component({
  selector: 'app-email-verification',
  imports: [MatButtonModule],
  template: `
    @if (api.account(); as account) {
      @if (!account.emailVerified) {
        <section aria-label="Verificación de correo">
          <p>Verifica tu correo {{ account.email }}.</p>
          <button mat-stroked-button type="button" (click)="request()" [disabled]="pending()">{{ pending() ? 'Enviando…' : 'Enviar enlace de verificación' }}</button>
          @if (message()) { <p role="status">{{ message() }}</p> }
          @if (error()) { <p role="alert">{{ error() }}</p> }
        </section>
      }
    }
  `,
  styles: `section { margin-top: 24px; padding: 20px; border: 1px solid #ccd8ec; border-radius: 12px; background: #edf3ff; } p { margin-top: 0; line-height: 1.5; } [role="alert"] { color: #a22720; }`,
})
export class EmailVerification {
  readonly api = inject(SessionApiService);
  readonly pending = signal(false);
  readonly message = signal('');
  readonly error = signal('');

  request(): void {
    if (this.pending()) return;
    this.pending.set(true);
    this.error.set('');
    this.message.set('');
    this.api.post<void>('/api/auth/email-verification/request', {}).pipe(finalize(() => this.pending.set(false))).subscribe({
      next: () => this.message.set('Revisa tu bandeja de entrada. Si lo acabas de solicitar, espera antes de reenviarlo.'),
      error: error => this.error.set(accessError(error)),
    });
  }
}
