package org.sourceanalysis.app.analysis.discovery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** The explicit or unrestricted HTTP-method condition declared by one Spring MVC mapping. */
public record HttpMethodCondition(Kind kind, List<String> methods) {

  private static final List<String> CANONICAL_ORDER =
      List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");

  public HttpMethodCondition {
    Objects.requireNonNull(kind, "method-condition kind");
    methods = methods == null ? List.of() : List.copyOf(methods);
    if (kind == Kind.UNRESTRICTED && !methods.isEmpty()) {
      throw new IllegalArgumentException("unrestricted HTTP method condition cannot list methods");
    }
    if (kind == Kind.EXPLICIT) {
      if (methods.isEmpty()
          || methods.stream().anyMatch(value -> !CANONICAL_ORDER.contains(value))
          || !methods.equals(canonicalize(methods))) {
        throw new IllegalArgumentException("explicit HTTP method condition must be canonical");
      }
    }
  }

  public static HttpMethodCondition unrestricted() {
    return new HttpMethodCondition(Kind.UNRESTRICTED, List.of());
  }

  public static HttpMethodCondition explicit(Collection<String> methods) {
    return new HttpMethodCondition(Kind.EXPLICIT, canonicalize(methods));
  }

  /** Combines class and method conditions without inventing a default request method. */
  public HttpMethodCondition combine(HttpMethodCondition other) {
    Objects.requireNonNull(other, "other method condition");
    if (kind == Kind.UNRESTRICTED) {
      return other;
    }
    if (other.kind == Kind.UNRESTRICTED) {
      return this;
    }
    List<String> combined = new ArrayList<>(methods);
    combined.addAll(other.methods);
    return explicit(combined);
  }

  /** Compact presentation for existing code that needs to display, not interpret, the condition. */
  public String display() {
    return kind == Kind.UNRESTRICTED ? "UNRESTRICTED" : String.join(",", methods);
  }

  private static List<String> canonicalize(Collection<String> methods) {
    if (methods == null) {
      throw new IllegalArgumentException("HTTP methods");
    }
    return CANONICAL_ORDER.stream().filter(methods::contains).toList();
  }

  public enum Kind {
    UNRESTRICTED,
    EXPLICIT
  }
}
