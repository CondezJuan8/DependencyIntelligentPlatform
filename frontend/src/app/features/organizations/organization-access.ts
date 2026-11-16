import { Component, Input, OnChanges, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { finalize } from 'rxjs';
import { accessError, Organization, SessionApiService } from '../../core/session-api.service';

type Role = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER';
interface Member { id: string; userId: string; email: string; displayName: string; status: string; roles: Role[]; }
interface Team { id: string; name: string; membershipIds: string[]; grants: { repositoryId: string; permission: string }[]; }
interface Invitation { id: string; email: string; status: string; expiresAt: string; roles: Role[]; }
interface Overview {
  ownMembershipId: string; owner: boolean; members: Member[]; teams: Team[];
  repositories: { id: string; fullName: string }[]; permissions: { code: string; description: string }[];
  roles: { code: Role; permissions: string[] }[]; invitations: Invitation[];
}
@Component({
  selector: 'app-organization-access',
  imports: [DatePipe, FormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule],
  templateUrl: './organization-access.html', styleUrl: './organization-access.scss',
})
export class OrganizationAccess implements OnChanges {
  @Input({ required: true }) organization!: Organization;
  private readonly api = inject(SessionApiService);
  readonly data = signal<Overview | null>(null);
  readonly loading = signal(false);
  readonly pending = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  readonly forbidden = signal(false);
  readonly confirmation = signal<Team | null>(null);
  readonly memberConfirmation = signal<Member | null>(null);
  readonly team = signal<Team | null>(null);
  readonly repository = signal('');
  readonly selectedPermissions = signal<string[]>([]);
  readonly memberRoles: Record<string, Role> = {};
  readonly memberStatuses: Record<string, string> = {};
  invitationEmail = ''; invitationRole: Role = 'MEMBER'; teamName = ''; renamedTeam = '';
  ngOnChanges(): void { this.team.set(null); this.repository.set(''); this.confirmation.set(null); this.memberConfirmation.set(null); this.message.set(''); this.load(); }
  path(): string { return `/api/organizations/${this.organization.id}`; }
  roles(): Role[] { return this.data()?.owner ? ['OWNER', 'ADMIN', 'MEMBER', 'VIEWER'] : ['MEMBER', 'VIEWER']; }
  editable(member: Member): boolean { return !!this.data()?.owner || !member.roles.some(role => role === 'OWNER' || role === 'ADMIN'); }
  load(): void {
    if (this.loading()) return;
    const organizationId = this.organization.id;
    this.loading.set(true); this.error.set(''); this.forbidden.set(false); this.data.set(null);
    this.api.get<Overview>(this.path() + '/access').pipe(finalize(() => this.loading.set(false))).subscribe({
      next: data => {
        if (this.organization.id !== organizationId) return;
        this.data.set(data);
        for (const member of data.members) { this.memberRoles[member.id] = member.roles[0] ?? 'VIEWER'; this.memberStatuses[member.id] = member.status; }
        const selected = this.team();
        if (selected) this.selectTeam(data.teams.find(team => team.id === selected.id) ?? null);
      },
      error: (error: HttpErrorResponse) => { if (error.status === 403) this.forbidden.set(true); else this.error.set(accessError(error)); },
    });
  }
  mutate(suffix: string, body: unknown, message: string, done?: () => void): void {
    if (this.pending() || this.loading()) return;
    this.pending.set(true); this.error.set(''); this.message.set('');
    this.api.post(this.path() + suffix, body).pipe(finalize(() => this.pending.set(false))).subscribe({
      next: () => { done?.(); this.message.set(message); this.load(); }, error: error => this.error.set(accessError(error)),
    });
  }
  invite(): void {
    if (!this.invitationEmail.trim()) return;
    this.mutate('/invitations', { email: this.invitationEmail, role: this.invitationRole }, 'Invitación enviada al correo. En local puedes verla en Mailpit.', () => { this.invitationEmail = ''; });
  }
  revoke(invitation: Invitation): void { this.mutate(`/invitations/${invitation.id}/revoke`, {}, 'Invitación revocada.'); }
  changeMember(): void {
    const member = this.memberConfirmation();
    if (!member) return;
    this.mutate(`/members/${member.id}`, { role: this.memberRoles[member.id], status: this.memberStatuses[member.id] }, 'Membresía actualizada.', () => this.memberConfirmation.set(null));
  }
  createTeam(): void { if (this.teamName.trim()) this.mutate('/teams', { name: this.teamName }, 'Equipo creado.', () => { this.teamName = ''; }); }
  selectTeam(team: Team | null): void { this.team.set(team); this.renamedTeam = team?.name ?? ''; this.selectRepository(this.repository()); }
  rename(): void { const team = this.team(); if (team && this.renamedTeam.trim()) this.mutate(`/teams/${team.id}`, { name: this.renamedTeam }, 'Equipo actualizado.'); }
  deleteTeam(): void { const team = this.confirmation(); if (team) this.mutate(`/teams/${team.id}/delete`, {}, 'Equipo y accesos retirados.', () => { this.confirmation.set(null); this.team.set(null); }); }
  setMember(member: Member, included: boolean): void { const team = this.team(); if (team) this.mutate(`/teams/${team.id}/members/${member.id}`, { included }, 'Miembros del equipo actualizados.'); }
  selectRepository(repository: string): void { this.repository.set(repository); this.selectedPermissions.set(this.team()?.grants.filter(grant => grant.repositoryId === repository).map(grant => grant.permission) ?? []); }
  togglePermission(code: string, checked: boolean): void { this.selectedPermissions.update(items => checked ? [...items, code] : items.filter(item => item !== code)); }
  savePermissions(): void { const team = this.team(); if (team && this.repository()) this.mutate(`/teams/${team.id}/repositories/${this.repository()}/permissions`, { permissions: this.selectedPermissions() }, 'Permisos del repositorio actualizados.'); }
  statusLabel(status: string): string { return ({ ACTIVE: 'Activo', SUSPENDED: 'Suspendido', LEFT: 'Retirado', PENDING: 'Pendiente', ACCEPTED: 'Aceptada', EXPIRED: 'Caducada', REVOKED: 'Revocada' } as Record<string, string>)[status] ?? status; }
  permissionLabel(code: string): string {
    return ({ 'repository.read': 'Consultar repositorio', 'scan.read': 'Consultar análisis', 'scan.create': 'Solicitar análisis', 'document.read': 'Consultar documentos', 'document.manage': 'Gestionar documentos', 'assistant.query': 'Consultar asistente', 'impact.create': 'Solicitar análisis de impacto', 'change.simulate': 'Solicitar pruebas aisladas', 'change.propose': 'Aprobar propuesta de PR', 'report.read': 'Consultar reportes', 'report.export': 'Exportar datos' } as Record<string, string>)[code] ?? code;
  }
}
