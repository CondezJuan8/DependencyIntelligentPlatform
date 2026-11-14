package com.dependencyintelligence.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.dependencyintelligence", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {
    @ArchTest
    static final ArchRule domainDependsOnlyOnDomainAndJava = classes().that().resideInAPackage("..domain..")
        .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "com.dependencyintelligence..domain..");

    @ArchTest
    static final ArchRule applicationDependsOnlyOnApplicationDomainAndJava = classes()
        .that().resideInAPackage("..application..")
        .should().onlyDependOnClassesThat().resideInAnyPackage("java..",
            "com.dependencyintelligence..application..", "com.dependencyintelligence..domain..");

    @ArchTest
    static final ArchRule persistenceEntitiesBelongToInfrastructure = classes()
        .that().areAnnotatedWith(jakarta.persistence.Entity.class)
        .should().resideInAPackage("..infrastructure.persistence..");

    @ArchTest
    static final ArchRule controllersBelongToInboundAdapters = classes()
        .that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
        .should().resideInAPackage("..api..");
}
