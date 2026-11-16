import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { finalize, forkJoin } from 'rxjs';
import { accessError, SessionApiService } from '../../core/session-api.service';
import { ExternalAuthService, IdentityConnections, IdentityProvider } from './external-auth.service';

@Component({
  selector: 'app-connected-identities',
  imports: [MatButtonModule],
  templateUrl: './connected-identities.html',
  styleUrl: './connected-identities.scss',
})
export class ConnectedIdentities implements OnInit {
  readonly api = inject(SessionApiService);
  private readonly external = inject(ExternalAuthService);
  readonly providers = signal<IdentityProvider[]>([]);
  readonly connections = signal<IdentityConnections | null>(null);
  readonly loading = signal(true);
  readonly pending = signal(false);
  readonly error = signal('');
  readonly confirmation = signal<IdentityProvider | null>(null);
  readonly visibleProviders = computed(() => this.providers().filter(provider => provider.available || this.linked(provider)));

  ngOnInit(): void { this.load(); }
  load(): void {
    this.loading.set(true);
    this.error.set('');
    forkJoin({ providers: this.external.providers(), connections: this.external.connections() })
      .pipe(finalize(() => this.loading.set(false))).subscribe({
        next: result => { this.providers.set(result.providers); this.connections.set(result.connections); },
        error: error => this.error.set(accessError(error)),
      });
  }

  linked(provider: IdentityProvider): boolean { return this.connections()?.linkedProviders.includes(provider.id) ?? false; }
  removable(provider: IdentityProvider): boolean {
    return !!this.connections()?.passwordAvailable || this.providers().some(other => other.id !== provider.id && other.available && this.linked(other));
  }
  link(provider: IdentityProvider): void {
    if (this.pending()) return;
    this.pending.set(true);
    this.error.set('');
    this.external.start(provider.id, 'link').pipe(finalize(() => this.pending.set(false))).subscribe({
      next: result => this.external.navigate(result.authorizationUrl),
      error: error => this.error.set(accessError(error)),
    });
  }
  unlink(): void {
    const provider = this.confirmation();
    if (!provider || this.pending()) return;
    this.pending.set(true);
    this.error.set('');
    this.external.unlink(provider.id).pipe(finalize(() => this.pending.set(false))).subscribe({
      next: () => this.external.unlinked(),
      error: error => this.error.set(accessError(error)),
    });
  }
}
