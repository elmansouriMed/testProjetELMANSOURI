// createdBy ELMANSOURI Mohamed
package fr.bank.payments.messages;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("fr.bank.payments");

  @Test
  void coreDoesNotDependOnFrameworks() {
    noClasses()
        .that()
        .resideInAnyPackage("..messages.domain..", "..messages.application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta..",
            "java.sql..",
            "com.ibm..",
            "org.mapstruct..",
            "org.hibernate..")
        .check(CLASSES);
  }

  @Test
  void dependenciesPointTowardsTheCore() {
    noClasses()
        .that()
        .resideInAnyPackage("..messages.domain..", "..messages.application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..messages.infrastructure..", "..shared..")
        .check(CLASSES);
  }

  @Test
  void inboundAdaptersDoNotUsePersistenceImplementations() {
    noClasses()
        .that()
        .resideInAPackage("..messages.infrastructure.adapter.in..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..messages.infrastructure.adapter.out..")
        .check(CLASSES);
  }

  @Test
  void layersDoNotContainCycles() {
    slices().matching("fr.bank.payments.messages.(*)..").should().beFreeOfCycles().check(CLASSES);
  }
}
