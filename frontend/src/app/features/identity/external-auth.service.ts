import { inject, Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { SessionApiService } from '../../core/session-api.service';
import { parseExternalAuthResult } from './external-auth-result';

export interface IdentityProvider { id: 'google' | 'github'; name: string; available: boolean; }
export interface IdentityConnections { linkedProviders: string[]; passwordAvailable: boolean; recentAuthentication: boolean; }
interface AuthorizationUrl { authorizationUrl: string; }

@Injectable({ providedIn: 'root' })
export class ExternalAuthService {
  private readonly api = inject(SessionApiService);
  readonly feedback = signal(parseExternalAuthResult(window.location.hash));

  constructor() {
    if (this.feedback()) window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search);
  }

  providers(): Observable<IdentityProvider[]> { return this.api.get<IdentityProvider[]>('/api/auth/providers'); }
  connections(): Observable<IdentityConnections> { return this.api.get<IdentityConnections>('/api/auth/providers/connections'); }
  start(provider: string, action: 'login' | 'link'): Observable<AuthorizationUrl> {
    return this.api.post<AuthorizationUrl>(`/api/auth/providers/${encodeURIComponent(provider)}/${action}`, {});
  }
  unlink(provider: string): Observable<void> { return this.api.post<void>(`/api/auth/providers/${encodeURIComponent(provider)}/unlink`, {}); }

  navigate(url: string): void {
    const target = new URL(url);
    const localHttp = target.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(target.hostname);
    if (target.protocol !== 'https:' && !localHttp) throw new Error('Invalid provider authorization URL');
    window.location.assign(target.href);
  }

  unlinked(): void {
    this.api.account.set(null);
    this.feedback.set(parseExternalAuthResult('#external-auth=unlinked'));
  }
}
