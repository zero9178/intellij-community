/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.patterns;

import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiExpressionPattern<T extends PsiExpression, Self extends PsiExpressionPattern<T,Self>> extends PsiJavaElementPattern<T,Self> {
  protected PsiExpressionPattern(final Class<T> aClass) {
    super(aClass);
  }

  public Self ofType(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T t, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return pattern.getCondition().accepts(t.getType(), matchingContext, traverseContext);
      }
    });
  }

  public Self methodCall(final ElementPattern methodPattern) {
    return with(new PatternCondition<T>() {
      public boolean accepts(@NotNull final T element, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        if (element instanceof PsiMethodCallExpression) {
          final JavaResolveResult[] results = ((PsiMethodCallExpression)element).getMethodExpression().multiResolve(true);
          for (JavaResolveResult result : results) {
            if (methodPattern.getCondition().accepts(result.getElement(), matchingContext, traverseContext)) {
              return true;
            }
          }
        }
        return false;
      }
    });
  }


  public static class Capture<T extends PsiExpression> extends PsiExpressionPattern<T, Capture<T>> {
    public Capture(final Class<T> aClass) {
      super(aClass);
    }

  }
}