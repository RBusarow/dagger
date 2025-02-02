/*
 * Copyright (C) 2017 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dagger.internal.codegen;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static dagger.internal.codegen.CompilerMode.DEFAULT_MODE;
import static dagger.internal.codegen.CompilerMode.FAST_INIT_MODE;
import static dagger.internal.codegen.Compilers.compilerWithOptions;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collection;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DelegateRequestRepresentationTest {
  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return CompilerMode.TEST_PARAMETERS;
  }

  private final CompilerMode compilerMode;

  public DelegateRequestRepresentationTest(CompilerMode compilerMode) {
    this.compilerMode = compilerMode;
  }

  private static final JavaFileObject REGULAR_SCOPED =
      JavaFileObjects.forSourceLines(
          "test.RegularScoped",
          "package test;",
          "",
          "import javax.inject.Scope;",
          "import javax.inject.Inject;",
          "",
          "@RegularScoped.CustomScope",
          "class RegularScoped {",
          "  @Inject RegularScoped() {}",
          "",
          "  @Scope @interface CustomScope {}",
          "}");

  private static final JavaFileObject REUSABLE_SCOPED =
      JavaFileObjects.forSourceLines(
          "test.ReusableScoped",
          "package test;",
          "",
          "import dagger.Reusable;",
          "import javax.inject.Inject;",
          "",
          "@Reusable",
          "class ReusableScoped {",
          "  @Inject ReusableScoped() {}",
          "}");

  private static final JavaFileObject UNSCOPED =
      JavaFileObjects.forSourceLines(
          "test.Unscoped",
          "package test;",
          "",
          "import javax.inject.Inject;",
          "",
          "class Unscoped {",
          "  @Inject Unscoped() {}",
          "}");

  private static final JavaFileObject COMPONENT =
      JavaFileObjects.forSourceLines(
          "test.TestComponent",
          "package test;",
          "",
          "import dagger.Component;",
          "",
          "@Component(modules = TestModule.class)",
          "@RegularScoped.CustomScope",
          "interface TestComponent {",
          "  @Qualifier(RegularScoped.class)",
          "  Object regular();",
          "",
          "  @Qualifier(ReusableScoped.class)",
          "  Object reusable();",
          "",
          "  @Qualifier(Unscoped.class)",
          "  Object unscoped();",
          "}");

  private static final JavaFileObject QUALIFIER =
      JavaFileObjects.forSourceLines(
          "test.Qualifier",
          "package test;",
          "",
          "@javax.inject.Qualifier",
          "@interface Qualifier {",
          "  Class<?> value();",
          "}");

  @Test
  public void toDoubleCheck() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Binds @RegularScoped.CustomScope @Qualifier(RegularScoped.class)",
            "  Object regular(RegularScoped delegate);",
            "",
            "  @Binds @RegularScoped.CustomScope @Qualifier(ReusableScoped.class)",
            "  Object reusable(ReusableScoped delegate);",
            "",
            "  @Binds @RegularScoped.CustomScope @Qualifier(Unscoped.class)",
            "  Object unscoped(Unscoped delegate);",
            "}");

    assertThatCompilationWithModule(module)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent implements TestComponent {")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "    this.regularScopedProvider =",
                    "        DoubleCheck.provider(",
                    "            new SwitchingProvider<RegularScoped>(testComponent, 0));",
                    "    this.reusableScopedProvider =",
                    "        SingleCheck.provider(",
                    "            new SwitchingProvider<ReusableScoped>(testComponent, 1));",
                    "    this.reusableProvider =",
                    "        DoubleCheck.provider((Provider) reusableScopedProvider);",
                    "    this.unscopedProvider = new SwitchingProvider<>(testComponent, 2);",
                    "    this.unscopedProvider2 =",
                    "        DoubleCheck.provider((Provider) unscopedProvider);",
                    "  }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "    this.regularScopedProvider = ",
                    "        DoubleCheck.provider(RegularScoped_Factory.create());",
                    "    this.reusableScopedProvider = ",
                    "        SingleCheck.provider(ReusableScoped_Factory.create());",
                    "    this.reusableProvider = DoubleCheck.provider(",
                    "        (Provider) reusableScopedProvider);",
                    "    this.unscopedProvider = DoubleCheck.provider(",
                    "        (Provider) Unscoped_Factory.create());",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  private static final class SwitchingProvider<T> implements Provider<T> {",
                    "    @SuppressWarnings(\"unchecked\")",
                    "    @Override",
                    "    public T get() {",
                    "      switch (id) {",
                    "        case 0: return (T) new RegularScoped();",
                    "        case 1: return (T) new ReusableScoped();",
                    "        case 2: return (T) new Unscoped();",
                    "        default: throw new AssertionError(id);",
                    "      }",
                    "    }",
                    "  }")
                .build());
  }

  @Test
  public void toSingleCheck() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Reusable;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Binds @Reusable @Qualifier(RegularScoped.class)",
            "  Object regular(RegularScoped delegate);",
            "",
            "  @Binds @Reusable @Qualifier(ReusableScoped.class)",
            "  Object reusable(ReusableScoped delegate);",
            "",
            "  @Binds @Reusable @Qualifier(Unscoped.class)",
            "  Object unscoped(Unscoped delegate);",
            "}");

    assertThatCompilationWithModule(module)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent implements TestComponent {")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "    this.regularScopedProvider =",
                    "        DoubleCheck.provider(",
                    "            new SwitchingProvider<RegularScoped>(testComponent, 0));",
                    "    this.reusableScopedProvider =",
                    "        SingleCheck.provider(",
                    "            new SwitchingProvider<ReusableScoped>(testComponent, 1));",
                    "    this.unscopedProvider = new SwitchingProvider<>(testComponent, 2);",
                    "    this.unscopedProvider2 =",
                    "        SingleCheck.provider((Provider) unscopedProvider);",
                    "  }")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "    this.regularScopedProvider = ",
                    "        DoubleCheck.provider(RegularScoped_Factory.create());",
                    "    this.reusableScopedProvider = ",
                    "        SingleCheck.provider(ReusableScoped_Factory.create());",
                    "    this.unscopedProvider = SingleCheck.provider(",
                    "        (Provider) Unscoped_Factory.create());",
                    "  }")
                .build());
  }

  @Test
  public void toUnscoped() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Binds @Qualifier(RegularScoped.class)",
            "  Object regular(RegularScoped delegate);",
            "",
            "  @Binds @Qualifier(ReusableScoped.class)",
            "  Object reusable(ReusableScoped delegate);",
            "",
            "  @Binds @Qualifier(Unscoped.class)",
            "  Object unscoped(Unscoped delegate);",
            "}");

    assertThatCompilationWithModule(module)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent implements TestComponent {",
                    "  private Provider<RegularScoped> regularScopedProvider;",
                    "  private Provider<ReusableScoped> reusableScopedProvider;")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "    this.regularScopedProvider = ",
                    "        DoubleCheck.provider(RegularScoped_Factory.create());",
                    "    this.reusableScopedProvider = ",
                    "        SingleCheck.provider(ReusableScoped_Factory.create());",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "  this.regularScopedProvider =",
                    "      DoubleCheck.provider(",
                    "          new SwitchingProvider<RegularScoped>(testComponent, 0));",
                    "  this.reusableScopedProvider =",
                    "      SingleCheck.provider(",
                    "          new SwitchingProvider<ReusableScoped>(testComponent, 1));",
                    "  }")
                .build());
  }

  @Test
  public void castNeeded_rawTypes_Provider_get() {
    JavaFileObject accessibleSupertype =
        JavaFileObjects.forSourceLines(
            "other.Supertype",
            "package other;",
            "",
            // accessible from the component, but the subtype is not
            "public interface Supertype {}");
    JavaFileObject inaccessibleSubtype =
        JavaFileObjects.forSourceLines(
            "other.Subtype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "class Subtype implements Supertype {",
            "  @Inject Subtype() {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "other.SupertypeModule",
            "package other;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "public interface SupertypeModule {",
            "  @Binds Supertype to(Subtype subtype);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules = other.SupertypeModule.class)",
            "interface TestComponent {",
            "  other.Supertype supertype();",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(accessibleSupertype, inaccessibleSubtype, module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent implements TestComponent {",
                    "  @SuppressWarnings(\"rawtypes\")",
                    "  private Provider subtypeProvider;")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "    this.subtypeProvider = DoubleCheck.provider(Subtype_Factory.create());",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "    this.subtypeProvider =",
                    "        DoubleCheck.provider(",
                    "            new SwitchingProvider<Object>(testComponent, 0));",
                    "  }")
                .addLines(
                    "  @Override",
                    "  public Supertype supertype() {",
                    "    return (Supertype) subtypeProvider.get();",
                    "  }",
                    "}")
                .build());
  }

  @Test
  public void noCast_rawTypes_Provider_get_toInaccessibleType() {
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "other.Supertype",
            "package other;",
            "",
            "interface Supertype {}");
    JavaFileObject subtype =
        JavaFileObjects.forSourceLines(
            "other.Subtype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "class Subtype implements Supertype {",
            "  @Inject Subtype() {}",
            "}");
    JavaFileObject usesSupertype =
        JavaFileObjects.forSourceLines(
            "other.UsesSupertype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "public class UsesSupertype {",
            "  @Inject UsesSupertype(Supertype supertype) {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "other.SupertypeModule",
            "package other;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "public interface SupertypeModule {",
            "  @Binds Supertype to(Subtype subtype);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "",
            "@Singleton",
            "@Component(modules = other.SupertypeModule.class)",
            "interface TestComponent {",
            "  other.UsesSupertype usesSupertype();",
            "}");
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(supertype, subtype, usesSupertype, module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent implements TestComponent {",
                    "  @SuppressWarnings(\"rawtypes\")",
                    "  private Provider subtypeProvider;",
                    "",
                    "  @Override",
                    "  public UsesSupertype usesSupertype() {",
                    //   can't cast the provider.get() to a type that's not accessible
                    "    return UsesSupertype_Factory.newInstance(subtypeProvider.get());",
                    "  }",
                    "}")
                .build());
  }

  @Test
  public void castedToRawType() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import javax.inject.Named;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String provideString() { return new String(); }",
            "",
            "  @Binds",
            "  CharSequence charSequence(String string);",
            "",
            "  @Binds",
            "  @Named(\"named\")",
            "  String namedString(String string);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Named;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<CharSequence> charSequence();",
            "",
            "  @Named(\"named\") Provider<String> namedString();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent implements TestComponent {")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  @Override",
                    "  public Provider<CharSequence> charSequence() {",
                    "    return (Provider) TestModule_ProvideStringFactory.create();",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<String> namedString() {",
                    "    return TestModule_ProvideStringFactory.create();",
                    "  }",
                    "}")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  private Provider<String> provideStringProvider;",
                    "",
                    "  @Override",
                    "  public Provider<CharSequence> charSequence() {",
                    "    return (Provider) provideStringProvider;",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<String> namedString() {",
                    "    return provideStringProvider;",
                    "  }",
                    "",
                    "  private static final class SwitchingProvider<T> implements Provider<T> {",
                    "    @SuppressWarnings(\"unchecked\")",
                    "    @Override",
                    "    public T get() {",
                    "      switch (id) {",
                    "        case 0: return (T) TestModule_ProvideStringFactory.provideString();",
                    "        default: throw new AssertionError(id);",
                    "      }",
                    "    }",
                    "  }")
                .build());
  }

  @Test
  public void doubleBinds() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "",
            "@Module",
            "interface TestModule {",
            "  @Provides",
            "  static String provideString() { return new String(); }",
            "",
            "  @Binds",
            "  CharSequence charSequence(String string);",
            "",
            "  @Binds",
            "  Object object(CharSequence charSequence);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Named;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<CharSequence> charSequence();",
            "  Provider<Object> object();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent implements TestComponent {")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  @Override",
                    "  public Provider<CharSequence> charSequence() {",
                    "    return (Provider) TestModule_ProvideStringFactory.create();",
                    "  }",
                    "  @Override",
                    "  public Provider<Object> object() {",
                    "    return (Provider) TestModule_ProvideStringFactory.create();",
                    "  }",
                    "}")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  private Provider<String> provideStringProvider;",
                    "",
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "    this.provideStringProvider = new SwitchingProvider<>(testComponent, 0);",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<CharSequence> charSequence() {",
                    "    return (Provider) provideStringProvider;",
                    "  }",
                    "",
                    "  @Override",
                    "  public Provider<Object> object() {",
                    "    return (Provider) provideStringProvider;",
                    "  }",
                    "",
                    "  private static final class SwitchingProvider<T> implements Provider<T> {",
                    "    @SuppressWarnings(\"unchecked\")",
                    "    @Override",
                    "    public T get() {",
                    "      switch (id) {",
                    "        case 0: return (T) TestModule_ProvideStringFactory.provideString();",
                    "        default: throw new AssertionError(id);",
                    "      }",
                    "    }",
                    "  }")
                .build());
  }

  @Test
  public void inlineFactoryOfInacessibleType() {
    JavaFileObject supertype =
        JavaFileObjects.forSourceLines(
            "other.Supertype", "package other;", "", "public interface Supertype {}");
    JavaFileObject injectableSubtype =
        JavaFileObjects.forSourceLines(
            "other.Subtype",
            "package other;",
            "",
            "import javax.inject.Inject;",
            "",
            "final class Subtype implements Supertype {",
            // important: this doesn't have any dependencies and therefore the factory will be able
            // to be referenced with an inline Subtype_Factory.create()
            "  @Inject Subtype() {}",
            "}");
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "other.TestModule",
            "package other;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "",
            "@Module",
            "public interface TestModule {",
            "  @Binds Supertype to(Subtype subtype);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.RequestsSubtypeAsProvider",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Provider;",
            "",
            "@Component(modules = other.TestModule.class)",
            "interface RequestsSubtypeAsProvider {",
            "  Provider<other.Supertype> supertypeProvider();",
            "}");

    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(supertype, injectableSubtype, module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerRequestsSubtypeAsProvider")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerRequestsSubtypeAsProvider")
                .addLines(
                    "package test;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerRequestsSubtypeAsProvider",
                    "    implements RequestsSubtypeAsProvider {")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  @Override",
                    "  public Provider<Supertype> supertypeProvider() {",
                    "    return (Provider) Subtype_Factory.create();",
                    "  }",
                    "}")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  @SuppressWarnings(\"rawtypes\")",
                    "  private Provider subtypeProvider;",
                    "",
                    "  @Override",
                    "  public Provider<Supertype> supertypeProvider() {",
                    "    return subtypeProvider;",
                    "  }",
                    "",
                    "  private static final class SwitchingProvider<T> implements Provider<T> {",
                    "    @SuppressWarnings(\"unchecked\")",
                    "    @Override",
                    "    public T get() {",
                    "      switch (id) {",
                    "        case 0: return (T) Subtype_Factory.newInstance();",
                    "        default: throw new AssertionError(id);",
                    "      }",
                    "    }",
                    "  }")
                .build());
  }

  @Test
  public void providerWhenBindsScopeGreaterThanDependencyScope() {
    JavaFileObject module =
        JavaFileObjects.forSourceLines(
            "test.TestModule",
            "package test;",
            "",
            "import dagger.Binds;",
            "import dagger.Module;",
            "import dagger.Provides;",
            "import dagger.Reusable;",
            "import javax.inject.Singleton;",
            "",
            "@Module",
            "public abstract class TestModule {",
            "  @Reusable",
            "  @Provides",
            "  static String provideString() {",
            "    return \"\";",
            "  }",
            "",
            "  @Binds",
            "  @Singleton",
            "  abstract Object bindString(String str);",
            "}");
    JavaFileObject component =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "import javax.inject.Singleton;",
            "import javax.inject.Provider;",
            "",
            "@Singleton",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  Provider<Object> object();",
            "}");

    Compilation compilation = compilerWithOptions(compilerMode.javacopts())
        .compile(module, component);
    assertThat(compilation).succeeded();
    assertThat(compilation)
        .generatedSourceFile("test.DaggerTestComponent")
        .containsElementsIn(
            compilerMode
                .javaFileBuilder("test.DaggerTestComponent")
                .addLines(
                    "package test;",
                    "",
                    GeneratedLines.generatedAnnotations(),
                    "final class DaggerTestComponent implements TestComponent {",
                    "  private Provider<String> provideStringProvider;",
                    "  private Provider<Object> bindStringProvider;")
                .addLinesIn(
                    DEFAULT_MODE,
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "    this.provideStringProvider =",
                    "        SingleCheck.provider(TestModule_ProvideStringFactory.create());",
                    "    this.bindStringProvider =",
                    "        DoubleCheck.provider((Provider) provideStringProvider);",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  @SuppressWarnings(\"unchecked\")",
                    "  private void initialize() {",
                    "    this.provideStringProvider =",
                    "        SingleCheck.provider(",
                    "            new SwitchingProvider<String>(testComponent, 0));",
                    "    this.bindStringProvider =",
                    "        DoubleCheck.provider((Provider) provideStringProvider);",
                    "  }")
                .addLines(
                    "  @Override",
                    "  public Provider<Object> object() {",
                    "    return bindStringProvider;",
                    "  }")
                .addLinesIn(
                    FAST_INIT_MODE,
                    "  private static final class SwitchingProvider<T> implements Provider<T> {",
                    "    @SuppressWarnings(\"unchecked\")",
                    "    @Override",
                    "    public T get() {",
                    "      switch (id) {",
                    "        case 0: return (T) TestModule_ProvideStringFactory.provideString();",
                    "        default: throw new AssertionError(id);",
                    "      }",
                    "    }",
                    "  }")
                .build());
  }

  private CompilationSubject assertThatCompilationWithModule(JavaFileObject module) {
    Compilation compilation =
        compilerWithOptions(compilerMode.javacopts())
            .compile(
                module,
                COMPONENT,
                QUALIFIER,
                REGULAR_SCOPED,
                REUSABLE_SCOPED,
                UNSCOPED);
    assertThat(compilation).succeeded();
    return assertThat(compilation);
  }
}
