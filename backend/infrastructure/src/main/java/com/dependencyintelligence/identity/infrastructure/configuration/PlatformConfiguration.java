package com.dependencyintelligence.identity.infrastructure.configuration;
import com.dependencyintelligence.identity.application.dto.PlatformModels.*;
import com.dependencyintelligence.identity.application.port.in.ManagePlatform;
import com.dependencyintelligence.identity.application.port.in.DecidePlatformAdmission;
import com.dependencyintelligence.identity.application.port.out.AccountTokenCodec;
import com.dependencyintelligence.identity.application.port.out.PlatformAdmissionDecisionStore;
import com.dependencyintelligence.identity.application.port.out.PlatformStore;
import com.dependencyintelligence.identity.application.usecase.ManagePlatformUseCase;
import com.dependencyintelligence.identity.application.usecase.DecidePlatformAdmissionUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.time.Clock;
import java.util.function.Supplier;
@Configuration
public class PlatformConfiguration {
    @Bean ManagePlatform managePlatform(PlatformStore store,JdbcTemplate jdbc,PlatformTransactionManager manager){
        var delegate=new ManagePlatformUseCase(store);var tx=new TransactionTemplate(manager);
        return new ManagePlatform(){
            private <T>T run(UUID user,Supplier<T> work){return tx.execute(status->{jdbc.queryForObject("select set_config('app.user_id',?,true)",String.class,user.toString());return work.get();});}
            public Overview overview(UUID actor){return run(actor,()->delegate.overview(actor));}
            public void execute(UUID actor,Action action){run(actor,()->{delegate.execute(actor,action);return null;});}
            public void updateName(UUID user,String name){run(user,()->{delegate.updateName(user,name);return null;});}
        };
    }
    @Bean DecidePlatformAdmission decidePlatformAdmission(PlatformAdmissionDecisionStore decisions, AccountTokenCodec tokens,
            ManagePlatform platform, Clock clock, PlatformTransactionManager manager) {
        var delegate = new DecidePlatformAdmissionUseCase(decisions, tokens, platform, clock);
        var tx = new TransactionTemplate(manager);
        return token -> tx.execute(status -> delegate.execute(token));
    }
}
