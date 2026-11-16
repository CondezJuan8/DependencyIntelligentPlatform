package com.dependencyintelligence.identity.infrastructure.oauth;

import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** An empty repository keeps local authentication available until real clients are enabled. */
public final class ExternalClientRegistrations implements ClientRegistrationRepository, Iterable<ClientRegistration> {
    private final Map<String, ClientRegistration> registrations = new LinkedHashMap<>();

    public ExternalClientRegistrations(IdentityProviderProperties properties, ExternalApplicationUrls urls) {
        add("google", properties.google(), CommonOAuth2Provider.GOOGLE, urls);
        add("github", properties.github(), CommonOAuth2Provider.GITHUB, urls);
    }

    private void add(String id, IdentityProviderProperties.Client properties, CommonOAuth2Provider provider, ExternalApplicationUrls urls) {
        if (properties == null || !properties.enabled()) return;
        if (properties.clientId() == null || properties.clientId().isBlank()
                || properties.clientSecret() == null || properties.clientSecret().isBlank()) {
            throw new IllegalStateException("Enabled " + id + " login requires client ID and client secret");
        }
        var builder = provider.getBuilder(id).clientId(properties.clientId()).clientSecret(properties.clientSecret())
            .redirectUri(urls.callback(id));
        if (id.equals("google")) builder.scope("openid", "profile", "email");
        else builder.scope("read:user", "user:email");
        registrations.put(id, builder.build());
    }

    @Override public ClientRegistration findByRegistrationId(String id) { return registrations.get(id); }
    @Override public Iterator<ClientRegistration> iterator() { return registrations.values().iterator(); }
}
