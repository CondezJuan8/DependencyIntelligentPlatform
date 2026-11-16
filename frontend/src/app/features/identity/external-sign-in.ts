import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { accessError } from '../../core/session-api.service';
import { ExternalAuthService, IdentityProvider } from './external-auth.service';

@Component({
  selector: 'app-external-sign-in',
  imports: [MatButtonModule],
  template: `
    @if (providers().length && !providers().every(unavailable)) {
      <div class="divider"><span>o continúa con</span></div>
      <div class="providers" aria-label="Otros métodos de acceso">
        @for (provider of providers(); track provider.id) {
          <button
            mat-stroked-button
            type="button"
            (click)="start(provider)"
            [disabled]="pending() || !provider.available"
            [attr.title]="
              provider.available
                ? 'Continuar con ' + provider.name
                : provider.name + ' no está disponible por ahora'
            "
          >
            @if (provider.id === 'github') {
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path
                  fill="currentColor"
                  d="M12 2a10 10 0 0 0-3.16 19.49c.5.1.68-.21.68-.48v-1.7c-2.78.6-3.37-1.18-3.37-1.18-.45-1.16-1.11-1.47-1.11-1.47-.91-.62.07-.61.07-.61 1 .07 1.53 1.03 1.53 1.03.89 1.53 2.34 1.09 2.91.83.09-.65.35-1.09.64-1.34-2.22-.25-4.56-1.11-4.56-4.94 0-1.09.39-1.99 1.03-2.69-.1-.25-.45-1.27.1-2.65 0 0 .84-.27 2.75 1.03a9.58 9.58 0 0 1 5 0c1.91-1.3 2.75-1.03 2.75-1.03.55 1.38.2 2.4.1 2.65.64.7 1.03 1.6 1.03 2.69 0 3.84-2.34 4.69-4.57 4.94.36.31.68.92.68 1.85v2.59c0 .27.18.59.69.48A10 10 0 0 0 12 2Z"
                />
              </svg>
            } @else {
              <span class="google" aria-hidden="true">G</span>
            }
            {{ provider.name }}
          </button>
        }
      </div>
      @if (providers().every(unavailable)) {
        <p class="provider-note">Google y GitHub no están disponibles por ahora.</p>
      }
    }
    @if (error()) {
      <p role="alert">{{ error() }}</p>
      <button mat-button type="button" (click)="load()" [disabled]="pending()">Reintentar</button>
    }
  `,
  styles: `
    .divider {
      display: flex;
      align-items: center;
      gap: 16px;
      color: var(--di-text-subtle);
      font-size: 11px;
      margin: 24px 0 18px;
    }
    .divider::before,
    .divider::after {
      content: '';
      height: 1px;
      flex: 1;
      background: var(--di-border);
    }
    .providers {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 14px;
    }
    .providers button {
      border-color: var(--di-border-soft);
      border-radius: 7px;
      height: 43px;
      font-size: 13px;
      letter-spacing: 0;
    }
    .providers svg {
      width: 19px;
      height: 19px;
      vertical-align: middle;
      margin-right: 9px;
    }
    .google {
      font-size: 21px;
      font-weight: 750;
      margin-right: 10px;
      color: #4285f4;
    }
    .provider-note {
      font-size: 10px;
      text-align: center;
      color: var(--di-text-subtle);
      margin: 12px 0 0;
      line-height: 1.5;
    }
    [role='alert'] {
      color: var(--di-critical-text);
    }
  `,
})
export class ExternalSignIn implements OnInit {
  private readonly external = inject(ExternalAuthService);
  readonly providers = signal<IdentityProvider[]>([]);
  readonly pending = signal(false);
  readonly error = signal('');
  readonly unavailable = (provider: IdentityProvider) => !provider.available;

  ngOnInit(): void {
    this.load();
  }
  load(): void {
    this.error.set('');
    this.external.providers().subscribe({
      next: (providers) => this.providers.set(providers),
      error: (error) => this.error.set(accessError(error)),
    });
  }
  start(provider: IdentityProvider): void {
    if (this.pending() || !provider.available) return;
    this.pending.set(true);
    this.error.set('');
    this.external
      .start(provider.id, 'login')
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({
        next: (result) => this.external.navigate(result.authorizationUrl),
        error: (error) => this.error.set(accessError(error)),
      });
  }
}
