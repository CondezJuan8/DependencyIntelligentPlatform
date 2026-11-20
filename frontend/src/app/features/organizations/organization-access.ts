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
import { A11yModule } from '@angular/cdk/a11y';
import { DiIcon } from '../../shared/di-icon';
import { TranslatePipe } from '../../core/translate.pipe';
import { accessError, Organization, SessionApiService } from '../../core/session-api.service';

type Role = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER';
interface Member {
  id: string;
  userId: string;
  email: string;
  displayName: string;
  status: string;
  roles: Role[];
  directGrants: { repositoryId: string; permission: string }[];
}
interface Team {
  id: string;
  name: string;
  membershipIds: string[];
  grants: { repositoryId: string; permission: string }[];
}
interface Invitation {
 teamId:string;repositoryId:string;
  id: string;
  email: string;
  status: string;
  expiresAt: string;
  roles: Role[];
}
interface Overview {
  ownMembershipId: string;
  owner: boolean;
  members: Member[];
  teams: Team[];
  repositories: { id: string; fullName: string }[];
  permissions: { code: string; description: string }[];
  roles: { code: Role; permissions: string[] }[];
  invitations: Invitation[];
}
@Component({
  selector: 'app-organization-access',
  imports: [
    A11yModule,
    DiIcon,
    TranslatePipe,
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatCheckboxModule,
  ],
  templateUrl: './organization-access.html',
  styleUrl: './organization-access.scss',
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
  readonly revokeConfirmation = signal<Invitation | null>(null);
 readonly approvalConfirmation=signal<Invitation|null>(null);
 readonly inviteRoles:Role[]=['MEMBER','VIEWER'];
 invitationTeamId='';invitationRepositoryId='';
  readonly tab = signal<'members' | 'invitations' | 'teams' | 'permissions'>('members');
  readonly dialog = signal<'invite' | 'team' | null>(null);
  readonly search = signal('');
  readonly statusFilter = signal('ALL');
  readonly page = signal(0);
  readonly team = signal<Team | null>(null);
  readonly repository = signal('');
  readonly selectedPermissions = signal<string[]>([]);
  readonly selectedMemberRepository = signal('');
  readonly selectedMemberPermissions = signal<string[]>([]);
  readonly memberRoles: Record<string, Role> = {};
  readonly memberStatuses: Record<string, string> = {};
  invitationEmail = '';
  invitationRole: Role = 'MEMBER';
  teamName = '';
  renamedTeam = '';
  ngOnChanges(): void {
    this.team.set(null);
    this.repository.set('');
    this.confirmation.set(null);
    this.memberConfirmation.set(null);
    this.message.set('');
    this.load();
  }
  path(): string {
    return `/api/organizations/${this.organization.id}`;
  }
  roles(): Role[] {
    return this.data()?.owner ? ['OWNER', 'ADMIN', 'MEMBER', 'VIEWER'] : ['MEMBER', 'VIEWER'];
  }
  editable(member: Member): boolean {
    return (
      !!this.data()?.owner || !member.roles.some((role) => role === 'OWNER' || role === 'ADMIN')
    );
  }
  load(): void {
    if (this.loading()) return;
    const organizationId = this.organization.id;
    this.loading.set(true);
    this.error.set('');
    this.forbidden.set(false);
    this.data.set(null);
    this.api
      .get<Overview>(this.path() + '/access')
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (data) => {
          if (this.organization.id !== organizationId) return;
          this.data.set(data);
          this.page.set(
            Math.min(this.page(), Math.max(0, Math.ceil(this.filteredMembers().length / 10) - 1)),
          );
          for (const member of data.members) {
            this.memberRoles[member.id] = member.roles[0] ?? 'VIEWER';
            this.memberStatuses[member.id] = member.status;
          }
          const selected = this.team();
          this.selectTeam(
            data.teams.find((team) => team.id === selected?.id) ?? data.teams[0] ?? null,
          );
        },
        error: (error: HttpErrorResponse) => {
          if (error.status === 403) this.forbidden.set(true);
          else this.error.set(accessError(error));
        },
      });
  }
  mutate(suffix: string, body: unknown, message: string, done?: () => void): void {
    if (this.pending() || this.loading()) return;
    this.pending.set(true);
    this.error.set('');
    this.message.set('');
    this.api
      .post(this.path() + suffix, body)
      .pipe(finalize(() => this.pending.set(false)))
      .subscribe({
        next: () => {
          done?.();
          this.message.set(message);
          this.load();
        },
        error: (error) => this.error.set(accessError(error)),
      });
  }
  invite(): void {
    if (!this.invitationEmail.trim() || !this.invitationTeamId || !this.invitationRepositoryId || !this.invitationRepositories().some(repo => repo.id === this.invitationRepositoryId)) return;
    this.mutate(
      '/invitations',
      { email: this.invitationEmail, role: this.invitationRole,teamId:this.invitationTeamId,repositoryId:this.invitationRepositoryId },
      'Invitación enviada. La persona recibirá un enlace para unirse.',
      () => {
        this.invitationEmail = '';
        this.dialog.set(null);
        this.tab.set('invitations');
      },
    );
  }
  revoke(invitation: Invitation): void {
    this.mutate(`/invitations/${invitation.id}/revoke`, {}, 'Invitación revocada.', () =>
      this.revokeConfirmation.set(null),
    );
  }
  changeMember(): void {
    const member = this.memberConfirmation();
    if (!member) return;
    this.mutate(
      `/members/${member.id}`,
      { role: this.memberRoles[member.id], status: this.memberStatuses[member.id] },
      'Membresía actualizada.',
      () => this.memberConfirmation.set(null),
    );
  }
  createTeam(): void {
    if (this.teamName.trim())
      this.mutate('/teams', { name: this.teamName }, 'Equipo creado.', () => {
        this.teamName = '';
        this.dialog.set(null);
      });
  }
  selectTeam(team: Team | null): void {
    this.team.set(team);
    this.renamedTeam = team?.name ?? '';
    this.selectRepository(this.repository() || this.data()?.repositories[0]?.id || '');
    if (team) requestAnimationFrame(() => document.getElementById('team-editor')?.scrollIntoView({ behavior: 'smooth', block: 'start' }));
  }
  rename(): void {
    const team = this.team();
    if (team && this.renamedTeam.trim())
      this.mutate(`/teams/${team.id}`, { name: this.renamedTeam }, 'Equipo actualizado.');
  }
  deleteTeam(): void {
    const team = this.confirmation();
    if (team)
      this.mutate(`/teams/${team.id}/delete`, {}, 'Equipo y accesos retirados.', () => {
        this.confirmation.set(null);
        this.team.set(null);
      });
  }
  setMember(member: Member, included: boolean): void {
    const team = this.team();
    if (team)
      this.mutate(
        `/teams/${team.id}/members/${member.id}`,
        { included },
        'Miembros del equipo actualizados.',
      );
  }
  selectRepository(repository: string): void {
    this.repository.set(repository);
    this.selectedPermissions.set(
      this.team()
        ?.grants.filter((grant) => grant.repositoryId === repository)
        .map((grant) => grant.permission) ?? [],
    );
  }
  togglePermission(code: string, checked: boolean): void {
    this.selectedPermissions.update((items) =>
      checked ? [...items, code] : items.filter((item) => item !== code),
    );
  }
  savePermissions(): void {
    const team = this.team();
    if (team && this.repository())
      this.mutate(
        `/teams/${team.id}/repositories/${this.repository()}/permissions`,
        { permissions: this.selectedPermissions() },
        'Permisos del repositorio actualizados.',
      );
  }
  statusLabel(status: string): string {
    return (
      (
        {
          ACTIVE: 'Activo',
          SUSPENDED: 'Suspendido',
          LEFT: 'Retirado',
          PENDING: 'Pendiente',AWAITING_OWNER:'Pendiente de aprobación',
          ACCEPTED: 'Aceptada',
          EXPIRED: 'Caducada',
          REVOKED: 'Revocada',
        } as Record<string, string>
      )[status] ?? status
    );
  }
  permissionLabel(code: string): string {
    return (
      (
        {
          'organization.manage':'Gestionar organización y membresías','team.manage':'Gestionar equipos','repository.manage':'Gestionar conexiones de repositorios','audit.read':'Consultar auditoría',
 'repository.read': 'Consultar repositorio',
          'scan.read': 'Consultar análisis',
          'scan.create': 'Solicitar análisis',
          'document.read': 'Consultar documentos',
          'document.manage': 'Gestionar documentos',
          'assistant.query': 'Consultar asistente',
          'impact.create': 'Solicitar análisis de impacto',
          'change.simulate': 'Solicitar pruebas aisladas',
          'change.propose': 'Aprobar propuesta de PR',
          'report.read': 'Consultar reportes',
          'report.export': 'Exportar datos',
        } as Record<string, string>
      )[code] ?? 'Permiso adicional'
    );
  }
  roleLabel(role: Role): string {
    return { OWNER: 'Propietario', ADMIN: 'Administrador', MEMBER: 'Miembro', VIEWER: 'Lector' }[
      role
    ];
  }
  initials(name: string): string {
    return name
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part[0])
      .join('')
      .toUpperCase();
  }
  teamsFor(member: Member): string {
    return (
      this.data()
        ?.teams.filter((team) => team.membershipIds.includes(member.id))
        .map((team) => team.name)
        .join(', ') || 'Sin equipo'
    );
  }
  filteredMembers(): Member[] {
    const query = this.search().trim().toLocaleLowerCase();
    return (
      this.data()?.members.filter(
        (member) =>
          (!query ||
            `${member.displayName} ${member.email} ${this.teamsFor(member)}`
              .toLocaleLowerCase()
              .includes(query)) &&
          (this.statusFilter() === 'ALL' || member.status === this.statusFilter()),
      ) ?? []
    );
  }
  visibleMembers(): Member[] {
    return this.filteredMembers().slice(this.page() * 10, (this.page() + 1) * 10);
  }
  setSearch(value: string): void {
    this.search.set(value);
    this.page.set(0);
  }
  setStatus(value: string): void {
    this.statusFilter.set(value);
    this.page.set(0);
  }
  repositoriesFor(team: Team): number {
    return new Set(team.grants.map((grant) => grant.repositoryId)).size;
  }
  selectTeamById(id: string): void {
    this.selectTeam(this.data()?.teams.find((team) => team.id === id) ?? null);
  }
  openDialog(kind: 'invite' | 'team'): void {
    this.error.set('');
    this.dialog.set(kind);
    if (kind === 'invite') {
      const teams = this.data()?.teams ?? [];
      const firstEligible = teams.find(team => team.grants.some(grant => grant.permission === 'repository.read' && this.data()?.repositories.some(repo => repo.id === grant.repositoryId)));
      this.selectInvitationTeam(firstEligible?.id ?? teams[0]?.id ?? '');
    }
  }
  editMember(member: Member): void {
    this.error.set('');
    this.memberRoles[member.id] = member.roles[0] ?? 'VIEWER';
    this.memberStatuses[member.id] = member.status;
    const repository = this.data()?.repositories[0]?.id ?? '';
    this.selectedMemberRepository.set(repository);
    this.selectedMemberPermissions.set(member.directGrants.filter(grant => grant.repositoryId === repository).map(grant => grant.permission));
    this.memberConfirmation.set(member);
  }
  selectMemberRepository(member: Member, repositoryId: string): void {
    this.selectedMemberRepository.set(repositoryId);
    this.selectedMemberPermissions.set(member.directGrants.filter(grant => grant.repositoryId === repositoryId).map(grant => grant.permission));
  }
  toggleMemberPermission(code: string, checked: boolean): void {
    this.selectedMemberPermissions.update(items => checked ? [...items, code] : items.filter(item => item !== code));
  }
  saveMemberPermissions(member: Member): void {
    const repository = this.selectedMemberRepository();
    if (!repository || this.pending()) return;
    this.mutate(`/members/${member.id}/repositories/${repository}/permissions`, { permissions: this.selectedMemberPermissions() }, 'Permisos directos del miembro actualizados.');
  }
  closeDialogs(): void {
    if (!this.pending()) {
      this.error.set('');
      this.dialog.set(null);
      this.memberConfirmation.set(null);
      this.revokeConfirmation.set(null);this.approvalConfirmation.set(null);
      this.confirmation.set(null);
    }
  }

 approve(invitation:Invitation):void{this.mutate(`/invitations/${invitation.id}/approve`,{},'Acceso aprobado para el equipo y repositorio indicados.',()=>this.approvalConfirmation.set(null));}
 invitationRepositories(){
   const team = this.data()?.teams.find(item => item.id === this.invitationTeamId);
   const allowed = new Set(team?.grants.filter(grant => grant.permission === 'repository.read').map(grant => grant.repositoryId) ?? []);
   return this.data()?.repositories.filter(repo => allowed.has(repo.id)) ?? [];
 }
 selectInvitationTeam(id:string):void{
   this.invitationTeamId=id;
   this.invitationRepositoryId='';
   const repositories = this.invitationRepositories();
   this.invitationRepositoryId = repositories[0]?.id ?? '';
 }
 openTeamsForInvite():void{
   const team = this.data()?.teams.find(item => item.id === this.invitationTeamId) ?? this.data()?.teams[0] ?? null;
   this.selectTeam(team);
   this.selectRepository(this.data()?.repositories[0]?.id ?? '');
   this.closeDialogs();
   this.tab.set('permissions');
 }
}
