package com.dependencyintelligence.identity.infrastructure.persistence;
import com.dependencyintelligence.identity.application.port.in.FindAccountWorkspace;
import com.dependencyintelligence.identity.application.port.out.RepositoryPermissionStore;
import com.dependencyintelligence.organization.application.port.out.OrganizationStore;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Component
public class JdbcAccountWorkspace implements FindAccountWorkspace {
    private final JdbcTemplate jdbc;private final TenantTransactions tenants;private final OrganizationStore organizations;private final RepositoryPermissionStore permissions;
    public JdbcAccountWorkspace(JdbcTemplate jdbc,TenantTransactions tenants,OrganizationStore organizations,RepositoryPermissionStore permissions){this.jdbc=jdbc;this.tenants=tenants;this.organizations=organizations;this.permissions=permissions;}
    public List<Repository> repositories(UUID user){
        var result=new ArrayList<Repository>();
        for(var org:organizations.listForUser(user))result.addAll(tenants.execute(org.id(),()->jdbc.query("select id,full_name,default_branch,provider from repositories where organization_id=? and is_enabled order by full_name",(r,n)->new Repository(r.getObject(1,UUID.class),org.id(),org.name(),r.getString(2),r.getString(3),r.getString(4)),org.id()).stream().filter(repo->permissions.permits(user,org.id(),repo.id(),"repository.read")).toList()));
        return List.copyOf(result);
    }
}
