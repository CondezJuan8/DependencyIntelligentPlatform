package com.dependencyintelligence.organization.infrastructure.persistence;

import com.dependencyintelligence.organization.application.port.out.WorkspaceSettingsStore;
import com.dependencyintelligence.organization.domain.WorkspaceLanguage;
import com.dependencyintelligence.organization.domain.WorkspaceTheme;
import com.dependencyintelligence.shared.application.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public class JdbcWorkspaceSettingsAdapter implements WorkspaceSettingsStore {
    private final JdbcTemplate jdbc;

    public JdbcWorkspaceSettingsAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public Settings read(UUID organization) {
        return jdbc.query("""
            select organization.id, organization.name, settings.theme, settings.language
            from organization_settings settings
            join organizations organization on organization.id = settings.organization_id and organization.status = 'ACTIVE'
            where settings.organization_id = ?
            """, (row, index) -> new Settings(row.getObject("id", UUID.class), row.getString("name"),
                WorkspaceTheme.parse(row.getString("theme")), WorkspaceLanguage.parse(row.getString("language"))), organization)
            .stream().findFirst().orElseThrow(() -> new ResourceNotFoundException("Configuración de organización no encontrada"));
    }

    @Override public void update(UUID organization, UUID actor, WorkspaceTheme theme, WorkspaceLanguage language) {
        int changed = jdbc.update("""
            update organization_settings
            set theme = ?, language = ?, updated_by_user_id = ?, updated_at = clock_timestamp()
            where organization_id = ?
            """, theme.name(), language.name(), actor, organization);
        if (changed != 1) throw new ResourceNotFoundException("Configuración de organización no encontrada");
    }
}
