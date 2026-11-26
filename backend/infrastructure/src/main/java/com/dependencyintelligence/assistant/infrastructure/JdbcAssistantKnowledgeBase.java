package com.dependencyintelligence.assistant.infrastructure;

import com.dependencyintelligence.assistant.application.dto.AssistantModels.Evidence;
import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.*;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Tenant-scoped persistence for authorized chunks, pgvector retrieval and cited conversations. */
@Component
public final class JdbcAssistantKnowledgeBase implements EvidenceSearch, ConversationStore, IndexableChunks {
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;
    public JdbcAssistantKnowledgeBase(JdbcTemplate jdbc, TenantTransactions tenants) { this.jdbc = jdbc; this.tenants = tenants; }

    @Override public List<Chunk> pending(UUID organizationId, List<UUID> repositoryIds, String model, String revision) {
        return tenants.execute(organizationId, () -> {
            var args = new ArrayList<Object>(); args.add(organizationId); args.add(organizationId); args.add(organizationId); args.add(model); args.add(revision);
            String visible = visibleDocuments(repositoryIds, args);
            return jdbc.query("""
                select distinct c.id,c.content from document_chunks c
                join document_versions v on v.organization_id=c.organization_id and v.id=c.document_version_id and v.original_status='RETAINED'
                join documents d on d.organization_id=v.organization_id and d.id=v.document_id and d.status='ACTIVE'
                where c.organization_id=? and v.organization_id=? and d.organization_id=?
                  and v.version_number=(select max(latest.version_number) from document_versions latest where latest.organization_id=d.organization_id and latest.document_id=d.id and latest.original_status='RETAINED')
                  and not exists (select 1 from document_chunk_embeddings e join embedding_models m on m.id=e.embedding_model_id
                    where e.organization_id=c.organization_id and e.document_chunk_id=c.id and m.provider='ollama' and m.model_name=? and m.revision=?)
                  and """ + visible + """
                order by c.id limit 32
                """, (row, i) -> new Chunk(row.getObject(1, UUID.class), row.getString(2)), args.toArray());
        });
    }

    @Override public boolean hasPending(UUID organizationId, List<UUID> repositoryIds, String model, String revision) {
        return tenants.execute(organizationId, () -> {
            var args = new ArrayList<Object>(); args.add(organizationId); args.add(organizationId); args.add(organizationId);
            args.add(model); args.add(revision);
            String visible = visibleDocuments(repositoryIds, args);
            return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from document_chunks c
                  join document_versions v on v.organization_id=c.organization_id and v.id=c.document_version_id and v.original_status='RETAINED'
                  join documents d on d.organization_id=v.organization_id and d.id=v.document_id and d.status='ACTIVE'
                  where c.organization_id=? and v.organization_id=? and d.organization_id=?
                    and v.version_number=(select max(latest.version_number) from document_versions latest where latest.organization_id=d.organization_id and latest.document_id=d.id and latest.original_status='RETAINED')
                    and not exists(select 1 from document_chunk_embeddings e join embedding_models m on m.id=e.embedding_model_id
                      where e.organization_id=c.organization_id and e.document_chunk_id=c.id and m.provider='ollama' and m.model_name=? and m.revision=?)
                    and """ + visible + """
                    limit 1)
                """, Boolean.class, args.toArray()));
        });
    }

    @Override public void save(UUID organizationId, Chunk chunk, Embedding embedding) {
        tenants.execute(organizationId, () -> {
            UUID modelId = model(embedding);
            UUID embeddingId = jdbc.queryForObject("""
                insert into document_chunk_embeddings(organization_id,document_chunk_id,embedding_model_id,status,vector_reference)
                values(?,?,?,'READY','pgvector') on conflict(organization_id,document_chunk_id,embedding_model_id)
                do update set status='READY',vector_reference='pgvector',created_at=clock_timestamp() returning id
                """, UUID.class, organizationId, chunk.id(), modelId);
            String vector = vector(embedding.values());
            jdbc.update("""
                insert into document_chunk_vectors(organization_id,embedding_id,embedding_model_id,embedding)
                values(?,?,?,?::vector) on conflict(organization_id,embedding_id) do update set embedding=excluded.embedding
                """, organizationId, embeddingId, modelId, vector);
            return null;
        });
    }

    @Override public List<Evidence> search(UUID organizationId, List<UUID> repositoryIds, String model, String revision, String query, float[] vector, int limit) {
        return tenants.execute(organizationId, () -> {
            var args = new ArrayList<Object>(); args.add(organizationId); args.add(organizationId); args.add(organizationId); args.add(organizationId);
            args.add(model); args.add(revision);
            String visible = visibleDocuments(repositoryIds, args);
            args.add(vector(vector)); args.add(query); args.add(query); args.add(limit);
            return jdbc.query("""
                with authorized_chunks as materialized (
                  select c.id,c.content,c.ordinal,c.page_start,c.section_path,d.id document_id,d.name document_name,
                         v.id version_id,v.original_filename,vc.embedding
                  from document_chunk_vectors vc
                  join document_chunk_embeddings e on e.organization_id=vc.organization_id and e.id=vc.embedding_id and e.embedding_model_id=vc.embedding_model_id and e.status='READY'
                  join embedding_models em on em.id=e.embedding_model_id and em.provider='ollama'
                  join document_chunks c on c.organization_id=e.organization_id and c.id=e.document_chunk_id
                  join document_versions v on v.organization_id=c.organization_id and v.id=c.document_version_id and v.original_status='RETAINED'
                  join documents d on d.organization_id=v.organization_id and d.id=v.document_id and d.status='ACTIVE'
                  where vc.organization_id=? and e.organization_id=? and c.organization_id=? and d.organization_id=?
                    and em.model_name=? and em.revision=?
                    and v.version_number=(select max(latest.version_number) from document_versions latest where latest.organization_id=d.organization_id and latest.document_id=d.id and latest.original_status='RETAINED')
                    and """ + visible + """
                ), vector_candidates as (
                  select id,row_number() over(order by distance) as rank_order
                  from (select id,embedding <=> ?::vector as distance from authorized_chunks order by distance limit 40) nearest
                ), text_candidates as (
                  select id,row_number() over(order by relevance desc,id) as rank_order
                  from (
                    select c.id,ts_rank_cd(to_tsvector('simple',c.content),websearch_to_tsquery('simple',?)) as relevance
                    from authorized_chunks c
                    where to_tsvector('simple',c.content) @@ websearch_to_tsquery('simple',?)
                    order by relevance desc,c.id limit 40
                  ) relevant
                ), fused_candidates as (
                  select coalesce(v.id,t.id) as id,
                         coalesce(1.0/(60+v.rank_order),0)+coalesce(1.0/(60+t.rank_order),0) as score
                  from vector_candidates v full outer join text_candidates t on t.id=v.id
                )
                select c.id,c.document_id,c.version_id,c.document_name,c.original_filename,c.page_start,
                       case when c.page_start is null then coalesce(nullif(c.section_path,''), 'Texto extraído · fragmento ' || c.ordinal)
                            else c.section_path end,
                       c.content,f.score
                from fused_candidates f join authorized_chunks c on c.id=f.id
                order by f.score desc,c.id limit ?
                """, (row, i) -> new Evidence(row.getObject(1, UUID.class), row.getObject(2, UUID.class), row.getObject(3, UUID.class),
                    row.getString(4), row.getString(5), (Integer) row.getObject(6), row.getString(7), row.getString(8), row.getDouble(9)), args.toArray());
        });
    }

    @Override public UUID createConversation(UUID organizationId, UUID userId, String title) {
        return tenants.execute(organizationId, () -> jdbc.queryForObject("""
            insert into assistant_conversations(organization_id,created_by_membership_id,title)
            select ?,id,? from organization_memberships where organization_id=? and user_id=? and status='ACTIVE'
            returning id
            """, UUID.class, organizationId, title, organizationId, userId));
    }
    @Override public int nextOrdinal(UUID organizationId, UUID conversationId) {
        return tenants.execute(organizationId, () -> jdbc.queryForObject("select coalesce(max(ordinal),0)+1 from assistant_messages where organization_id=? and conversation_id=?", Integer.class, organizationId, conversationId));
    }
    @Override public UUID saveMessage(UUID organizationId, UUID conversationId, int ordinal, String role, String content, String modelName) {
        return tenants.execute(organizationId, () -> {
            UUID modelId = modelName == null ? null : jdbc.queryForObject("""
                insert into assistant_model_versions(provider,model_name,revision) values('openai',?,?)
                on conflict(provider,model_name,revision) do update set model_name=excluded.model_name returning id
                """, UUID.class, modelName, modelName);
            return jdbc.queryForObject("insert into assistant_messages(organization_id,conversation_id,ordinal,role,content,model_version_id) values(?,?,?,?,?,?) returning id",
                UUID.class, organizationId, conversationId, ordinal, role, content, modelId);
        });
    }
    @Override public void saveEvidence(UUID organizationId, UUID messageId, List<Evidence> evidence) {
        tenants.execute(organizationId, () -> {
            for (int i = 0; i < evidence.size(); i++) {
                Evidence item = evidence.get(i);
                jdbc.update("insert into assistant_message_evidences(organization_id,message_id,ordinal,document_chunk_id,citation_label) values(?,?,?,?,?)",
                    organizationId, messageId, i + 1, item.chunkId(), "[" + (i + 1) + "] " + item.documentName());
            }
            return null;
        });
    }
    @Override public boolean canAccess(UUID organizationId, UUID userId, UUID conversationId) {
        return tenants.execute(organizationId, () -> Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from assistant_conversations c join organization_memberships m
              on m.organization_id=c.organization_id and m.id=c.created_by_membership_id
            where c.organization_id=? and c.id=? and m.user_id=? and m.status='ACTIVE')
            """, Boolean.class, organizationId, conversationId, userId)));
    }

    private UUID model(Embedding embedding) {
        return jdbc.queryForObject("""
            insert into embedding_models(provider,model_name,revision,dimension) values('ollama',?,?,?)
            on conflict(provider,model_name,revision) do update set dimension=embedding_models.dimension
              where embedding_models.dimension=excluded.dimension returning id
            """, UUID.class, embedding.model(), embedding.revision(), embedding.values().length);
    }
    private static String vector(float[] values) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) { if (i > 0) result.append(','); result.append(Float.toString(values[i])); }
        return result.append(']').toString();
    }
    private static String visibleDocuments(List<UUID> repositoryIds, List<Object> args) {
        String general = "not exists(select 1 from document_repositories scope where scope.organization_id=d.organization_id and scope.document_id=d.id)";
        if (repositoryIds.isEmpty()) return general;
        args.addAll(repositoryIds);
        return "(" + general + " or exists(select 1 from document_repositories scope where scope.organization_id=d.organization_id and scope.document_id=d.id and scope.repository_id in (" + marks(repositoryIds.size()) + ")))";
    }
    private static String marks(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }
}
