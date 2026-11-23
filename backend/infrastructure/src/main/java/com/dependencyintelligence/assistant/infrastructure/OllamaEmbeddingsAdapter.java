package com.dependencyintelligence.assistant.infrastructure;

import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.Embedding;
import com.dependencyintelligence.assistant.application.port.out.AssistantPorts.Embeddings;
import java.time.Duration;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/** Ollama adapter. Model/revision are supplied by deployment configuration, never guessed. */
@Component
public final class OllamaEmbeddingsAdapter implements Embeddings {
    private final RestClient client;
    private final String model;
    private final String revision;

    public OllamaEmbeddingsAdapter(RestClient.Builder builder,
            @Value("${app.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${app.ai.ollama.embedding-model:}") String model,
            @Value("${app.ai.ollama.embedding-revision:}") String revision,
            @Value("${app.ai.request-timeout:PT45S}") Duration timeout) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        this.client = builder.baseUrl(baseUrl).requestFactory(factory).build();
        this.model = model.strip(); this.revision = revision.strip();
    }

    @Override public Embedding embed(String text) {
        return embedBatch(List.of(text)).getFirst();
    }

    @Override public List<Embedding> embedBatch(List<String> texts) {
        if (model.isBlank() || revision.isBlank()) throw new AiProviderUnavailableException("Configura en Ollama el modelo de embeddings y su digest/revisión verificada antes de usar el asistente.");
        try {
            verifyInstalledModel();
            Map<?, ?> body = client.post().uri("/api/embed").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", model, "input", texts, "truncate", false)).retrieve().body(Map.class);
            Object raw = body == null ? null : body.get("embeddings");
            if (!(raw instanceof List<?> rows) || rows.size() != texts.size())
                throw new AiProviderUnavailableException("Ollama no devolvió un vector por cada texto solicitado.");
            List<Embedding> result = new ArrayList<>(rows.size());
            int dimension = -1;
            for (Object row : rows) {
                if (!(row instanceof List<?> values) || values.isEmpty()) throw new AiProviderUnavailableException("Ollama devolvió un embedding vacío.");
                if (dimension >= 0 && dimension != values.size()) throw new AiProviderUnavailableException("Ollama devolvió embeddings con dimensiones distintas.");
                dimension = values.size();
                float[] vector = new float[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    if (!(values.get(i) instanceof Number number)) throw new AiProviderUnavailableException("Ollama devolvió un vector con valores no numéricos.");
                    vector[i] = number.floatValue();
                    if (!Float.isFinite(vector[i])) throw new AiProviderUnavailableException("Ollama devolvió un vector no finito.");
                }
                result.add(new Embedding(vector, model, revision));
            }
            return List.copyOf(result);
        } catch (AiProviderUnavailableException failure) { throw failure; }
        catch (RuntimeException failure) { throw new AiProviderUnavailableException("No se pudo obtener el embedding desde Ollama. Revisa que el servicio y el modelo configurado estén disponibles.", failure); }
    }

    private void verifyInstalledModel() {
        Map<?, ?> tags = client.get().uri("/api/tags").retrieve().body(Map.class);
        Object raw = tags == null ? null : tags.get("models");
        if (!(raw instanceof List<?> models)) throw new AiProviderUnavailableException("Ollama no devolvió su lista de modelos instalados.");
        boolean matches = models.stream().filter(Map.class::isInstance).map(Map.class::cast)
            .anyMatch(item -> model.equals(item.get("name")) && revision.equals(item.get("digest")));
        if (!matches) throw new AiProviderUnavailableException("El modelo Ollama o su digest no coinciden con la configuración. Revisa /api/tags y configura el nombre y digest instalados.");
    }
}
