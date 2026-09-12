package org.sourceanalysis.tools.jdtsyntax;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.CreationReference;
import org.eclipse.jdt.core.dom.DoStatement;
import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ForStatement;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodReference;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.SwitchExpression;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.SynchronizedStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.WhileStatement;

/** Projects standard JDT Core AST nodes into the engine-neutral syntax protocol. */
public final class JdtSyntaxReader {

  public JdtSyntaxProtocol.Response describe(JdtSyntaxProtocol.Request request) {
    validate(request);
    ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setSource(request.text().toCharArray());
    parser.setResolveBindings(false);
    parser.setBindingsRecovery(false);
    parser.setStatementsRecovery(true);
    Map<String, String> options = new HashMap<>();
    options.put(JavaCore.COMPILER_SOURCE, request.languageLevel());
    options.put(JavaCore.COMPILER_COMPLIANCE, request.languageLevel());
    options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, request.languageLevel());
    parser.setCompilerOptions(options);
    CompilationUnit unit = (CompilationUnit) parser.createAST(null);
    Collector collector = new Collector(request.text(), unit);
    unit.accept(collector);
    collector.addProblems(unit.getProblems());
    String packageName =
        unit.getPackage() == null ? null : unit.getPackage().getName().getFullyQualifiedName();
    return new JdtSyntaxProtocol.Response(
        JdtSyntaxProtocol.VERSION,
        request.requestId(),
        request.sourceKey(),
        request.sourceSha256(),
        packageName,
        collector.imports,
        collector.declarations,
        collector.calls,
        collector.controls,
        collector.exits,
        collector.diagnostics);
  }

  private static void validate(JdtSyntaxProtocol.Request request) {
    if (request == null) {
      throw new JdtSyntaxProtocol.ProtocolException("request is required");
    }
    if (!JdtSyntaxProtocol.VERSION.equals(request.protocolVersion())) {
      throw new JdtSyntaxProtocol.ProtocolException("unsupported protocol version");
    }
    if (!JdtSyntaxProtocol.DESCRIBE_COMPILATION_UNIT.equals(request.operation())) {
      throw new JdtSyntaxProtocol.ProtocolException("unsupported operation");
    }
    required(request.requestId(), "request ID");
    required(request.sourceKey(), "source key");
    required(request.languageLevel(), "language level");
    required(request.text(), "source text");
    if (!sha256(request.text()).equals(request.sourceSha256())) {
      throw new JdtSyntaxProtocol.ProtocolException("source fingerprint mismatch");
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new JdtSyntaxProtocol.ProtocolException(label + " is required");
    }
  }

  private static String sha256(String source) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static final class Collector extends ASTVisitor {
    private final String source;
    private final CompilationUnit unit;
    private final List<JdtSyntaxProtocol.ImportView> imports = new ArrayList<>();
    private final List<JdtSyntaxProtocol.Declaration> declarations = new ArrayList<>();
    private final List<JdtSyntaxProtocol.CallSiteView> calls = new ArrayList<>();
    private final List<JdtSyntaxProtocol.ControlView> controls = new ArrayList<>();
    private final List<JdtSyntaxProtocol.ExitView> exits = new ArrayList<>();
    private final List<JdtSyntaxProtocol.Diagnostic> diagnostics = new ArrayList<>();
    private final Deque<String> typeNames = new ArrayDeque<>();
    private final Deque<CallableState> callables = new ArrayDeque<>();

    private Collector(String source, CompilationUnit unit) {
      this.source = source;
      this.unit = unit;
    }

    @Override
    public boolean visit(ImportDeclaration node) {
      imports.add(
          new JdtSyntaxProtocol.ImportView(
              text(node),
              node.getName().getFullyQualifiedName(),
              node.isOnDemand(),
              node.isStatic(),
              range(node)));
      return false;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
      enterType(node.getName().getIdentifier(), "TYPE", node, node.modifiers());
      return true;
    }

    @Override
    public void endVisit(TypeDeclaration node) {
      typeNames.pop();
    }

    @Override
    public boolean visit(EnumDeclaration node) {
      enterType(node.getName().getIdentifier(), "TYPE", node, node.modifiers());
      return true;
    }

    @Override
    public void endVisit(EnumDeclaration node) {
      typeNames.pop();
    }

    @Override
    public boolean visit(RecordDeclaration node) {
      enterType(node.getName().getIdentifier(), "TYPE", node, node.modifiers());
      return true;
    }

    @Override
    public void endVisit(RecordDeclaration node) {
      typeNames.pop();
    }

    @Override
    public boolean visit(AnnotationTypeDeclaration node) {
      enterType(node.getName().getIdentifier(), "TYPE", node, node.modifiers());
      return true;
    }

    @Override
    public void endVisit(AnnotationTypeDeclaration node) {
      typeNames.pop();
    }

    @Override
    public boolean visit(AnonymousClassDeclaration node) {
      typeNames.push(typeNames.isEmpty() ? "<anonymous>" : typeNames.peek() + ".<anonymous>");
      return true;
    }

    @Override
    public void endVisit(AnonymousClassDeclaration node) {
      typeNames.pop();
    }

    private void enterType(String name, String kind, ASTNode node, List<?> modifiers) {
      String qualified = typeNames.isEmpty() ? name : typeNames.peek() + "." + name;
      declarations.add(
          declaration(
              localId(kind, node),
              kind,
              name,
              typeNames.peek(),
              null,
              text(node),
              modifiers(modifiers),
              annotations(modifiers),
              List.of(),
              null,
              node,
              false));
      typeNames.push(qualified);
    }

    @Override
    public boolean visit(MethodDeclaration node) {
      String kind = node.isConstructor() ? "CONSTRUCTOR" : "METHOD";
      String id = localId(kind, node);
      List<JdtSyntaxProtocol.ParameterView> parameters = new ArrayList<>();
      for (int index = 0; index < node.parameters().size(); index++) {
        SingleVariableDeclaration parameter =
            (SingleVariableDeclaration) node.parameters().get(index);
        parameters.add(
            new JdtSyntaxProtocol.ParameterView(
                index,
                parameter.getName().getIdentifier(),
                text(parameter.getType()),
                parameter.isVarargs(),
                annotations(parameter.modifiers()),
                range(parameter)));
      }
      String returnType =
          node.isConstructor() || node.getReturnType2() == null
              ? null
              : text(node.getReturnType2());
      declarations.add(
          declaration(
              id,
              kind,
              node.getName().getIdentifier(),
              typeNames.peek(),
              currentCallableId(),
              signature(node),
              modifiers(node.modifiers()),
              annotations(node.modifiers()),
              parameters,
              returnType,
              node,
              node.getBody() != null));
      callables.push(new CallableState(id, false));
      return true;
    }

    @Override
    public void endVisit(MethodDeclaration node) {
      callables.pop();
    }

    @Override
    public boolean visit(LambdaExpression node) {
      String id = localId("LAMBDA", node);
      List<JdtSyntaxProtocol.ParameterView> parameters = new ArrayList<>();
      for (int index = 0; index < node.parameters().size(); index++) {
        ASTNode raw = (ASTNode) node.parameters().get(index);
        if (raw instanceof SingleVariableDeclaration parameter) {
          parameters.add(
              new JdtSyntaxProtocol.ParameterView(
                  index,
                  parameter.getName().getIdentifier(),
                  text(parameter.getType()),
                  parameter.isVarargs(),
                  annotations(parameter.modifiers()),
                  range(parameter)));
        } else {
          parameters.add(
              new JdtSyntaxProtocol.ParameterView(
                  index, text(raw), "inferred", false, List.of(), range(raw)));
        }
      }
      declarations.add(
          declaration(
              id,
              "LAMBDA",
              null,
              typeNames.peek(),
              currentCallableId(),
              text(node),
              List.of(),
              List.of(),
              parameters,
              null,
              node,
              true));
      callables.push(new CallableState(id, true));
      return true;
    }

    @Override
    public void endVisit(LambdaExpression node) {
      callables.pop();
    }

    @Override
    public boolean visit(Initializer node) {
      String id = localId("INITIALIZER", node);
      declarations.add(
          declaration(
              id,
              "INITIALIZER",
              null,
              typeNames.peek(),
              currentCallableId(),
              text(node),
              modifiers(node.modifiers()),
              List.of(),
              List.of(),
              null,
              node,
              true));
      callables.push(new CallableState(id, false));
      return true;
    }

    @Override
    public void endVisit(Initializer node) {
      callables.pop();
    }

    @Override
    public boolean visit(FieldDeclaration node) {
      for (Object fragmentValue : node.fragments()) {
        VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentValue;
        declarations.add(
            declaration(
                localId("FIELD", fragment),
                "FIELD",
                fragment.getName().getIdentifier(),
                typeNames.peek(),
                currentCallableId(),
                text(node),
                modifiers(node.modifiers()),
                annotations(node.modifiers()),
                List.of(),
                text(node.getType()),
                node,
                fragment.getInitializer() != null));
      }
      return true;
    }

    @Override
    public boolean visit(MethodInvocation node) {
      addCall("METHOD", node, node.getName(), node.getExpression(), node.arguments(), false);
      return true;
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
      addCall("SUPER_METHOD", node, node.getName(), null, node.arguments(), false);
      return true;
    }

    @Override
    public boolean visit(ClassInstanceCreation node) {
      addCall("CONSTRUCTOR", node, node.getType(), node.getExpression(), node.arguments(), false);
      return true;
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
      addCall("THIS_CONSTRUCTOR", node, node, null, node.arguments(), false);
      return true;
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
      addCall("SUPER_CONSTRUCTOR", node, node, node.getExpression(), node.arguments(), false);
      return true;
    }

    @Override
    public boolean visit(ExpressionMethodReference node) {
      addReference("METHOD_REFERENCE", node, node.getName(), node.getExpression());
      return true;
    }

    @Override
    public boolean visit(TypeMethodReference node) {
      addReference("METHOD_REFERENCE", node, node.getName(), node.getType());
      return true;
    }

    @Override
    public boolean visit(SuperMethodReference node) {
      addReference("METHOD_REFERENCE", node, node.getName(), null);
      return true;
    }

    @Override
    public boolean visit(CreationReference node) {
      addReference("CONSTRUCTOR_REFERENCE", node, node.getType(), node.getType());
      return true;
    }

    private void addReference(
        String kind, MethodReference node, ASTNode navigation, ASTNode receiver) {
      addCall(kind, node, navigation, receiver, List.of(), true);
    }

    private void addCall(
        String kind,
        ASTNode node,
        ASTNode navigation,
        ASTNode receiver,
        List<?> arguments,
        boolean deferred) {
      if (callables.isEmpty()) {
        return;
      }
      List<String> actuals = arguments.stream().map(value -> text((ASTNode) value)).toList();
      calls.add(
          new JdtSyntaxProtocol.CallSiteView(
              "call:" + node.getStartPosition() + ":" + node.getLength(),
              callables.peek().id,
              kind,
              range(node),
              range(navigation),
              text(node),
              receiver == null ? null : text(receiver),
              actuals,
              deferred || callables.peek().deferred));
    }

    @Override
    public void preVisit(ASTNode node) {
      ASTNode parent = node.getParent();
      if (parent instanceof IfStatement statement && statement.getElseStatement() == node) {
        enterControl("ELSE", null, node);
      } else if (parent instanceof TryStatement statement && statement.getFinally() == node) {
        enterControl("FINALLY", null, node);
      }
    }

    @Override
    public void postVisit(ASTNode node) {
      ASTNode parent = node.getParent();
      if ((parent instanceof IfStatement ifStatement && ifStatement.getElseStatement() == node)
          || (parent instanceof TryStatement tryStatement && tryStatement.getFinally() == node)) {
        exitControl();
      }
    }

    @Override
    public boolean visit(IfStatement node) {
      enterControl("IF", node.getExpression(), node);
      return true;
    }

    @Override
    public void endVisit(IfStatement node) {
      exitControl();
    }

    @Override
    public boolean visit(TryStatement node) {
      enterControl("TRY", null, node);
      return true;
    }

    @Override
    public void endVisit(TryStatement node) {
      exitControl();
    }

    @Override
    public boolean visit(CatchClause node) {
      enterControl("CATCH", node.getException(), node);
      return true;
    }

    @Override
    public void endVisit(CatchClause node) {
      exitControl();
    }

    @Override
    public boolean visit(ForStatement node) {
      enterControl("LOOP", node.getExpression(), node);
      return true;
    }

    @Override
    public void endVisit(ForStatement node) {
      exitControl();
    }

    @Override
    public boolean visit(EnhancedForStatement node) {
      enterControl("LOOP", node.getExpression(), node);
      return true;
    }

    @Override
    public void endVisit(EnhancedForStatement node) {
      exitControl();
    }

    @Override
    public boolean visit(WhileStatement node) {
      enterControl("LOOP", node.getExpression(), node);
      return true;
    }

    @Override
    public void endVisit(WhileStatement node) {
      exitControl();
    }

    @Override
    public boolean visit(DoStatement node) {
      enterControl("LOOP", node.getExpression(), node);
      return true;
    }

    @Override
    public void endVisit(DoStatement node) {
      exitControl();
    }

    @Override
    public boolean visit(SwitchStatement node) {
      enterControl("SWITCH", node.getExpression(), node);
      return true;
    }

    @Override
    public void endVisit(SwitchStatement node) {
      exitControl();
    }

    @Override
    public boolean visit(SwitchExpression node) {
      enterControl("SWITCH", node.getExpression(), node);
      return true;
    }

    @Override
    public void endVisit(SwitchExpression node) {
      exitControl();
    }

    @Override
    public boolean visit(SynchronizedStatement node) {
      enterControl("SYNCHRONIZED", node.getExpression(), node);
      return true;
    }

    @Override
    public void endVisit(SynchronizedStatement node) {
      exitControl();
    }

    private void enterControl(String kind, ASTNode expression, ASTNode node) {
      if (callables.isEmpty()) {
        return;
      }
      CallableState state = callables.peek();
      int index = state.nextControlIndex++;
      Integer parent = state.controlIndexes.peek();
      controls.add(
          new JdtSyntaxProtocol.ControlView(
              state.id,
              index,
              kind,
              expression == null ? null : text(expression),
              range(node),
              parent));
      state.controlIndexes.push(index);
    }

    private void exitControl() {
      if (!callables.isEmpty() && !callables.peek().controlIndexes.isEmpty()) {
        callables.peek().controlIndexes.pop();
      }
    }

    @Override
    public boolean visit(ReturnStatement node) {
      addExit("RETURN", node.getExpression(), node);
      return true;
    }

    @Override
    public boolean visit(ThrowStatement node) {
      addExit("THROW", node.getExpression(), node);
      return true;
    }

    private void addExit(String kind, Expression expression, ASTNode node) {
      if (!callables.isEmpty()) {
        exits.add(
            new JdtSyntaxProtocol.ExitView(
                callables.peek().id,
                kind,
                expression == null ? null : text(expression),
                range(node)));
      }
    }

    private JdtSyntaxProtocol.Declaration declaration(
        String localId,
        String kind,
        String name,
        String declaringType,
        String enclosing,
        String signature,
        List<String> modifiers,
        List<String> annotations,
        List<JdtSyntaxProtocol.ParameterView> parameters,
        String returnType,
        ASTNode node,
        boolean bodyPresent) {
      return new JdtSyntaxProtocol.Declaration(
          localId,
          kind,
          name,
          declaringType,
          enclosing,
          signature,
          List.copyOf(modifiers),
          List.copyOf(annotations),
          List.copyOf(parameters),
          returnType,
          range(node),
          text(node),
          bodyPresent);
    }

    private String signature(MethodDeclaration node) {
      Block body = node.getBody();
      if (body == null) {
        return text(node);
      }
      return source.substring(node.getStartPosition(), body.getStartPosition()).stripTrailing();
    }

    private List<String> modifiers(List<?> values) {
      return values.stream()
          .filter(Modifier.class::isInstance)
          .map(value -> text((ASTNode) value))
          .toList();
    }

    private List<String> annotations(List<?> values) {
      return values.stream()
          .filter(Annotation.class::isInstance)
          .map(value -> text((ASTNode) value))
          .toList();
    }

    private String currentCallableId() {
      return callables.isEmpty() ? null : callables.peek().id;
    }

    private String localId(String kind, ASTNode node) {
      return "decl:"
          + kind.toLowerCase(java.util.Locale.ROOT)
          + ":"
          + node.getStartPosition()
          + ":"
          + node.getLength();
    }

    private String text(ASTNode node) {
      int start = node.getStartPosition();
      int end = start + node.getLength();
      if (start < 0 || end < start || end > source.length()) {
        return "";
      }
      return source.substring(start, end);
    }

    private JdtSyntaxProtocol.SourceRange range(ASTNode node) {
      int start = node.getStartPosition();
      int length = node.getLength();
      int last = length == 0 ? start : start + length - 1;
      return new JdtSyntaxProtocol.SourceRange(
          start, length, unit.getLineNumber(start), unit.getLineNumber(last));
    }

    private void addProblems(IProblem[] problems) {
      for (IProblem problem : problems) {
        int start = Math.max(0, problem.getSourceStart());
        int end = Math.max(start, problem.getSourceEnd() + 1);
        diagnostics.add(
            new JdtSyntaxProtocol.Diagnostic(
                Integer.toString(problem.getID()),
                problem.isError() ? "ERROR" : problem.isWarning() ? "WARNING" : "INFO",
                problem.getMessage(),
                new JdtSyntaxProtocol.SourceRange(
                    start,
                    end - start,
                    unit.getLineNumber(start),
                    unit.getLineNumber(Math.max(start, end - 1)))));
      }
    }
  }

  private static final class CallableState {
    private final String id;
    private final boolean deferred;
    private final Deque<Integer> controlIndexes = new ArrayDeque<>();
    private int nextControlIndex;

    private CallableState(String id, boolean deferred) {
      this.id = id;
      this.deferred = deferred;
    }
  }
}
