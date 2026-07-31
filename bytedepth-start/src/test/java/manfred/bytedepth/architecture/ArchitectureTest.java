package manfred.bytedepth.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final String DOMAIN = "..domain..";
    private static final String APP = "..app..";
    private static final String INFRASTRUCTURE = "..infrastructure..";
    private static final String ADAPTER = "..adapter..";

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("manfred.bytedepth");

    @Test
    void dependenciesFlowOnlyInward() {
        rule(DOMAIN, APP, INFRASTRUCTURE, ADAPTER).check(classes);
        rule(APP, INFRASTRUCTURE, ADAPTER).check(classes);
        rule(INFRASTRUCTURE, ADAPTER).check(classes);
        rule(ADAPTER, INFRASTRUCTURE).check(classes);
    }

    @Test
    void domainRemainsFrameworkFree() {
        noClasses().that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "org.apache.ibatis..", "com.baomidou..",
                        "jakarta..", "javax.persistence..", "javax.servlet..")
                .check(classes);
    }

    @Test
    void webAdapterDoesNotAccessPersistenceOrRedisApis() {
        noClasses().that().resideInAPackage(ADAPTER)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.apache.ibatis..", "com.baomidou..", "org.springframework.data.redis..")
                .check(classes);
    }

    private ArchRule rule(String sourceLayer, String... forbiddenLayers) {
        return noClasses().that().resideInAPackage(sourceLayer)
                .should().dependOnClassesThat().resideInAnyPackage(forbiddenLayers);
    }
}
