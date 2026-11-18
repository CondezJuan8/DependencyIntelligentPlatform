package com.dependencyintelligence.organization.infrastructure.persistence;

import com.dependencyintelligence.organization.application.dto.OrganizationResult;
import com.dependencyintelligence.organization.application.port.out.OrganizationStore;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import com.dependencyintelligence.identity.application.exception.AuthenticationFailedException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcOrganizationAdapter implements OrganizationStore {
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;
    private final TransactionTemplate transactions;

    public JdbcOrganizationAdapter(JdbcTemplate jdbc, TenantTransactions tenants, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.transactions = new TransactionTemplate(manager);
    }

    @Override public OrganizationResult createWithOwner(UUID userId, String name) {
        UUID id = UUID.randomUUID();
        return tenants.execute(id, () -> {
            jdbc.queryForObject("select set_config('app.user_id', ?, true)", String.class, userId.toString());
            var active = jdbc.query("select id from users where id = ? and is_active and email_verified_at is not null and admission_status='APPROVED' and account_type='OWNER' for share",
                (row, index) -> row.getObject("id", UUID.class), userId);
            if (active.isEmpty()) throw new AuthenticationFailedException();
            jdbc.update("insert into organizations(id, name, slug) values (?, ?, ?)", id, name, "org-" + id);
            UUID membership = jdbc.queryForObject("""
                insert into organization_memberships(organization_id, user_id) values (?, ?) returning id
                """, UUID.class, id, userId);
            int assigned = jdbc.update("""
                insert into membership_roles(organization_id, membership_id, role_id)
                select ?, ?, id from roles where code = 'OWNER'
                """, id, membership);
            if (assigned != 1) throw new IllegalStateException("Owner role is not configured");
            jdbc.update("insert into organization_settings(organization_id) values (?)", id);
            jdbc.update("""
                insert into audit_events(id, organization_id, actor_id, action, resource_type, resource_id, result)
                values (?, ?, ?, 'organization.create', 'organization', ?, 'SUCCESS')
                """, UUID.randomUUID(), id, userId, id);
            return new OrganizationResult(id, name);
        });
    }

    @Override public List<OrganizationResult> listForUser(UUID userId) {
        return transactions.execute(status -> {
            jdbc.queryForObject("select set_config('app.user_id', ?, true)", String.class, userId.toString());
            return jdbc.query("""
                select organization.id, organization.name from organizations organization
                join organization_memberships membership on membership.organization_id = organization.id
                where membership.user_id = ? and membership.status = 'ACTIVE' and organization.status = 'ACTIVE'
                order by organization.name, organization.id
                """, (row, index) -> new OrganizationResult(row.getObject("id", UUID.class), row.getString("name")), userId);
        });
    }
}
