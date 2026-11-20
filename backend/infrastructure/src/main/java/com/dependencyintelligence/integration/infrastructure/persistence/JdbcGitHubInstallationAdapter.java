package com.dependencyintelligence.integration.infrastructure.persistence;

import com.dependencyintelligence.identity.application.port.in.AuthorizeRepositoryAction;
import com.dependencyintelligence.integration.application.dto.GitHubModels.Candidate;
import com.dependencyintelligence.integration.application.port.out.GitHubAppGateway;
import com.dependencyintelligence.integration.application.port.out.GitHubInstallationStore;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Clock;

@Repository
public class JdbcGitHubInstallationAdapter implements GitHubInstallationStore {
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;
    private final AuthorizeRepositoryAction authorization;
    private final Clock clock;
    public JdbcGitHubInstallationAdapter(JdbcTemplate jdbc, TenantTransactions tenants, AuthorizeRepositoryAction authorization, Clock clock) {
        this.jdbc = jdbc; this.tenants = tenants; this.authorization = authorization; this.clock = clock;
    }

    @Override public void connect(UUID organization, UUID user, GitHubAppGateway.InstallationInfo app) {
        tenants.execute(organization, () -> {
            authorization.check(user, organization, null, "repository.manage");
            UUID integration = jdbc.queryForObject("""
                insert into integrations(id, organization_id, provider, external_id, status, external_account_login)
                values (?, ?, 'github', ?, 'ACTIVE', ?)
                on conflict (organization_id, provider, external_id) do update
                  set status='ACTIVE', external_account_login=excluded.external_account_login, disconnected_at=null
                returning id
                """, UUID.class, UUID.randomUUID(), organization, Long.toString(app.id()), app.accountLogin());
            var saved = jdbc.query("""
                insert into github_installations(id, organization_id, integration_id, installation_external_id, account_type)
                values (?, ?, ?, ?, ?)
                on conflict (installation_external_id) do update set account_type=excluded.account_type
                  where github_installations.organization_id=excluded.organization_id
                returning id
                """, (row, index) -> row.getObject(1, UUID.class), UUID.randomUUID(), organization, integration, app.id(), app.accountType());
            if (saved.isEmpty()) throw new IllegalStateException("This GitHub installation is already connected to another organization.");
            return null;
        });
    }

    @Override public List<GitHubAppGateway.InstallationInfo> connected(UUID organization, UUID user) {
        return tenants.execute(organization, () -> {
            authorization.check(user, organization, null, "repository.manage");
            return jdbc.query("""
                select g.installation_external_id, i.external_account_login, g.account_type
                from github_installations g join integrations i on i.organization_id=g.organization_id and i.id=g.integration_id
                where g.organization_id=? and i.provider='github' and i.status='ACTIVE'
                order by i.external_account_login, g.installation_external_id
                """, (row, index) -> new GitHubAppGateway.InstallationInfo(row.getLong(1), row.getString(2), row.getString(3)), organization);
        });
    }

    @Override public List<String> importedRepositoryIds(UUID organization, UUID user) {
        return tenants.execute(organization, () -> {
            authorization.check(user, organization, null, "repository.manage");
            return jdbc.queryForList("select external_id from repositories where organization_id=? and provider='github' and is_enabled order by full_name", String.class, organization);
        });
    }

    @Override public int importRepositories(UUID organization, UUID user, Map<Long, List<Candidate>> repositories) {
        return tenants.execute(organization, () -> {
            authorization.check(user, organization, null, "repository.manage");
            int inserted = 0;
            for (var batch : repositories.entrySet()) {
                UUID integration = jdbc.query("""
                    select integration_id from github_installations where organization_id=? and installation_external_id=?
                    """, (row, index) -> row.getObject(1, UUID.class), organization, batch.getKey()).stream().findFirst()
                    .orElseThrow(() -> new IllegalStateException("GitHub installation is no longer connected."));
            for (Candidate repository : batch.getValue()) {
                int count = jdbc.update("""
                    insert into repositories(id, organization_id, integration_id, provider, external_id, full_name,
                      default_branch, html_url, visibility, is_archived, last_synced_at)
                    values (?, ?, ?, 'github', ?, ?, ?, ?, ?, ?, ?)
                    on conflict (organization_id, provider, external_id) do update set
                      integration_id=excluded.integration_id, full_name=excluded.full_name,
                      default_branch=excluded.default_branch, html_url=excluded.html_url,
                      visibility=excluded.visibility, is_archived=excluded.is_archived,
                      last_synced_at=excluded.last_synced_at, is_enabled=true
                    """, UUID.randomUUID(), organization, integration, repository.externalId(), repository.fullName(),
                    repository.defaultBranch().isBlank() ? "main" : repository.defaultBranch(), repository.htmlUrl(),
                    repository.visibility(), repository.archived(), Timestamp.from(clock.instant()));
                inserted += count;
            }
            }
            return inserted;
        });
    }

    @Override public int synchronizeRepositories(UUID organization, UUID user, long installationId, List<Candidate> repositories) {
        return tenants.execute(organization, () -> {
            authorization.check(user, organization, null, "repository.manage");
            UUID integration = jdbc.query("select integration_id from github_installations where organization_id=? and installation_external_id=?",
                (row,index)->row.getObject(1,UUID.class),organization,installationId).stream().findFirst()
                .orElseThrow(()->new IllegalStateException("GitHub installation is no longer connected."));
            var ids = repositories.stream().map(Candidate::externalId).distinct().toList();
            String sql = "update repositories set is_enabled=false where organization_id=? and integration_id=? and provider='github' and is_enabled";
            Object[] args;
            if (ids.isEmpty()) args = new Object[]{organization,integration};
            else {
                sql += " and external_id not in (" + String.join(",", java.util.Collections.nCopies(ids.size(), "?")) + ")";
                var values = new java.util.ArrayList<Object>(); values.add(organization); values.add(integration); values.addAll(ids); args=values.toArray();
            }
            int disabled = jdbc.update(sql,args);
            for (Candidate repository : repositories) jdbc.update("""
                update repositories set full_name=?, default_branch=?, html_url=?, visibility=?, is_archived=?, last_synced_at=?
                 where organization_id=? and integration_id=? and provider='github' and external_id=? and is_enabled
                """,repository.fullName(),repository.defaultBranch(),repository.htmlUrl(),repository.visibility(),repository.archived(),
                Timestamp.from(clock.instant()),organization,integration,repository.externalId());
            return disabled;
        });
    }

    @Override public int revokeInstallation(UUID organization, UUID user, long installationId) {
        return tenants.execute(organization, () -> {
            authorization.check(user, organization, null, "repository.manage");
            UUID integration = jdbc.query("select integration_id from github_installations where organization_id=? and installation_external_id=?",
                (row,index)->row.getObject(1,UUID.class),organization,installationId).stream().findFirst()
                .orElseThrow(()->new IllegalStateException("GitHub installation is no longer connected."));
            jdbc.update("update integrations set status='REVOKED', disconnected_at=clock_timestamp() where organization_id=? and id=? and status<>'REVOKED'",organization,integration);
            return jdbc.update("update repositories set is_enabled=false where organization_id=? and integration_id=? and provider='github' and is_enabled",organization,integration);
        });
    }
}
