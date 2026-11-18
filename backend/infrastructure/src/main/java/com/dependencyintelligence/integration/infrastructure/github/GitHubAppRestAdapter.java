package com.dependencyintelligence.integration.infrastructure.github;

import com.dependencyintelligence.integration.application.dto.GitHubModels.Candidate;
import com.dependencyintelligence.integration.application.port.out.GitHubAppGateway;
import com.dependencyintelligence.integration.infrastructure.configuration.GitHubAppProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class GitHubAppRestAdapter implements GitHubAppGateway {
    private static final String API_VERSION = "2026-03-10";
    private final GitHubAppProperties properties;
    private final ObjectMapper json;
    private final RestClient client;
    private final RestClient website;
    private final Clock clock;

    public GitHubAppRestAdapter(GitHubAppProperties properties, ObjectMapper json, Clock clock) {
        this.properties = properties; this.json = json; this.clock = clock;
        var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http); factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = RestClient.builder().baseUrl(properties.apiBaseUrl()).requestFactory(factory)
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", API_VERSION).build();
        this.website = RestClient.builder().baseUrl("https://github.com").requestFactory(factory)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
    }

    @Override public String installationUrl(String state) {
        requireConfigured();
        return "https://github.com/apps/" + properties.slug() + "/installations/new?state=" + state;
    }

    @Override public String userAuthorizationUrl(String state, String codeChallenge) {
        requireAuthorizationConfigured();
        return UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
            .queryParam("client_id", properties.clientId()).queryParam("redirect_uri", properties.callbackUrl())
            .queryParam("state", state).queryParam("code_challenge", codeChallenge)
            .queryParam("code_challenge_method", "S256").build().encode().toUriString();
    }

    @Override public String exchangeAuthorizationCode(String code, String codeVerifier) {
        requireAuthorizationConfigured();
        try {
            var response = website.post().uri("/login/oauth/access_token").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("client_id", properties.clientId(), "client_secret", properties.clientSecret(), "code", code,
                    "redirect_uri", properties.callbackUrl(), "code_verifier", codeVerifier))
                .retrieve().body(UserTokenResponse.class);
            if (response == null || response.accessToken() == null || !response.accessToken().startsWith("ghu_")) throw upstreamFailure(null);
            return response.accessToken();
        } catch (RestClientException exception) { throw upstreamFailure(exception); }
    }

    @Override public void verifyUserCanAccessInstallation(String userAccessToken, long installationId) {
        try {
            var response = client.get().uri(uri -> uri.path("/user/installations/{id}/repositories")
                .queryParam("per_page", 1).build(installationId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userAccessToken).retrieve().body(RepositoryPage.class);
            if (response == null || response.totalCount() < 0) throw upstreamFailure(null);
        } catch (RestClientException exception) { throw upstreamFailure(exception); }
    }

    @Override public InstallationInfo verifyInstallation(long installationId) {
        requireConfigured();
        if (installationId < 1) throw new IllegalArgumentException("GitHub installation id is invalid.");
        try {
            var result = client.get().uri("/app/installations/{id}", installationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt()).retrieve().body(InstallationResponse.class);
            if (result == null || result.id() != installationId || result.appId() != Long.parseLong(properties.id())
                    || result.account() == null || result.account().login() == null) {
                throw new IllegalArgumentException("GitHub installation does not belong to this application.");
            }
            String kind = "Organization".equalsIgnoreCase(result.account().type()) ? "ORGANIZATION" : "User".equalsIgnoreCase(result.account().type()) ? "USER" : "";
            if (kind.isEmpty()) throw new IllegalArgumentException("GitHub account type is not supported.");
            return new InstallationInfo(result.id(), result.account().login(), kind);
        } catch (RestClientException exception) {
            throw upstreamFailure(exception);
        }
    }

    @Override public List<Candidate> repositories(long installationId) {
        requireConfigured();
        String token = installationToken(installationId);
        var result = new ArrayList<Candidate>();
        int page = 1; int total;
        do {
            int pageNumber = page;
            var response = client.get().uri(uri -> uri.path("/installation/repositories").queryParam("per_page", 100).queryParam("page", pageNumber).build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).retrieve().body(RepositoryPage.class);
            if (response == null || response.repositories() == null) throw upstreamFailure(null);
            total = response.totalCount();
            for (var repo : response.repositories()) {
                result.add(new Candidate(Long.toString(repo.id()), repo.fullName(), repo.defaultBranch() == null || repo.defaultBranch().isBlank() ? "main" : repo.defaultBranch(),
                    repo.htmlUrl(), normalizeVisibility(repo.visibility()), repo.archived()));
            }
            page++;
        } while (result.size() < total && page <= 20);
        if (result.size() < total) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La instalación contiene más de 2000 repositorios; acota el acceso en GitHub.");
        return List.copyOf(result);
    }

    private String installationToken(long installationId) {
        try {
            var token = client.post().uri("/app/installations/{id}/access_tokens", installationId)
                .contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt())
                .body(Map.of()).retrieve().body(InstallationToken.class);
            if (token == null || token.token() == null || token.token().isBlank()) throw upstreamFailure(null);
            return token.token();
        } catch (RestClientException exception) { throw upstreamFailure(exception); }
    }

    private String appJwt() {
        try {
            long now = clock.instant().getEpochSecond();
            String header = encode(json.writeValueAsBytes(Map.of("alg", "RS256", "typ", "JWT")));
            String claims = encode(json.writeValueAsBytes(Map.of("iat", now - 30, "exp", now + 540, "iss", properties.id())));
            String unsigned = header + "." + claims;
            Signature signature = Signature.getInstance("SHA256withRSA"); signature.initSign(privateKey());
            signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return unsigned + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        } catch (Exception exception) { throw new IllegalStateException("No se pudo firmar la autenticación de GitHub App."); }
    }

    private PrivateKey privateKey() throws Exception {
        byte[] pem = Base64.getDecoder().decode(properties.privateKeyBase64());
        String text = new String(pem, StandardCharsets.US_ASCII);
        String encoded = text.replaceAll("-----BEGIN [^-]+-----", "").replaceAll("-----END [^-]+-----", "").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(encoded);
        if (text.contains("BEGIN RSA PRIVATE KEY")) der = wrapPkcs1(der);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static byte[] wrapPkcs1(byte[] key) {
        byte[] algorithm = new byte[] {0x30,0x0d,0x06,0x09,0x2a,(byte)0x86,0x48,(byte)0x86,(byte)0xf7,0x0d,0x01,0x01,0x01,0x05,0x00};
        byte[] version = new byte[] {0x02,0x01,0x00};
        byte[] octets = der(0x04, key);
        ByteBuffer body = ByteBuffer.allocate(version.length + algorithm.length + octets.length).put(version).put(algorithm).put(octets);
        return der(0x30, body.array());
    }
    private static byte[] der(int tag, byte[] value) {
        int n=value.length, lengthBytes=n<128?1:n<=255?2:n<=65535?3:4;
        ByteBuffer result=ByteBuffer.allocate(1+lengthBytes+n);result.put((byte)tag);
        if(n<128)result.put((byte)n);else{int bytes=lengthBytes-1;result.put((byte)(0x80|bytes));for(int shift=(bytes-1)*8;shift>=0;shift-=8)result.put((byte)(n>>shift));}
        result.put(value);return result.array();
    }
    private static String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static String normalizeVisibility(String value) { return value == null ? "UNKNOWN" : switch(value.toLowerCase()) { case "public" -> "PUBLIC"; case "private" -> "PRIVATE"; case "internal" -> "INTERNAL"; default -> "UNKNOWN"; }; }
    private void requireConfigured() {
        if (!properties.enabled() || properties.slug() == null || properties.slug().isBlank() || properties.id() == null || properties.id().isBlank()
                || properties.privateKeyBase64() == null || properties.privateKeyBase64().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La GitHub App local todavía no está configurada.");
        }
    }
    private void requireAuthorizationConfigured() {
        requireConfigured();
        if (properties.clientId() == null || properties.clientId().isBlank() || properties.clientSecret() == null
                || properties.clientSecret().isBlank() || properties.callbackUrl() == null || properties.callbackUrl().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "La autorización de usuario de GitHub App aún no está configurada.");
        }
    }
    private static ResponseStatusException upstreamFailure(Exception ignored) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub no pudo completar la operación. Revisa permisos e instalación y vuelve a intentar.");
    }

    private record AccountResponse(String login, String type) { }
    private record InstallationResponse(long id, @JsonProperty("app_id") long appId, AccountResponse account) { }
    private record InstallationToken(String token) { }
    private record UserTokenResponse(@JsonProperty("access_token") String accessToken) { }
    private record RepositoryResponse(long id, @JsonProperty("full_name") String fullName,
            @JsonProperty("default_branch") String defaultBranch, @JsonProperty("html_url") String htmlUrl,
            String visibility, boolean archived) { }
    private record RepositoryPage(@JsonProperty("total_count") int totalCount, @JsonProperty("repositories") List<RepositoryResponse> repositories) { }
}
