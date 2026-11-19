package com.dependencyintelligence.scan.infrastructure.persistence;

import com.dependencyintelligence.scan.application.dto.*;
import com.dependencyintelligence.scan.application.port.out.MavenAnalysisStore;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.util.*;

@Component
public final class JdbcMavenAnalysisAdapter implements MavenAnalysisStore {
  private final JdbcTemplate jdbc; private final TenantTransactions tenants;
  public JdbcMavenAnalysisAdapter(JdbcTemplate jdbc, TenantTransactions tenants){this.jdbc=jdbc;this.tenants=tenants;}
  public MavenInventory save(UUID scan,UUID org,UUID repo,String provider,String sha,String commit,java.time.Instant at,ManifestAnalysis data){
    return tenants.execute(org,()->{
      jdbc.update("""
        insert into scans(id,organization_id,repository_id,git_reference,status,requested_at,started_at,finished_at,
          origin,scanner_name,scanner_version,trigger_type,source_sha256,manifest_count,unresolved_count)
        values(?,?,?,?, 'COMPLETED',?,?,?,?,'maven-manifest','1','MANUAL',?,?,?)
        """,scan,org,repo,commit,Timestamp.from(at),Timestamp.from(at),Timestamp.from(at),"local".equals(provider)?"LOCAL_UPLOAD_SCAN":"REPOSITORY_SCAN",sha,data.manifestCount(),data.unresolvedCount());
      if(commit!=null){jdbc.update("insert into repository_commits(organization_id,repository_id,commit_sha) values(?,?,?) on conflict do nothing",org,repo,commit);jdbc.update("update scans set repository_commit_id=(select id from repository_commits where organization_id=? and repository_id=? and commit_sha=?) where organization_id=? and id=?",org,repo,commit,org,scan);}
      UUID eco=jdbc.queryForObject("select id from package_ecosystems where code='maven'",UUID.class);
      for(var d:data.dependencies()){var c=d.coordinates();UUID pkg=jdbc.queryForObject("insert into components(organization_id,package_ecosystem_id,namespace,name) values(?,?,?,?) on conflict on constraint uq_component_identity do update set id=components.id returning id",UUID.class,org,eco,c.namespace(),c.name());UUID ver=jdbc.queryForObject("insert into component_versions(organization_id,component_id,version) values(?,?,?) on conflict(organization_id,component_id,version) do update set id=component_versions.id returning id",UUID.class,org,pkg,c.version());jdbc.update("insert into component_identifiers(organization_id,component_version_id,identifier_type,value) values(?,?,'PURL',?) on conflict do nothing",org,ver,c.purl());String ref=d.manifestPath()+"#"+c.namespace()+":"+c.name()+":"+c.version();jdbc.update("insert into scan_components(organization_id,scan_id,bom_ref,component_version_id,dependency_type,dependency_scope_id) values(?,?,?,?,'DIRECT',(select id from dependency_scopes where code=?)) on conflict do nothing",org,scan,ref,ver,d.scope());}
      return latest0(org,repo).orElseThrow();});
  }
  public Optional<MavenInventory> latest(UUID org,UUID repo){return tenants.execute(org,()->latest0(org,repo));}
  private Optional<MavenInventory> latest0(UUID org,UUID repo){var rows=jdbc.query("select id,origin,source_sha256,git_reference,finished_at,manifest_count,unresolved_count from scans where organization_id=? and repository_id=? and scanner_name='maven-manifest' and status='COMPLETED' order by finished_at desc limit 1",(r,n)->new Object[]{r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4),r.getTimestamp(5).toInstant(),r.getInt(6),r.getInt(7)},org,repo);if(rows.isEmpty())return Optional.empty();Object[] s=rows.getFirst();UUID scan=(UUID)s[0];var deps=jdbc.query("select sc.bom_ref,pe.code,c.namespace,c.name,cv.version,ds.code from scan_components sc join component_versions cv on cv.organization_id=sc.organization_id and cv.id=sc.component_version_id join components c on c.organization_id=cv.organization_id and c.id=cv.component_id join package_ecosystems pe on pe.id=c.package_ecosystem_id join dependency_scopes ds on ds.id=sc.dependency_scope_id where sc.organization_id=? and sc.scan_id=? order by c.name",(r,n)->{String ref=r.getString(1);int i=ref.indexOf('#');return new ManifestDependency(i<0?"pom.xml":ref.substring(0,i),new com.dependencyintelligence.component.domain.PackageCoordinates(r.getString(2),r.getString(3),r.getString(4),r.getString(5),"pkg:maven/"+r.getString(3)+"/"+r.getString(4)+"@"+r.getString(5)),r.getString(6));},org,scan);return Optional.of(new MavenInventory(scan,"LOCAL_UPLOAD_SCAN".equals(s[1])?"local":"github",(String)s[2],(String)s[3],(java.time.Instant)s[4],(int)s[5],(int)s[6],deps));}
}
