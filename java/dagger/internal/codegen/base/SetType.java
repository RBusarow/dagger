/*
 * Copyright (C) 2015 The Dagger Authors.
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

package dagger.internal.codegen.base;

import static androidx.room.compiler.processing.compat.XConverters.toJavac;
import static com.google.common.base.Preconditions.checkArgument;
import static dagger.internal.codegen.langmodel.DaggerTypes.isTypeOf;
import static dagger.internal.codegen.langmodel.DaggerTypes.unwrapType;

import androidx.room.compiler.processing.XType;
import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.squareup.javapoet.ClassName;
import dagger.internal.codegen.javapoet.TypeNames;
import dagger.spi.model.Key;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/** Information about a {@link java.util.Set} {@link TypeMirror}. */
@AutoValue
public abstract class SetType {
  /**
   * The set type itself, wrapped using {@link MoreTypes#equivalence()}. Use
   * {@link #declaredSetType()} instead.
   */
  protected abstract Equivalence.Wrapper<DeclaredType> wrappedDeclaredSetType();

  /**
   * The set type itself.
   */
  private DeclaredType declaredSetType() {
    return wrappedDeclaredSetType().get();
  }

  /** {@code true} if the set type is the raw {@link java.util.Set} type. */
  public boolean isRawType() {
    return declaredSetType().getTypeArguments().isEmpty();
  }

  /**
   * The element type.
   */
  public TypeMirror elementType() {
    return unwrapType(declaredSetType());
  }

  /** Returns {@code true} if {@link #elementType()} is of type {@code className}. */
  public boolean elementsAreTypeOf(ClassName className) {
    return MoreTypes.isType(elementType()) && isTypeOf(className, elementType());
  }

  /**
   * {@code T} if {@link #elementType()} is a {@code WrappingClass<T>}.
   *
   * @throws IllegalStateException if {@link #elementType()} is not a {@code WrappingClass<T>}
   */
  // TODO(b/202033221): Consider using stricter input type, e.g. FrameworkType.
  public TypeMirror unwrappedElementType(ClassName wrappingClass) {
    checkArgument(
        elementsAreTypeOf(wrappingClass),
        "expected elements to be %s, but this type is %s",
        wrappingClass,
        declaredSetType());
    return unwrapType(elementType());
  }

  /** {@code true} if {@code type} is a {@link java.util.Set} type. */
  public static boolean isSet(XType type) {
    return isSet(toJavac(type));
  }

  /** {@code true} if {@code type} is a {@link java.util.Set} type. */
  public static boolean isSet(TypeMirror type) {
    return MoreTypes.isType(type) && isTypeOf(TypeNames.SET, type);
  }

  /** {@code true} if {@code key.type()} is a {@link java.util.Set} type. */
  public static boolean isSet(Key key) {
    return isSet(key.type().java());
  }

  /**
   * Returns a {@link SetType} for {@code type}.
   *
   * @throws IllegalArgumentException if {@code type} is not a {@link java.util.Set} type
   */
  public static SetType from(XType type) {
    return from(toJavac(type));
  }

  /**
   * Returns a {@link SetType} for {@code type}.
   *
   * @throws IllegalArgumentException if {@code type} is not a {@link java.util.Set} type
   */
  public static SetType from(TypeMirror type) {
    checkArgument(isSet(type), "%s must be a Set", type);
    return new AutoValue_SetType(MoreTypes.equivalence().wrap(MoreTypes.asDeclared(type)));
  }

  /**
   * Returns a {@link SetType} for {@code key}'s {@link Key#type() type}.
   *
   * @throws IllegalArgumentException if {@code key.type()} is not a {@link java.util.Set} type
   */
  public static SetType from(Key key) {
    return from(key.type().java());
  }
}
