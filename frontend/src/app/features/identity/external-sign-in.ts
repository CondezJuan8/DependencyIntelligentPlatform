import { Component, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { accessError } from '../../core/session-api.service';
import { ExternalAuthService, IdentityProvider } from './external-auth.service';

@Component({
  selector: 'app-external-sign-in',
  imports: [MatButtonModule],
  template: `
    @if (providers().length) {
      <div class="providers" aria-label="Otros métodos de acceso">
        @for (provider of providers(); track provider.id) {
          <button mat-stroked-button type="button" (click)="start(provider)" [disabled]="pending()">Continuar con {{ provider.name }}</button>
        }
      </div>
    }
    @if (error()) {
      <p role="alert">{{ error() }}</p>
      <button mat-button type="button" (click)="load()" [disabled]="pending()">Reintentar</button>
    }
  `,
  styles: `.providers { display: grid; gap: 12px; margin: 24px 0; } [role="alert"] { color: var(--di-critical); }`,
})
export class ExternalSignIn implements OnInit {
  private readonly external = inject(ExternalAuthService);
  readonly providers = signal<IdentityProvider[]>([]);
  readonly pending = signal(false);
  readonly error = signal('');

  ngOnInit(): void { this.load(); }
  load(): void {
    this.error.set('');
    this.external.providers().subscribe({
      next: providers => this.providers.set(providers.filter(provider => provider.available)),
      error: error => this.error.set(accessError(error)),
    });
  }
  start(provider: IdentityProvider): void {
    if (this.pending()) return;
    this.pending.set(true);
    this.error.set('');
    this.external.start(provider.id, 'login').pipe(finalize(() => this.pending.set(false))).subscribe({
      next: result => this.external.navigate(result.authorizationUrl),
      error: error => this.error.set(accessError(error)),
    });
  }
}
