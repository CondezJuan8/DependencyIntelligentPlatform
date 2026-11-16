package com.dependencyintelligence.identity.infrastructure.configuration;
import com.dependencyintelligence.identity.application.port.in.ReadDevelopmentMail;
import com.dependencyintelligence.identity.application.port.out.*;
import com.dependencyintelligence.identity.application.usecase.ReadDevelopmentMailUseCase;
import com.dependencyintelligence.organization.application.port.out.*;
import com.dependencyintelligence.shared.infrastructure.persistence.TenantTransactions;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;
@Configuration
public class DevelopmentMailConfiguration{
 @Bean ReadDevelopmentMail readDevelopmentMail(AccountStore accounts,DevelopmentMailbox mailbox,InvitationStore invitations,OrganizationAccessStore access,TenantTransactions tenants,PlatformTransactionManager manager){
    var delegate=new ReadDevelopmentMailUseCase(accounts,mailbox,invitations,access);var tx=new TransactionTemplate(manager);
    return new ReadDevelopmentMail(){public List<Message> account(UUID actor,UUID recipient){return tx.execute(status->delegate.account(actor,recipient));}public List<Message> invitation(UUID actor,UUID org,UUID id){return tenants.execute(org,()->delegate.invitation(actor,org,id));}};
 }
}
