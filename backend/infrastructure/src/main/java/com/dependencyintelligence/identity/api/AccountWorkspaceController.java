package com.dependencyintelligence.identity.api;
import com.dependencyintelligence.identity.application.port.in.FindAccountWorkspace;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
@RestController
public class AccountWorkspaceController{
    private final FindAccountWorkspace workspace;
    public AccountWorkspaceController(FindAccountWorkspace workspace){this.workspace=workspace;}
    @GetMapping("/api/account/workspace") public List<FindAccountWorkspace.Repository> list(Principal user){return workspace.repositories(UUID.fromString(user.getName()));}
}
