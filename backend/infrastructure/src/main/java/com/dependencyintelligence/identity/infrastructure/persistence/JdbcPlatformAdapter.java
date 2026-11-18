package com.dependencyintelligence.identity.infrastructure.persistence;
import com.dependencyintelligence.identity.application.dto.PlatformModels.*;
import com.dependencyintelligence.identity.application.port.out.PlatformStore;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import com.dependencyintelligence.organization.application.exception.AccessConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcPlatformAdapter implements PlatformStore {
    private final JdbcTemplate jdbc;
    public JdbcPlatformAdapter(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public boolean administrator(UUID user){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from platform_administrators a join users u on u.id=a.user_id where a.user_id=? and u.is_active and u.admission_status='APPROVED' and u.email_verified_at is not null)",Boolean.class,user));}
    public void lockAdministration(){jdbc.queryForObject("select pg_advisory_xact_lock(1414014)",Object.class);}
    private long count(String sql){return jdbc.queryForObject(sql,Long.class);}
    private static final String USERS="select u.*,exists(select 1 from platform_administrators a where a.user_id=u.id) as administrator from users u";
    private User mapUser(java.sql.ResultSet r)throws java.sql.SQLException{return new User(r.getObject("id",UUID.class),r.getString("email"),r.getString("display_name"),r.getString("account_type"),r.getString("admission_status"),r.getTimestamp("email_verified_at")!=null,r.getBoolean("is_active"),r.getBoolean("administrator"));}
    public User user(UUID id){return jdbc.query(USERS+" where u.id=? for update of u",(r,n)->mapUser(r),id).stream().findFirst().orElseThrow(()->new ResourceNotFoundException("Cuenta no encontrada"));}
    public long activeAdministrators(){return count("select count(*) from platform_administrators a join users u on u.id=a.user_id where u.is_active and u.email_verified_at is not null and u.admission_status='APPROVED'");}
    public Overview overview(){
        var stats=new Statistics(count("select count(*) from users u where u.account_type='OWNER' and u.admission_status='APPROVED' and u.is_active and not exists(select 1 from platform_administrators a where a.user_id=u.id)"),count("select count(*) from users where account_type='OWNER' and admission_status='PENDING_REVIEW'"),count("select count(*) from users where account_type='MEMBER' and is_active"),count("select count(*) from repositories"),count("select count(*) from repositories where is_enabled"),count("select count(*) from organization_teams"),count("select count(*) from organizations"),count("select count(*) from membership_invitations where status in ('PENDING','AWAITING_OWNER') and expires_at>clock_timestamp()"));
        var users=jdbc.query(USERS+" order by u.created_at desc,u.id limit 200",(r,n)->mapUser(r));
        var repositories=items("select r.id,r.organization_id,r.full_name,o.name,r.is_enabled from repositories r join organizations o on o.id=r.organization_id order by r.full_name,r.id limit 200");
        var teams=items("select t.id,t.organization_id,t.name,o.name,t.is_enabled from organization_teams t join organizations o on o.id=t.organization_id order by t.name,t.id limit 200");
        var organizations=items("select id,id,name,name,status='ACTIVE' from organizations order by name,id limit 200");
        var memberships=jdbc.query("select m.id,m.organization_id,u.email,r.code,m.status from organization_memberships m join users u on u.id=m.user_id join membership_roles mr on mr.organization_id=m.organization_id and mr.membership_id=m.id join roles r on r.id=mr.role_id order by u.email,m.id limit 200",(r,n)->new Membership(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getString(5)));
        var invitations=jdbc.query("select i.id,i.organization_id,i.email_normalized,i.status,u.email,t.name,r.full_name from membership_invitations i join organization_memberships m on m.organization_id=i.organization_id and m.id=i.invited_by_membership_id join users u on u.id=m.user_id left join organization_teams t on t.organization_id=i.organization_id and t.id=i.target_team_id left join repositories r on r.organization_id=i.organization_id and r.id=i.repository_id order by i.created_at desc,i.id limit 200",(r,n)->new Invitation(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7)));
        var audit=jdbc.query("select * from platform_audit_events order by created_at desc,id limit 100",(r,n)->new Audit(r.getObject("id",UUID.class),r.getObject("actor_id",UUID.class),r.getString("action"),r.getString("resource_type"),r.getObject("resource_id",UUID.class),r.getString("reason"),r.getTimestamp("created_at").toInstant()));
        return new Overview(stats,users,repositories,teams,organizations,memberships,invitations,audit);
    }
    private List<Item> items(String sql){return jdbc.query(sql,(r,n)->new Item(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4),r.getBoolean(5)));}
    public void change(UUID actor,Action action){
        String sql; boolean enabled=action.operation()==Operation.ENABLE;
        switch(action.resource()){
            case USER -> {
                sql=switch(action.operation()){
                    case APPROVE -> "update users set email_verified_at=coalesce(email_verified_at,clock_timestamp()),admission_status='APPROVED',reviewed_by_user_id=?,reviewed_at=clock_timestamp(),updated_at=clock_timestamp() where id=?";
                    case REJECT -> "update users set admission_status='REJECTED',credential_version=credential_version+1,reviewed_by_user_id=?,reviewed_at=clock_timestamp(),updated_at=clock_timestamp() where id=?";
                    case ENABLE,DISABLE,REMOVE -> "update users set is_active=?,credential_version=credential_version+1,updated_at=clock_timestamp() where id=?";
                };
                if(action.operation()==Operation.ENABLE || action.operation()==Operation.DISABLE || action.operation()==Operation.REMOVE) requireChanged(jdbc.update(sql,enabled,action.id()));
                else requireChanged(jdbc.update(sql,actor,action.id()));
                return;
            }
            case REPOSITORY -> sql="update repositories set is_enabled=? where id=?";
            case TEAM -> sql="update organization_teams set is_enabled=? where id=?";
            case ORGANIZATION -> { requireChanged(jdbc.update("update organizations set status=?,updated_at=clock_timestamp() where id=?",enabled?"ACTIVE":"SUSPENDED",action.id())); return; }
            case MEMBERSHIP -> {
                var targets=jdbc.queryForList("select organization_id,user_id from organization_memberships where id=?",action.id());
                if(targets.isEmpty())throw new ResourceNotFoundException("Membresía no encontrada");
                var target=targets.getFirst();
                UUID org=(UUID)target.get("organization_id");
                jdbc.queryForObject("select id from organizations where id=? for update",UUID.class,org);
                boolean lastOwner=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from membership_roles mr join roles r on r.id=mr.role_id where mr.organization_id=? and mr.membership_id=? and r.code='OWNER') and (select count(distinct m.id) from organization_memberships m join membership_roles mr on mr.organization_id=m.organization_id and mr.membership_id=m.id join roles r on r.id=mr.role_id and r.code='OWNER' join users u on u.id=m.user_id and u.is_active where m.organization_id=? and m.status='ACTIVE')<=1 and exists(select 1 from organizations where id=? and status='ACTIVE')",Boolean.class,org,action.id(),org,org));
                if(lastOwner)throw new AccessConflictException("Asigna otro propietario o suspende la organización antes de retirar al último propietario");
                requireChanged(jdbc.update("update organization_memberships set status=? where id=?",action.operation()==Operation.REMOVE?"LEFT":"SUSPENDED",action.id()));
                if(action.operation()==Operation.REMOVE){
                    jdbc.queryForObject("select set_config('app.organization_id',?,true)",String.class,org.toString());
                    jdbc.update("delete from member_repository_assignments where organization_id=? and membership_id=?",org,action.id());
                    jdbc.update("delete from team_memberships where organization_id=? and membership_id=?",org,action.id());
                }
                return;
            }
            default -> throw new IllegalArgumentException("Recurso no válido");
        }
        requireChanged(jdbc.update(sql,enabled,action.id()));
    }
    private void requireChanged(int count){if(count!=1)throw new ResourceNotFoundException("Recurso no encontrado");}
    public void audit(UUID actor,Action action){jdbc.update("insert into platform_audit_events(actor_id,action,resource_type,resource_id,reason) values(?,?,?,?,?)",actor,action.operation().name(),action.resource().name(),action.id(),action.reason().strip());}
    public void updateName(UUID user,String name){requireChanged(jdbc.update("update users set display_name=?,updated_at=clock_timestamp() where id=? and is_active",name,user));}
}
