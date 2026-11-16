package com.dependencyintelligence.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Isolated provider fixture. Uses real HTTP, signed OIDC tokens and enforced PKCE. */
final class LocalOAuthProvider implements AutoCloseable {
    record Profile(String subject, String email, boolean verified, String hostedDomain) { }
    record IssuedCode(String provider, Map<String, String> authorization, Profile profile, String defect) { }
    private final HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private final RSAKey key;
    private final RSAKey otherKey;
    private final Map<String, IssuedCode> codes = new ConcurrentHashMap<>();
    private final Map<String, Profile> tokens = new ConcurrentHashMap<>();
    final AtomicInteger tokenRequests = new AtomicInteger();
    final AtomicInteger pkceValidated = new AtomicInteger();

    LocalOAuthProvider() throws Exception {
        key = new RSAKeyGenerator(2048).keyID("fixture-key").generate();
        otherKey = new RSAKeyGenerator(2048).keyID("fixture-key").generate();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    String base() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    ClientRegistration client(String provider) {
        var builder = ClientRegistration.withRegistrationId(provider).clientName(provider)
            .clientId(provider + "-fixture").clientSecret("fixture-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("http://localhost:8088/api/auth/oauth2/callback/" + provider)
            .authorizationUri(base() + "/" + provider + "/authorize")
            .tokenUri(base() + "/" + provider + "/token")
            .userInfoUri(base() + "/" + provider + "/user");
        if (provider.equals("google")) {
            builder.scope("openid", "profile", "email").jwkSetUri(base() + "/google/jwks")
                .issuerUri(base() + "/google").userNameAttributeName("sub");
        } else builder.scope("read:user", "user:email").userNameAttributeName("id");
        return builder.build();
    }

    String code(String provider, Map<String, String> authorization, Profile profile, String defect) {
        String code = "fixture-code-" + UUID.randomUUID();
        codes.put(code, new IssuedCode(provider, Map.copyOf(authorization), profile, defect));
        return code;
    }

    static Map<String, String> parameters(String query) {
        Map<String, String> values = new HashMap<>();
        if (query == null) return values;
        for (String entry : query.split("&")) {
            String[] pair = entry.split("=", 2);
            values.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "");
        }
        return values;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/google/jwks")) { respond(exchange, 200, new JWKSet(key.toPublicJWK()).toJSONObject()); return; }
            if (path.endsWith("/token")) { token(exchange); return; }
            Profile profile = tokens.get(exchange.getRequestHeaders().getFirst("Authorization"));
            if (profile == null) { respond(exchange, 401, Map.of("error", "invalid_token")); return; }
            if (path.equals("/github/user/emails")) {
                respond(exchange, 200, profile.email() == null ? List.of() : List.of(Map.of(
                    "email", profile.email(), "primary", true, "verified", profile.verified())));
            } else if (path.equals("/github/user")) {
                respond(exchange, 200, Map.of("id", Long.parseLong(profile.subject()), "login", "fixture-login", "name", "Fixture GitHub",
                    "email", "untrusted-public-profile@example.test"));
            } else respond(exchange, 404, Map.of("error", "not_found"));
        } catch (Exception exception) { respond(exchange, 500, Map.of("error", "fixture_error")); }
    }

    private void token(HttpExchange exchange) throws Exception {
        tokenRequests.incrementAndGet();
        Map<String, String> body = parameters(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        IssuedCode code = codes.remove(body.get("code"));
        if (code == null) { respond(exchange, 400, Map.of("error", "invalid_grant")); return; }
        String credentials = Base64.getEncoder().encodeToString((code.provider() + "-fixture:fixture-secret").getBytes(StandardCharsets.UTF_8));
        String verifier = body.getOrDefault("code_verifier", "");
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
            .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        if (!Objects.equals(exchange.getRequestHeaders().getFirst("Authorization"), "Basic " + credentials)
                || !challenge.equals(code.authorization().get("code_challenge"))
                || !"authorization_code".equals(body.get("grant_type"))
                || !Objects.equals(body.get("redirect_uri"), code.authorization().get("redirect_uri"))) {
            respond(exchange, 400, Map.of("error", "invalid_grant")); return;
        }
        pkceValidated.incrementAndGet();
        String accessToken = "fixture-access-" + UUID.randomUUID();
        tokens.put("Bearer " + accessToken, code.profile());
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", accessToken);
        response.put("token_type", "Bearer");
        response.put("expires_in", 300);
        response.put("scope", code.authorization().get("scope"));
        if (code.provider().equals("google")) response.put("id_token", idToken(code));
        respond(exchange, 200, response);
    }

    private String idToken(IssuedCode code) throws Exception {
        var claims = new JWTClaimsSet.Builder().subject(code.profile().subject())
            .issuer(code.defect().equals("issuer") ? base() + "/wrong" : base() + "/google")
            .audience(code.defect().equals("audience") ? "wrong-client" : "google-fixture")
            .issueTime(Date.from(Instant.now().minusSeconds(10)))
            .expirationTime(Date.from(Instant.now().plusSeconds(code.defect().equals("expired") ? -600 : 300)))
            .claim("nonce", code.defect().equals("nonce") ? "wrong-nonce" : code.authorization().get("nonce"))
            .claim("name", "Fixture Google");
        if (code.profile().email() != null) claims.claim("email", code.profile().email()).claim("email_verified", code.profile().verified());
        if (code.profile().hostedDomain() != null) claims.claim("hd", code.profile().hostedDomain());
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("fixture-key").type(JOSEObjectType.JWT).build(), claims.build());
        jwt.sign(new RSASSASigner(code.defect().equals("signature") ? otherKey : key));
        return jwt.serialize();
    }

    private void respond(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] content = json.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, content.length);
        exchange.getResponseBody().write(content);
        exchange.close();
    }
    @Override public void close() { server.stop(0); }
}
