package com.dependencyintelligence.identity.application.usecase;
import com.dependencyintelligence.identity.application.dto.PlatformModels.*;
import com.dependencyintelligence.identity.application.exception.PermissionDeniedException;
import com.dependencyintelligence.identity.application.port.in.ManagePlatform;
import com.dependencyintelligence.identity.application.port.out.PlatformStore;
import com.dependencyintelligence.identity.domain.PersonalName;
import com.dependencyintelligence.organization.application.exception.AccessConflictException;
import java.util.Set;
import java.util.UUID;
public final class ManagePlatformUseCase implements ManagePlatform {
    private final PlatformStore store;
    public ManagePlatformUseCase(PlatformStore store){this.store=store;}
    private void authorize(UUID user){if(!store.administrator(user)) throw new PermissionDeniedException();}
    public Overview overview(UUID actor){authorize(actor);return store.overview();}
    public void execute(UUID actor,Action action){
        if(action==null || action.resource()==null || action.id()==null || action.operation()==null || action.reason()==null || action.reason().strip().length()<3 || action.reason().strip().length()>500) throw new IllegalArgumentException("Indica una acción válida y un motivo de 3 a 500 caracteres");
        store.lockAdministration(); authorize(actor);
        if(action.resource()==Resource.USER){
            var target=store.user(action.id());
            if(action.operation()==Operation.APPROVE && (!target.emailVerified() || !target.accountType().equals("OWNER"))) throw new AccessConflictException("Solo se aprueban responsables con correo verificado");
            if(action.operation()==Operation.ENABLE && (!target.emailVerified() || !target.admissionStatus().equals("APPROVED"))) throw new AccessConflictException("La cuenta debe estar verificada y aprobada");
            if(Set.of(Operation.DISABLE,Operation.REMOVE,Operation.REJECT).contains(action.operation())){
                if(actor.equals(action.id())) throw new AccessConflictException("No puedes deshabilitar tu propia cuenta de administración");
                if(target.administrator() && target.active() && store.activeAdministrators()<=1) throw new AccessConflictException("Debe conservarse un administrador activo");
            }
        } else if(action.resource()==Resource.MEMBERSHIP){
            if(action.operation()!=Operation.REMOVE && action.operation()!=Operation.DISABLE) throw new IllegalArgumentException("Acción de membresía no válida");
        } else if(action.operation()!=Operation.ENABLE && action.operation()!=Operation.DISABLE) throw new IllegalArgumentException("Solo se permite habilitar o deshabilitar este recurso");
        store.change(actor,action);store.audit(actor,action);
    }
    public void updateName(UUID user,String name){store.updateName(user,new PersonalName(name).value());}
}
