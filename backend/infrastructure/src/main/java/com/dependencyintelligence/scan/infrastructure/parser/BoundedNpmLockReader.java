package com.dependencyintelligence.scan.infrastructure.parser;
import com.dependencyintelligence.component.domain.PackageCoordinates;
import com.dependencyintelligence.scan.application.dto.*;
import com.dependencyintelligence.scan.application.port.out.NpmLockfileReader;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;

/** Parses npm lockfile v2/v3 as data; never runs npm or project scripts. */
@Component public final class BoundedNpmLockReader implements NpmLockfileReader {
 private static final int MAX_LOCK=5*1024*1024,MAX_ENTRIES=5000,MAX_PACKAGES=30000;
 private final ObjectMapper json=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
 public ManifestAnalysis read(InputStream input){byte[] lock=null;long expanded=0;int entries=0;
  try(var zip=new ZipInputStream(input)){for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry()){if(++entries>MAX_ENTRIES)throw bad("ZIP con demasiados archivos.");String p=e.getName();if(p.startsWith("/")||p.contains("\\")||Arrays.asList(p.split("/",-1)).contains(".."))throw bad("Ruta no válida en ZIP.");if(!e.isDirectory()&&p.endsWith("package-lock.json")){ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] buf=new byte[8192];for(int n;(n=zip.read(buf))!=-1;){expanded+=n;if(expanded>120L*1024*1024||b.size()+n>MAX_LOCK)throw bad("ZIP o package-lock supera el límite.");b.write(buf,0,n);}if(lock!=null)throw bad("Más de un package-lock.json; analiza cada aplicación por separado.");lock=b.toByteArray();}else{byte[] buf=new byte[8192];for(int n;(n=zip.read(buf))!=-1;){expanded+=n;if(expanded>120L*1024*1024)throw bad("ZIP supera el límite descomprimido.");}}zip.closeEntry();}}
  catch(IOException e){throw new IllegalArgumentException("No se pudo leer el ZIP npm.",e);}if(lock==null)return new ManifestAnalysis(List.of(),0,0);
  try{JsonNode root=json.readTree(lock);int lv=root.path("lockfileVersion").asInt(0);if(lv<2||lv>3)throw bad("Solo se admite package-lock v2 o v3.");JsonNode packages=root.path("packages");if(!packages.isObject()||packages.size()>MAX_PACKAGES)throw bad("Mapa packages inválido o demasiado grande.");Set<String> direct=new HashSet<>();Set<String> dev=new HashSet<>(),optional=new HashSet<>();JsonNode top=packages.path("");for(String section:List.of("dependencies","devDependencies","optionalDependencies")){JsonNode deps=top.path(section);if(deps.isObject()){Iterator<String> it=deps.fieldNames();while(it.hasNext()){String n=it.next();direct.add(n);if(section.equals("devDependencies"))dev.add(n);if(section.equals("optionalDependencies"))optional.add(n);}}}
   List<ManifestDependency> out=new ArrayList<>();Iterator<Map.Entry<String,JsonNode>> it=packages.fields();while(it.hasNext()){var en=it.next();String path=en.getKey();JsonNode node=en.getValue();if(path.isEmpty()||node.path("link").asBoolean(false)||!node.path("version").isTextual())continue;String name=node.path("name").asText("");if(name.isBlank()){int i=path.lastIndexOf("node_modules/");if(i<0)continue;name=path.substring(i+13);if(name.startsWith("@")){int slash=name.indexOf('/',1);if(slash>0){int next=name.indexOf('/',slash+1);if(next>0)name=name.substring(0,next);}}else{int slash=name.indexOf('/');if(slash>0)name=name.substring(0,slash);}}String ver=node.path("version").asText();if(name.length()>512||ver.isBlank()||ver.length()>255)continue;boolean isDirect=direct.contains(name);String scope=optional.contains(name)||node.path("optional").asBoolean(false)?"optional":dev.contains(name)||node.path("dev").asBoolean(false)?"development":"runtime";String purl="pkg:npm/"+URLEncoder.encode(name,StandardCharsets.UTF_8).replace("+","%20")+"@"+ver;var c=new PackageCoordinates("npm",null,name,ver,purl);out.add(new ManifestDependency(path,c,scope,isDirect?"DIRECT":"TRANSITIVE"));}
   return new ManifestAnalysis(out,1,0);
  }catch(IOException e){throw new IllegalArgumentException("package-lock.json no es JSON válido.",e);}}
 private static IllegalArgumentException bad(String s){return new IllegalArgumentException(s);}
}
