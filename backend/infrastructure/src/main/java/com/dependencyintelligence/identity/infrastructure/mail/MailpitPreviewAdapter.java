package com.dependencyintelligence.identity.infrastructure.mail;
import com.dependencyintelligence.identity.application.port.out.DevelopmentMailbox;
import com.dependencyintelligence.identity.application.port.in.ReadDevelopmentMail.Message;
import com.dependencyintelligence.identity.application.exception.AccountMailUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
@Component
public class MailpitPreviewAdapter implements DevelopmentMailbox{
    private final RestClient client;private final String origin;private final boolean enabled;
    public MailpitPreviewAdapter(@Value("${app.mail.preview-enabled:false}")boolean enabled,@Value("${app.mail.preview-url:http://127.0.0.1:8025}")String origin,@Value("${app.mail.preview-user:}")String user,@Value("${app.mail.preview-password:}")String password){
        this.enabled=enabled;this.origin=origin.replaceAll("/+$","");
        var uri=URI.create(origin);if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||!(uri.getScheme().equals("http")||uri.getScheme().equals("https")))throw new IllegalArgumentException("URL del correo local no válida");
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());factory.setReadTimeout(Duration.ofSeconds(3));
        var builder=RestClient.builder().requestFactory(factory);if(!user.isBlank())builder.defaultHeaders(headers->headers.setBasicAuth(user,password));this.client=builder.build();
    }
    public List<Message> find(String email,String required){
        if(!enabled)throw new AccountMailUnavailableException();
        try{
            var result=new ArrayList<Message>();
            JsonNode page=client.get().uri(URI.create(origin+"/api/v1/search?query="+URLEncoder.encode("to:"+email,StandardCharsets.UTF_8))).retrieve().body(JsonNode.class);
            if(page==null)return List.of();int count=0;
            for(var item:page.path("messages")){
                if(++count>40)break;
                String id=item.path("ID").asText();if(!id.matches("[A-Za-z0-9-]+"))continue;
                var message=client.get().uri(origin+"/api/v1/message/"+id).retrieve().body(JsonNode.class);
                if(message==null)continue;
                boolean recipient=false;for(var address:message.path("To"))if(address.path("Address").asText().equalsIgnoreCase(email))recipient=true;
                if(!recipient)continue;
                String text=message.path("Text").asText();
                if(text.contains(required)&&text.length()<=40000)result.add(new Message(message.path("Subject").asText(),text));
            }
            return List.copyOf(result);
        }catch(RuntimeException e){throw new AccountMailUnavailableException();}
    }
}
