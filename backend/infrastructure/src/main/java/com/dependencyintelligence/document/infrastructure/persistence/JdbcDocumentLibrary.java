package com.dependencyintelligence.document.infrastructure.persistence;

import com.dependencyintelligence.document.application.dto.DocumentModels.*;
import com.dependencyintelligence.document.application.port.out.DocumentLibrary;
import com.dependencyintelligence.document.domain.DocumentType;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import jakarta.annotation.PreDestroy;
import org.apache.tika.config.TimeoutLimits;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.pipes.fork.PipesForkParser;
import org.apache.tika.pipes.fork.PipesForkParserConfig;
import org.apache.tika.pipes.fork.PipesForkResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public final class JdbcDocumentLibrary implements DocumentLibrary {
    private static final int MAX_EXTRACTED_CHARS = 1_000_000;
    private static final int CHUNK_CHARS = 2_500;
    private final JdbcTemplate jdbc;
    private final TenantTransactions tenants;
    private final Path root;
    private final Path tikaConfig;
    private final PipesForkParser tika;

    public JdbcDocumentLibrary(JdbcTemplate jdbc, TenantTransactions tenants,
            @Value("${app.documents.storage-root:./data/documents}") String root) {
        this.jdbc = jdbc;
        this.tenants = tenants;
        this.root = Path.of(root).toAbsolutePath().normalize();
        Path configPath = null;
        try {
            configPath = copyTikaConfig();
            this.tikaConfig = configPath;
            this.tika = new PipesForkParser(new PipesForkParserConfig().setUserConfigPath(tikaConfig).setNumClients(1)
                .setTimeoutLimits(new TimeoutLimits(45_000, 30_000)).addJvmArg("-Xmx256m"));
            this.tika.start();
        } catch (Exception configurationFailure) {
            if (configurationFailure instanceof InterruptedException) Thread.currentThread().interrupt();
            if (configPath != null) try { Files.deleteIfExists(configPath); } catch (IOException cleanup) { configurationFailure.addSuppressed(cleanup); }
            throw new IllegalStateException("No se pudo preparar el extractor documental aislado.", configurationFailure);
        }
    }

    @PreDestroy public void closeParser() throws IOException {
        try { tika.close(); } finally { Files.deleteIfExists(tikaConfig); }
    }

    private static Path copyTikaConfig() throws IOException {
        Path config = Files.createTempFile("dependency-intelligence-tika-", ".json");
        try (var input = JdbcDocumentLibrary.class.getResourceAsStream("/tika-config.json")) {
            if (input == null) throw new IOException("Falta la configuración OCR de Apache Tika.");
            Files.copy(input, config, StandardCopyOption.REPLACE_EXISTING);
            return config;
        } catch (IOException failure) {
            Files.deleteIfExists(config);
            throw failure;
        }
    }

    @Override public List<Summary> list(UUID organizationId, List<UUID> visibleRepositoryIds) {
        return tenants.execute(organizationId, () -> {
            var nameArgs = new ArrayList<Object>();
            String repositoryNames = repositoryNames(visibleRepositoryIds, nameArgs, "d");
            var visibleArgs = new ArrayList<Object>();
            String visible = visibilityClause(visibleRepositoryIds, visibleArgs, "d");
            var args = new ArrayList<Object>(nameArgs);
            args.add(organizationId); args.addAll(visibleArgs);
            return jdbc.query("""
                select d.id,d.name,d.document_type,d.description,d.status,
                       coalesce(latest.indexing_status,'PENDING'),coalesce(latest.version_number,0),
                       (select count(*) from document_versions count_version where count_version.organization_id=d.organization_id and count_version.document_id=d.id),
                       coalesce(latest.size_bytes,0),latest.mime_type,latest.sha256,latest.uploaded_at,
                       coalesce(repos.names,'')
                from documents d
                left join lateral (select v.* from document_versions v where v.organization_id=d.organization_id and v.document_id=d.id and v.original_status='RETAINED' order by v.version_number desc limit 1) latest on true
                left join lateral (
                """ + repositoryNames + """
                ) repos on true
                where d.organization_id=? and d.status in ('ACTIVE','DELETION_PENDING') and """ + visible + """
                order by coalesce(latest.uploaded_at,d.created_at) desc,d.name
                """,
                (row, i) -> new Summary(row.getObject(1, UUID.class), row.getString(2), row.getString(3), row.getString(4),
                    row.getString(5), row.getString(6), row.getInt(7), Math.toIntExact(row.getLong(8)), row.getLong(9),
                    row.getString(10), row.getString(11), row.getTimestamp(12) == null ? null : row.getTimestamp(12).toInstant(),
                    row.getString(13).isBlank() ? List.of() : List.of(row.getString(13).split(", "))), args.toArray());
        });
    }

    @Override public List<Version> versions(UUID organizationId, UUID documentId) {
        return tenants.execute(organizationId, () -> jdbc.query("""
            select id,version_number,original_filename,mime_type,size_bytes,sha256,indexing_status,uploaded_at
            from document_versions where organization_id=? and document_id=? and original_status='RETAINED'
            order by version_number desc
            """, (row, i) -> new Version(row.getObject(1, UUID.class), row.getInt(2), row.getString(3), row.getString(4),
                row.getLong(5), row.getString(6), row.getString(7), row.getTimestamp(8).toInstant()), organizationId, documentId));
    }

    @Override public Summary upload(Upload upload, boolean createDocument, UUID requestedDocumentId) {
        UUID documentId = createDocument ? UUID.randomUUID() : requestedDocumentId;
        UUID versionId = UUID.randomUUID();
        DocumentType type = DocumentType.fromFilename(upload.originalFilename());
        String storageKey = upload.organizationId() + "/" + documentId + "/" + versionId + extension(upload.originalFilename());
        Path target = resolve(storageKey);
        byte[] content = upload.content();
        String hash = sha256(content);
        Extraction extraction = extract(content, upload.originalFilename(), type);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return tenants.execute(upload.organizationId(), () -> {
                if (createDocument) {
                    jdbc.update("""
                        insert into documents(id,organization_id,name,document_type,origin,description,status,uploaded_by_membership_id)
                        values(?,?,?,?,'UPLOAD',?,'ACTIVE',(select id from organization_memberships where organization_id=? and user_id=? and status='ACTIVE'))
                        """, documentId, upload.organizationId(), upload.name().strip(), type.code(), upload.description(), upload.organizationId(), upload.userId());
                } else if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from documents where organization_id=? and id=? and status='ACTIVE')", Boolean.class, upload.organizationId(), documentId))) {
                    throw new IllegalArgumentException("No encontramos el documento activo para añadir la versión.");
                }
                int versionNumber = createDocument ? 1 : jdbc.queryForObject("select coalesce(max(version_number),0)+1 from document_versions where organization_id=? and document_id=?", Integer.class, upload.organizationId(), documentId);
                if (createDocument) for (UUID repositoryId : upload.repositoryIds()) {
                    jdbc.update("insert into document_repositories(organization_id,document_id,repository_id) values(?,?,?) on conflict do nothing", upload.organizationId(), documentId, repositoryId);
                }
                String status = extraction.text().isBlank() ? "FAILED" : "COMPLETED";
                jdbc.update("""
                    insert into document_versions(id,organization_id,document_id,version_number,sha256,size_bytes,mime_type,original_filename,
                      storage_backend,original_status,storage_key,indexing_status,uploaded_at)
                    values(?,?,?,?,?,?,?,?,'LOCAL','RETAINED',?,?,?)
                    """, versionId, upload.organizationId(), documentId, versionNumber, hash, content.length,
                    extraction.mimeType(), safeFilename(upload.originalFilename()), storageKey, status, Timestamp.from(Instant.now()));
                saveProcessingRun(upload.organizationId(), versionId, "EXTRACT", status, extraction.error());
                if ("COMPLETED".equals(status)) {
                    List<String> chunks = chunks(extraction.text());
                    saveProcessingRun(upload.organizationId(), versionId, "CHUNK", "COMPLETED", null);
                    int ordinal = 1;
                    for (String chunk : chunks) jdbc.update("""
                        insert into document_chunks(organization_id,document_version_id,ordinal,content,page_start,page_end,section_path,token_count,content_hash)
                        values(?,?,?,?,null,null,null,?,?)
                        """, upload.organizationId(), versionId, ordinal++, chunk, chunk.length(), sha256(chunk.getBytes(StandardCharsets.UTF_8)));
                }
                return summary(upload.organizationId(), documentId);
            });
        } catch (Exception failure) {
            try { Files.deleteIfExists(target); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("No se pudo almacenar el documento de forma privada.", failure);
        }
    }

    @Override public Download download(UUID organizationId, UUID documentId, UUID versionId) {
        return tenants.execute(organizationId, () -> {
            var versions = jdbc.query("""
                select original_filename,mime_type,storage_key from document_versions
                where organization_id=? and document_id=? and original_status='RETAINED' and (?::uuid is null or id=?)
                order by version_number desc limit 1
                """, (row, i) -> new String[]{row.getString(1), row.getString(2), row.getString(3)}, organizationId, documentId, versionId, versionId);
            if (versions.isEmpty()) throw new IllegalArgumentException("El archivo ya no está disponible.");
            String[] version = versions.getFirst();
            try { return new Download(version[0], version[1], Files.readAllBytes(resolve(version[2]))); }
            catch (IOException failure) { throw new IllegalStateException("No se pudo leer el archivo privado.", failure); }
        });
    }

    @Override public List<SearchHit> search(UUID organizationId, List<UUID> visibleRepositoryIds, String query) {
        return tenants.execute(organizationId, () -> {
            var args = new ArrayList<Object>(); args.add(query);
            String repositoryNames = repositoryNames(visibleRepositoryIds, args, "d");
            args.add(organizationId); args.add(query);
            var visibleArgs = new ArrayList<Object>();
            String visible = visibilityClause(visibleRepositoryIds, visibleArgs, "d");
            args.addAll(visibleArgs); args.add(query);
            return jdbc.query("""
                select d.id,v.id,d.name,v.original_filename,ts_headline('simple',c.content,websearch_to_tsquery('simple',?), 'MaxFragments=2,MaxWords=30,MinWords=8'),c.page_start,coalesce(repos.names,'')
                from document_chunks c join document_versions v on v.organization_id=c.organization_id and v.id=c.document_version_id
                join documents d on d.organization_id=v.organization_id and d.id=v.document_id
                left join lateral (
                """ + repositoryNames + """
                ) repos on true
                where c.organization_id=? and d.status='ACTIVE' and v.original_status='RETAINED'
                  and to_tsvector('simple',c.content) @@ websearch_to_tsquery('simple',?) and """ + visible + """
                order by ts_rank(to_tsvector('simple',c.content),websearch_to_tsquery('simple',?)) desc limit 50
                """,
                (row, i) -> new SearchHit(row.getObject(1, UUID.class), row.getObject(2, UUID.class), row.getString(3), row.getString(4),
                    row.getString(5), (Integer) row.getObject(6), row.getString(7)),
                args.toArray());
        });
    }

    @Override public void delete(UUID organizationId, UUID documentId) {
        List<String> keys = tenants.execute(organizationId, () -> jdbc.queryForList("select storage_key from document_versions where organization_id=? and document_id=? and original_status='RETAINED'", String.class, organizationId, documentId));
        UUID requestId = tenants.execute(organizationId, () -> {
            UUID id = jdbc.query("select id from data_deletion_requests where organization_id=? and document_id=? and status='PENDING' order by requested_at desc limit 1", (row, i) -> row.getObject(1, UUID.class), organizationId, documentId)
                .stream().findFirst().orElseGet(() -> jdbc.queryForObject("insert into data_deletion_requests(organization_id,document_id,status) values(?,?,'PENDING') returning id", UUID.class, organizationId, documentId));
            jdbc.update("update documents set status='DELETION_PENDING' where organization_id=? and id=? and status in ('ACTIVE','DELETION_PENDING')", organizationId, documentId);
            return id;
        });
        for (String key : keys) try { Files.deleteIfExists(resolve(key)); }
        catch (IOException failure) { throw new IllegalStateException("La eliminación quedó pendiente; el original permanece privado y puedes reintentar.", failure); }
        tenants.execute(organizationId, () -> {
            if (tableExists("document_chunk_vectors")) jdbc.update("delete from document_chunk_vectors where organization_id=? and embedding_id in (select id from document_chunk_embeddings where organization_id=? and document_chunk_id in (select id from document_chunks where organization_id=? and document_version_id in (select id from document_versions where organization_id=? and document_id=?)))", organizationId, organizationId, organizationId, organizationId, documentId);
            jdbc.update("delete from assistant_message_evidences where organization_id=? and document_chunk_id in (select id from document_chunks where organization_id=? and document_version_id in (select id from document_versions where organization_id=? and document_id=?))", organizationId, organizationId, organizationId, documentId);
            jdbc.update("delete from document_chunk_embeddings where organization_id=? and document_chunk_id in (select id from document_chunks where organization_id=? and document_version_id in (select id from document_versions where organization_id=? and document_id=?))", organizationId, organizationId, organizationId, documentId);
            jdbc.update("delete from document_chunks where organization_id=? and document_version_id in (select id from document_versions where organization_id=? and document_id=?)", organizationId, organizationId, documentId);
            jdbc.update("delete from document_processing_artifacts where organization_id=? and processing_run_id in (select id from document_processing_runs where organization_id=? and document_version_id in (select id from document_versions where organization_id=? and document_id=?))", organizationId, organizationId, organizationId, documentId);
            jdbc.update("delete from document_processing_runs where organization_id=? and document_version_id in (select id from document_versions where organization_id=? and document_id=?)", organizationId, organizationId, documentId);
            jdbc.update("update document_versions set original_status='DELETED',storage_key=null,temporary_storage_key=null,temporary_expires_at=null,deleted_at=clock_timestamp(),indexing_status='CANCELLED' where organization_id=? and document_id=?", organizationId, documentId);
            jdbc.update("update documents set status='ARCHIVED' where organization_id=? and id=?", organizationId, documentId);
            jdbc.update("update data_deletion_requests set status='COMPLETED',completed_at=clock_timestamp() where organization_id=? and id=?", organizationId, requestId);
            return null;
        });
    }

    private Summary summary(UUID organizationId, UUID documentId) {
        return jdbc.queryForObject("""
            select d.id,d.name,d.document_type,d.description,d.status,v.indexing_status,v.version_number,
                   (select count(*) from document_versions allv where allv.organization_id=d.organization_id and allv.document_id=d.id),
                   v.size_bytes,v.mime_type,v.sha256,v.uploaded_at,coalesce(repos.names,'')
            from documents d join document_versions v on v.organization_id=d.organization_id and v.document_id=d.id
            left join lateral (select string_agg(r.full_name,', ' order by r.full_name) names from document_repositories dr join repositories r on r.organization_id=dr.organization_id and r.id=dr.repository_id where dr.organization_id=d.organization_id and dr.document_id=d.id) repos on true
            where d.organization_id=? and d.id=? and v.version_number=(select max(latest.version_number) from document_versions latest where latest.organization_id=d.organization_id and latest.document_id=d.id)
            """, (row, i) -> new Summary(row.getObject(1, UUID.class), row.getString(2), row.getString(3), row.getString(4),
                row.getString(5), row.getString(6), row.getInt(7), Math.toIntExact(row.getLong(8)), row.getLong(9), row.getString(10),
                row.getString(11), row.getTimestamp(12).toInstant(), row.getString(13).isBlank() ? List.of() : List.of(row.getString(13).split(", "))), organizationId, documentId);
    }

    private void saveProcessingRun(UUID org, UUID version, String stage, String status, String error) {
        Instant now = Instant.now();
        jdbc.update("""
            insert into document_processing_runs(organization_id,document_version_id,stage,tool_name,tool_version,status,started_at,finished_at,error_code)
            values(?,?,?,'Apache Tika','4.2.0',?,?,?,?)
            """, org, version, stage, status, Timestamp.from(now), Timestamp.from(now), error == null ? null : "EXTRACTION_FAILED");
    }

    private Extraction extract(byte[] content, String filename, DocumentType type) {
        try (TikaInputStream stream = TikaInputStream.get(content)) {
            var metadata = new Metadata(); metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, safeFilename(filename));
            PipesForkResult result = tika.parse(stream, metadata, new ParseContext());
            if (!result.isSuccess() || result.getMetadataList().isEmpty()) return new Extraction("", "application/octet-stream", "No se pudo extraer el texto dentro del tiempo y memoria permitidos.");
            Metadata parsedMetadata = result.getMetadataList().getFirst();
            String mime = parsedMetadata.get(HttpHeaders.CONTENT_TYPE);
            if (!allowedMime(type, mime)) throw new IllegalArgumentException("El contenido real del archivo no coincide con el tipo permitido.");
            String text = result.getContent() == null ? parsedMetadata.get(TikaCoreProperties.TIKA_CONTENT) : result.getContent();
            if (text == null) text = "";
            text = text.strip();
            if (text.length() > MAX_EXTRACTED_CHARS) text = text.substring(0, MAX_EXTRACTED_CHARS);
            return new Extraction(text, mime == null ? "application/octet-stream" : mime, null);
        } catch (IllegalArgumentException invalidType) {
            throw invalidType;
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            return new Extraction("", "application/octet-stream", "No se pudo extraer el texto; comprueba el archivo o inténtalo otra vez.");
        }
    }
    private static boolean allowedMime(DocumentType type, String mime) {
        if (mime == null) return false;
        if (type == DocumentType.PDF) return mime.equalsIgnoreCase("application/pdf");
        if (type == DocumentType.DOCX) return mime.equalsIgnoreCase("application/vnd.openxmlformats-officedocument.wordprocessingml.document") || mime.equalsIgnoreCase("application/x-tika-ooxml");
        return mime.startsWith("text/") || mime.equals("application/json") || mime.equals("application/yaml") || mime.equals("application/x-yaml") || mime.equals("application/vnd.oai.openapi");
    }

    private static List<String> chunks(String text) {
        var result = new ArrayList<String>();
        String remaining = text.replace("\r\n", "\n").strip();
        while (!remaining.isEmpty()) {
            int end = Math.min(CHUNK_CHARS, remaining.length());
            if (end < remaining.length()) { int boundary = remaining.lastIndexOf('\n', end); if (boundary > end / 2) end = boundary; }
            String part = remaining.substring(0, end).strip(); if (!part.isEmpty()) result.add(part);
            remaining = remaining.substring(end).stripLeading();
        }
        return result;
    }

    private String visibilityClause(List<UUID> repositories, List<Object> args, String docAlias) {
        if (repositories.isEmpty()) return "not exists(select 1 from document_repositories hidden where hidden.organization_id=" + docAlias + ".organization_id and hidden.document_id=" + docAlias + ".id)";
        args.addAll(repositories);
        return "(not exists(select 1 from document_repositories hidden where hidden.organization_id=" + docAlias + ".organization_id and hidden.document_id=" + docAlias + ".id) or exists(select 1 from document_repositories visible where visible.organization_id=" + docAlias + ".organization_id and visible.document_id=" + docAlias + ".id and visible.repository_id in (" + String.join(",", java.util.Collections.nCopies(repositories.size(), "?")) + ")))";
    }

    private String repositoryNames(List<UUID> repositories, List<Object> args, String documentAlias) {
        if (repositories.isEmpty()) return "select null::text names";
        args.addAll(repositories);
        return "select string_agg(r.full_name,', ' order by r.full_name) names from document_repositories dr join repositories r on r.organization_id=dr.organization_id and r.id=dr.repository_id where dr.organization_id=" + documentAlias + ".organization_id and dr.document_id=" + documentAlias + ".id and dr.repository_id in (" + String.join(",", java.util.Collections.nCopies(repositories.size(), "?")) + ")";
    }

    private boolean tableExists(String table) { return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from information_schema.tables where table_schema=current_schema() and table_name=?)", Boolean.class, table)); }
    private Path resolve(String key) { Path path = root.resolve(key).normalize(); if (!path.startsWith(root) || key.contains("..")) throw new IllegalArgumentException("Storage key inválida."); return path; }
    private static String extension(String filename) { int dot = filename.lastIndexOf('.'); return dot < 0 ? ".bin" : filename.substring(dot).toLowerCase(java.util.Locale.ROOT); }
    private static String safeFilename(String filename) { String leaf = Path.of(filename).getFileName().toString(); return leaf.length() > 255 ? leaf.substring(leaf.length() - 255) : leaf; }
    private static String sha256(byte[] content) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); } catch (Exception failure) { throw new IllegalStateException(failure); } }
    private record Extraction(String text, String mimeType, String error) { }
}
