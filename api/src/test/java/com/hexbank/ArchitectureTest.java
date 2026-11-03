package com.hexbank;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "com.hexbank", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String ROOT = "com.hexbank.";

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..",
                    "org.hibernate..", "lombok..");

    @ArchTest
    static final ArchRule noCyclesBetweenModules = slices()
            .matching("com.hexbank.(*)..")
            .should().beFreeOfCycles();

    /** adapter -> application -> domain, nunca ao contrário. */
    @ArchTest
    static final ArchRule hexagonalLayers = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Adapter").definedBy("..adapter..")
            .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapter")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Adapter")
            .withOptionalLayers(true);

    /** Entre módulos, só application.port.in e domain. shared e a raiz ficam de fora. */
    @ArchTest
    static final ArchRule modulesUseOnlyPublicApiOfOthers = classes()
            .that().resideInAPackage("com.hexbank..")
            .should(onlyUsePublicApiOfOtherModules());

    private static ArchCondition<JavaClass> onlyUsePublicApiOfOtherModules() {
        return new ArchCondition<>("usar apenas application.port.in ou domain de outros módulos") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String originModule = moduleOf(origin);
                if (originModule == null) {
                    return;
                }
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String targetModule = moduleOf(target);
                    if (targetModule == null || targetModule.equals(originModule) || targetModule.equals("shared")) {
                        continue;
                    }
                    String pkg = target.getPackageName();
                    boolean publicApi = pkg.contains(".application.port.in") || pkg.contains(".domain");
                    if (!publicApi) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    /** "com.hexbank.ledger.domain.Journal" -> "ledger"; fora de módulo -> null. */
    private static String moduleOf(JavaClass javaClass) {
        String pkg = javaClass.getPackageName();
        if (!pkg.startsWith(ROOT)) {
            return null;
        }
        String rest = pkg.substring(ROOT.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}