import { HttpClient, HttpErrorResponse, HttpEvent } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, Observable, of, switchMap, tap, throwError } from 'rxjs';

export interface Account { id: string; email: string; displayName: string; emailVerified: boolean; accountType:'OWNER'|'MEMBER'; admissionStatus:'PENDING_REVIEW'|'APPROVED'|'REJECTED'; platformAdmin:boolean; }
export interface Organization { id: string; name: string; }
export type WorkspaceTheme = 'SYSTEM' | 'LIGHT' | 'DARK';
export type WorkspaceLanguage = 'ES' | 'EN';
export interface WorkspaceSettings { organizationId: string; organizationName: string; theme: WorkspaceTheme; language: WorkspaceLanguage; canManage: boolean; }
interface CsrfToken { headerName: string; token: string; }

@Injectable({ providedIn: 'root' })
export class SessionApiService {
  private readonly http = inject(HttpClient);
  readonly account = signal<Account | null>(null);

  restore(): Observable<Account | null> {
    return this.http.get<Account>('/api/auth/me').pipe(
      tap(account => this.account.set(account)),
      catchError((error: HttpErrorResponse) => {
        this.account.set(null);
        return error.status === 401 ? of(null) : throwError(() => error);
      }),
    );
  }

  authenticate(mode: 'login' | 'register', body: { email: string; password: string; displayName?: string }): Observable<Account> {
    return this.post<Account>(`/api/auth/${mode}`, body).pipe(tap(account => this.account.set(account)));
  }

  logout(): Observable<void> {
    return this.post<void>('/api/auth/logout', {}).pipe(tap(() => this.account.set(null)));
  }

  get<T>(path: string): Observable<T> {
    return this.http.get<T>(path).pipe(catchError(error => this.handleFailure(error)));
  }

  post<T>(path: string, body: unknown, extraHeaders: Record<string, string> = {}): Observable<T> {
    // Fetch a fresh masked token because authentication/logout rotates the server token.
    return this.http.get<CsrfToken>('/api/auth/csrf').pipe(
      switchMap(csrf => this.http.post<T>(path, body, { headers: { [csrf.headerName]: csrf.token, ...extraHeaders } })),
      catchError(error => this.handleFailure(error)),
    );
  }

  put<T>(path: string, body: unknown): Observable<T> {
    return this.http.get<CsrfToken>('/api/auth/csrf').pipe(
      switchMap(csrf => this.http.put<T>(path, body, { headers: { [csrf.headerName]: csrf.token } })),
      catchError(error => this.handleFailure(error)),
    );
  }

  delete<T>(path: string): Observable<T> {
    return this.http.get<CsrfToken>('/api/auth/csrf').pipe(
      switchMap(csrf => this.http.delete<T>(path, { headers: { [csrf.headerName]: csrf.token } })),
      catchError(error => this.handleFailure(error)),
    );
  }

  upload<T>(path: string, body: FormData): Observable<HttpEvent<T>> {
    return this.http.get<CsrfToken>('/api/auth/csrf').pipe(
      switchMap(csrf => this.http.post<T>(path, body, {
        headers: { [csrf.headerName]: csrf.token }, observe: 'events', reportProgress: true,
      })),
      catchError(error => this.handleFailure(error)),
    );
  }

  private handleFailure(error: HttpErrorResponse): Observable<never> {
    if (error.status === 401) this.account.set(null);
    return throwError(() => error);
  }
}

export function accessError(error: HttpErrorResponse): string {
  if (error.status === 413) return 'La carpeta supera el tamaño máximo permitido para subirla.';
  if (typeof error.error?.detail === 'string') return error.error.detail;
  if (error.status === 0) return 'No se pudo conectar con el servidor. Inténtalo de nuevo.';
  if (error.status >= 500) return 'El servidor no pudo completar la operación. Inténtalo de nuevo en unos momentos.';
  if (error.status === 429) return 'Has realizado demasiados intentos. Espera un minuto e inténtalo de nuevo.';
  if (error.status === 403) return typeof error.error?.detail === 'string'
    ? error.error.detail
    : 'Tu cuenta no tiene permiso para esta acción en la organización seleccionada.';
  if (error.status === 401) return 'La sesión ha expirado o las credenciales son incorrectas.';
  return 'Revisa los datos e inténtalo de nuevo.';
}
