package com.dependencyintelligence.identity.api;
import com.dependencyintelligence.identity.application.dto.PlatformModels.*;
import com.dependencyintelligence.identity.application.port.in.ManagePlatform;
import com.dependencyintelligence.identity.application.port.in.FindActiveAccount;
import com.dependencyintelligence.identity.application.dto.AccountResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.UUID;
@RestController
public class PlatformController {
    private final ManagePlatform platform;private final FindActiveAccount accounts;
    public PlatformController(ManagePlatform platform,FindActiveAccount accounts){this.platform=platform;this.accounts=accounts;}
    @GetMapping("/api/platform/overview") public Overview overview(Principal user){return platform.overview(UUID.fromString(user.getName()));}
    @PostMapping("/api/platform/actions") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void action(Principal user,@Valid @RequestBody Request request){platform.execute(UUID.fromString(user.getName()),new Action(request.resource(),request.id(),request.operation(),request.reason()));}
    @PostMapping("/api/account/profile") public AccountResult profile(Principal user,@Valid @RequestBody Profile request){UUID id=UUID.fromString(user.getName());platform.updateName(id,request.displayName());return accounts.execute(id);}
    public record Request(@NotNull Resource resource,@NotNull UUID id,@NotNull Operation operation,@NotBlank @Size(min=3,max=500) String reason){}
    public record Profile(@NotBlank @Size(max=200) String displayName){}
}
