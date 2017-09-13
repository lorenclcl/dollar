/*
 *    Copyright (c) 2014-2017 Neil Ellis
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package dollar.internal.runtime.script.parser;

import com.google.common.io.ByteStreams;
import dollar.api.DollarStatic;
import dollar.api.Pipeable;
import dollar.api.Scope;
import dollar.api.VarKey;
import dollar.api.script.DollarParser;
import dollar.api.script.ParserOptions;
import dollar.api.script.Source;
import dollar.api.types.DollarFactory;
import dollar.api.types.DollarRange;
import dollar.api.var;
import dollar.internal.runtime.script.Builtins;
import dollar.internal.runtime.script.ErrorHandlerFactory;
import dollar.internal.runtime.script.api.Operator;
import dollar.internal.runtime.script.api.exceptions.DollarScriptException;
import dollar.internal.runtime.script.operators.AssignmentOperator;
import dollar.internal.runtime.script.operators.BinaryOp;
import dollar.internal.runtime.script.operators.CollectOperator;
import dollar.internal.runtime.script.operators.DefinitionOperator;
import dollar.internal.runtime.script.operators.ParameterOperator;
import dollar.internal.runtime.script.operators.PureDefinitionOperator;
import dollar.internal.runtime.script.operators.UnaryOp;
import dollar.internal.runtime.script.operators.WindowOperator;
import dollar.internal.runtime.script.parser.scope.ClassScopeFactory;
import dollar.internal.runtime.script.parser.scope.FileScope;
import dollar.internal.runtime.script.parser.scope.ScriptScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jparsec.OperatorTable;
import org.jparsec.Parser;
import org.jparsec.Parsers;
import org.jparsec.Terminals;
import org.jparsec.Token;
import org.jparsec.TokenMap;
import org.jparsec.Tokens;
import org.pegdown.Extensions;
import org.pegdown.PegDownProcessor;
import org.pegdown.ast.RootNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.google.common.io.Files.asCharSource;
import static dollar.api.DollarStatic.$;
import static dollar.api.DollarStatic.$void;
import static dollar.api.scripting.ScriptingSupport.compile;
import static dollar.api.types.meta.MetaConstants.*;
import static dollar.internal.runtime.script.DollarUtilFactory.util;
import static dollar.internal.runtime.script.api.OperatorPriority.EQ_PRIORITY;
import static dollar.internal.runtime.script.parser.DollarLexer.*;
import static dollar.internal.runtime.script.parser.Func.*;
import static dollar.internal.runtime.script.parser.SourceNodeOptions.NO_SCOPE;
import static dollar.internal.runtime.script.parser.Symbols.*;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.jparsec.Parsers.*;

public class DollarParserImpl implements DollarParser {
    @NotNull
    public static final String NAMED_PARAMETER_META_ATTR = "__named_parameter";
    @NotNull
    private static final Logger log = LoggerFactory.getLogger("DollarParser");
    @NotNull
    private final ClassLoader classLoader;
    @NotNull
    private final Map<VarKey, var> exports = new ConcurrentHashMap<>();
    @NotNull
    private final ParserOptions options;
    @Nullable
    private String file;

    public DollarParserImpl(@NotNull ParserOptions options) {
        this.options = options;
        classLoader = DollarParser.class.getClassLoader();
    }

    public DollarParserImpl(@NotNull ParserOptions options, @NotNull ClassLoader classLoader) {
        this.options = options;
        this.classLoader = classLoader;

    }

    private Parser<var> assertExpression(@NotNull Parser.Reference<var> ref, boolean pure) {
        return OP(ASSERT)
                       .next(
                               or(
                                       array(STRING_LITERAL.followedBy(OP(PAIR)), ref.lazy()),
                                       array(OP(PAIR).optional(null), ref.lazy()
                                       )
                               )
                                       .token()
                                       .map(token -> {
                                           Object[] objects = (Object[]) token.value();
                                           var message = (var) objects[0];
                                           var condition = (var) objects[1];
                                           return util().reactiveNode(ASSERT, pure, condition, token, this,
                                                                      i -> assertFunc(message, condition));
                                       }));
    }

    private Parser<Function<? super var, ? extends var>> assignmentOperator(@NotNull Parser.Reference<var> ref,
                                                                            boolean pure) {
        assert ASSIGNMENT.validForPure(pure);

        return array(KEYWORD(EXPORT).optional(null),//0
                     or(
                             KEYWORD(CONST),
                             KEYWORD(VOLATILE),
                             KEYWORD(VAR)
                     ).optional(null),//1
                     IDENTIFIER.between(OP(LT), OP(GT)).optional(null),//2
                     ref.lazy().between(OP(LEFT_PAREN), OP(RIGHT_PAREN)).optional(null).token().map((Token token) -> {
                         if (token.value() != null) {
                             ((var) token.value()).meta(CONSTRAINT_SOURCE, new SourceCode(util().scope(), token));
                             return token.value();
                         } else {
                             return null;
                         }
                     }),//3
                     OP(DOLLAR).next(ref.lazy().between(OP(LEFT_PAREN), OP(RIGHT_PAREN))).or(IDENTIFIER).or(BUILTIN), //4
                     or(
                             OP(ASSIGNMENT).map(i -> {
                                 var var = $(i.toString());
                                 var.metaAttribute(ASSIGNMENT_TYPE, "assign");
                                 return var;
                             }),
                             OP(WHEN).next(ref.lazy()).followedBy(OP(ASSIGNMENT)).map(var -> {
                                 var.metaAttribute(ASSIGNMENT_TYPE, "when");
                                 return var;
                             }),
                             OP(SUBSCRIBE_ASSIGN).map(i -> {
                                 var var = $(i.toString());
                                 var.metaAttribute(ASSIGNMENT_TYPE, "subscribe");
                                 return var;
                             })
                     )//5
        ).token().map(new AssignmentOperator(pure, this));
    }

    @NotNull
    private Parser<var> blockExpression(@NotNull Parser<var> parentParser, boolean pure) {
        Parser.Reference<var> ref = Parser.newReference();
        //Now we do the complex part, the following will only return the last value in the
        //block when the block is evaluated, but it will trigger execution of the rest.
        //This gives it functionality like a conventional function in imperative languages
        Parser<var> or = (or(parentParser, parentParser.between(OP_NL(LEFT_BRACE), NL_OP(RIGHT_BRACE))))
                                 .sepBy1(SEMICOLON_TERMINATOR)
                                 .followedBy(SEMICOLON_TERMINATOR.optional(null))
                                 .map(var -> var)
                                 .token()
                                 .map(token -> util().node(BLOCK_OP,
                                                           "block-" + util().shortHash(token), pure,
                                                           this, token, (List<var>) token.value(),
                                                           i -> blockFunc(Integer.MAX_VALUE, (List<var>) token.value())));
        ref.set(or);
        return or;
    }

    @NotNull
    private Parser<var> builtin(boolean pure) {
        return BUILTIN.token()
                       .map(token -> util().reactiveNode(BUILTIN_OP, pure, (var) token.value(), token, this,
                                                         args -> Builtins.execute(token.toString(), emptyList(), pure)));
    }

    private Parser<Function<? super var, ? extends var>> castOperator(boolean pure) {
        return KEYWORD(AS)
                       .next(IDENTIFIER)
                       .token()
                       .map(token -> lhs -> {
                                assert CAST.validForPure(pure);
                                return util().reactiveNode(CAST, "cast-" + token.toString().toLowerCase(), pure, token, lhs,
                                                           (var) token.value(), this,
                                                           i -> castFunc(lhs, token.toString())
                                );
                            }
                       );
    }

    private Parser<var> classExpression(@NotNull Parser.Reference<var> ref) {
        return KEYWORD_NL(CLASS_OP)
                       .next(
                               array(IDENTIFIER,
                                     OP_NL(LEFT_BRACE).next(
                                             (ref.lazy().prefix(
                                                     or(assignmentOperator(ref, false),
                                                        definitionOperator(ref, false)
                                                     )
                                             )).sepBy1(SEMICOLON_TERMINATOR).followedBy(SEMICOLON_TERMINATOR.optional(null))
                                     ).followedBy(OP(RIGHT_BRACE))
                               )
                       )
                       .token()
                       .map(token -> {
                           Object[] objects = (Object[]) token.value();
                           ArrayList<var> in = new ArrayList<>();
                           in.add((var) objects[0]);
                           in.addAll((List<var>) objects[1]);
                           return util().node(CLASS_OP, "class-" + objects[0],
                                              false, this, token, in,
                                              i -> {
                                                  ClassScopeFactory factory = new ClassScopeFactory(util().scope(),
                                                                                                    objects[0].toString(),
                                                                                                    (List<var>) objects[1], false,
                                                                                                    false);

                                                  util().scope().registerClass(objects[0].toString(), factory);
                                                  return $void();
                                              });
                       });
    }

    private Parser<var> collectExpression(@NotNull Parser.Reference<var> ref, boolean pure) {
        return KEYWORD_NL(COLLECT_OP)
                       .next(
                               array(ref.lazy(),
                                     KEYWORD(UNTIL).next(ref.lazy()).optional(null),
                                     KEYWORD(UNLESS).next(ref.lazy()).optional(null),
                                     ref.lazy())
                       )
                       .token()
                       .map(new CollectOperator(this, pure));
    }

    private Parser<Function<? super var, ? extends var>> definitionOperator(@NotNull Parser.Reference<var> ref,
                                                                            boolean pure) {

        return or(
                array(
                        KEYWORD(EXPORT).optional(null),//0
                        KEYWORD(CONST).optional(null), //1
                        IDENTIFIER.between(OP(LT), OP(GT)).optional(null),//2
                        (OP(DOLLAR).next(ref.lazy().between(OP(LEFT_PAREN), OP(RIGHT_PAREN))).or(IDENTIFIER))
                                .token().map((Token token) -> {
                            ((var) token.value()).meta(CONSTRAINT_SOURCE, new SourceCode(util().scope(), token));
                            return token.value();
                        }), //3
                        OP(DEFINITION) //4

                ).token().map(new DefinitionOperator(pure, this, false)),
                array(
                        KEYWORD(EXPORT).optional(null), //0
                        IDENTIFIER.between(OP(LT), OP(GT)).optional(null), //1
                        KEYWORD(DEF), //2
                        IDENTIFIER //3

                ).token().map(new DefinitionOperator(pure, this, true))

        );
    }

    private Parser<var> dollarIdentifier(@NotNull Parser.Reference<var> ref) {
        return OP(DOLLAR).next(
                array(Terminals.Identifier.PARSER, OP(DEFAULT).next(ref.lazy()).optional(null)).between(
                        OP(LEFT_BRACE),
                        OP(RIGHT_BRACE)
                )
        )
                       .token()
                       .map(token -> {
                           Object[] objects = (Object[]) token.value();
                           var defaultVal = (var) objects[1];
                           return util().variableNode(false, VarKey.of(objects[0].toString()), false, defaultVal, token, this);
                       });
    }

    private Parser<var> everyExpression(@NotNull Parser<var> expression, boolean pure) {
        return KEYWORD_NL(EVERY_OP)
                       .next(array(unitExpression(false),
                                   KEYWORD(UNTIL).next(expression).optional(null),
                                   KEYWORD(UNLESS).next(expression).optional(null),
                                   expression))
                       .token()
                       .map(token -> {
                           assert EVERY_OP.validForPure(pure);
                           final AtomicInteger count = new AtomicInteger(-1);
                           Object[] objects = (Object[]) token.value();
                           var durationVar = (var) objects[0];
                           var until = (var) objects[1];
                           var unless = (var) objects[2];
                           var block = (var) objects[3];
                           return util().reactiveNode(EVERY_OP, false, block, token, this,
                                                      i -> everyFunc(count, durationVar, until, unless, block));

                       });
    }

    @Override
    public void export(@NotNull VarKey name, @NotNull var export) {
        export.meta(SCOPES, new ArrayList<>(util().scopes()));
        exports.put(name, export);
    }

    @NotNull
    @Override
    public ParserOptions options() {
        return options;
    }

    @Override
    @NotNull
    public var parse(@NotNull Scope scope, @NotNull String source) {
        DollarStatic.context().parser(this);
        var v = util().inScope(false, scope, newScope -> {
            DollarStatic.context().classLoader(classLoader);
            Parser<?> parser = script();
            try {
                parser.from(TOKENIZER, IGNORED).parse(source);
            } catch (RuntimeException e) {
                ErrorHandlerFactory.instance().handleTopLevel(e, null, (file != null) ? new File(file) : null);

            }
            HashMap<var, var> exportMap = new HashMap<>();
            exports.forEach((varKey, var) -> exportMap.put(DollarFactory.fromStringValue(varKey.asString()), var));
            return $(exportMap);
        }).orElseThrow(() -> new AssertionError("Optional should not be null here"));
        if (v != null) {
            return v;
        } else {
            throw new AssertionError("parse should not return null");
        }

    }

    @Override
    @NotNull
    public var parse(@NotNull File file, boolean parallel) throws Exception {

        this.file = file.getAbsolutePath();
        if (file.getName().endsWith(".md") || file.getName().endsWith(".markdown")) {
            return parseMarkdown(file);
        } else {
            String source = new String(Files.readAllBytes(file.toPath()));
            return parse(new FileScope(source, file, true, false), source);
        }

    }

    @Override
    @NotNull
    public var parse(@NotNull InputStream in, boolean parallel, @NotNull Scope scope) throws Exception {
        String source = new String(ByteStreams.toByteArray(in));
        return parse(new ScriptScope(scope, source, "(stream-scope)", true, false), source);
    }

    @Override
    @NotNull
    public var parse(@NotNull InputStream in, @NotNull String file, boolean parallel) throws Exception {
        this.file = file;
        String source = new String(ByteStreams.toByteArray(in));
        return parse(new FileScope(source, new File(file), true, false), source);
    }

    @Override
    @NotNull
    public var parse(@NotNull String source, boolean parallel) throws Exception {
        return parse(new ScriptScope(source, "(string)", true, false), source);
    }

    @Override
    @NotNull
    public var parseMarkdown(@NotNull String source) {
        PegDownProcessor processor = new PegDownProcessor(Extensions.FENCED_CODE_BLOCKS);
        RootNode rootNode = processor.parseMarkdown(source.toCharArray());
        rootNode.accept(new CodeExtractionVisitor());
        return $();
    }

    @NotNull
    private Parser<var> expression(final boolean pure) {
        Parser<var> main;
        Parser.Reference<var> ref = Parser.newReference();
        return expressionInternal(pure, ref);

    }

    @NotNull
    private Parser<var> expressionInternal(boolean pure, @NotNull Parser.Reference<var> ref) {
        Parser<var> main;
        if (pure) {
            main = ref.lazy()
                           .between(OP(LEFT_PAREN), OP(RIGHT_PAREN))
                           .or(Parsers.or(unitExpression(true),
                                          listExpression(ref.lazy(), true),
                                          mapExpression(ref.lazy(), true),
                                          assertExpression(ref, true),
                                          collectExpression(ref, true),
                                          windowExpression(ref, true),
                                          whenExpression(ref.lazy(), true),
                                          functionCall(true),
                                          rangeExpression(ref, true),
                                          variableUsageOperator(ref, true),
                                          DECIMAL_LITERAL,
                                          INTEGER_LITERAL,
                                          STRING_LITERAL,
                                          IDENTIFIER_KEYWORD,
                                          variableRef(true),

                                          builtin(true)))
                           .or(blockExpression(ref.lazy(), true).between(OP_NL(LEFT_BRACE), NL_OP(RIGHT_BRACE)));
        } else {
            main = ref.lazy()
                           .between(OP(LEFT_PAREN), OP(RIGHT_PAREN))
                           .or(Parsers.or(unitExpression(false),
                                          listExpression(ref.lazy(), false),
                                          mapExpression(ref.lazy(), false),
                                          KEYWORD(PURE).next(expression(true)),
                                          classExpression(ref),
                                          newExpression(ref, false),
                                          thisRef(false),
//                                          printExpression(ref, false),
                                          moduleExpression(ref),
                                          assertExpression(ref, false),
                                          collectExpression(ref, false),
                                          windowExpression(ref, false),
                                          whenExpression(ref.lazy(), false),
                                          everyExpression(ref.lazy(), false),
                                          functionCall(false),
                                          scriptExpression(ref, false),
                                          pureDefinitionOperator(ref),
                                          rangeExpression(ref, false),
                                          forkExpression(ref, false),
                                          variableUsageOperator(ref, false),
                                          URL,
                                          DECIMAL_LITERAL,
                                          INTEGER_LITERAL,
                                          STRING_LITERAL,
                                          dollarIdentifier(ref),
                                          IDENTIFIER_KEYWORD,
                                          builtin(false),
                                          variableRef(false)))
                           .or(blockExpression(ref.lazy(), false).between(OP_NL(LEFT_BRACE), NL_OP(RIGHT_BRACE)));
        }

        OperatorTable<var> table = new OperatorTable<>();

        table = infixl(pure, table, PLUS, var::$plus);
        table = infixl(pure, table, MINUS, var::$minus);
        table = infixl(pure, table, CHOOSE, var::$choose);
        table = infixl(pure, table, DEFAULT, var::$default);
        table = infixl(pure, table, DIVIDE, var::$divide);
        table = infixl(pure, table, MOD, var::$modulus);


        table = infixl(pure, table, INEQUALITY_OPERATOR, var::$notEquals);
        table = infixl(pure, table, EQUALITY, var::$equals);
        table = infixl(pure, table, AND, DollarStatic::$and);
        table = infixl(pure, table, OR, DollarStatic::$or);
        table = infixl(pure, table, LT, DollarStatic::$lt);
        table = infixl(pure, table, GT, DollarStatic::$gt);
        table = infixl(pure, table, LT_EQUALS, DollarStatic::$lte);
        table = infixl(pure, table, GT_EQUALS, DollarStatic::$gte);
        table = infixl(pure, table, MULTIPLY, Func::multiplyFunc);
        table = infixl(pure, table, PAIR, Func::pairFunc);
        table = infixl(pure, table, ELSE, Func::elseFunc);
        table = infixl(pure, table, IN, Func::inFunc);
        table = infixl(pure, table, WHEN, Func::listenFunc);

        table = infixl(pure, table, EACH, (lhs, rhs) -> eachFunc(pure, lhs, rhs));
        table = infixl(pure, table, REDUCE, (lhs, rhs) -> reduceFunc(pure, lhs, rhs));
        table = infixl(pure, table, CAUSES, (lhs, rhs) -> causesFunc(pure, lhs, rhs));


        table = infixlReactive(pure, table, ASSERT_EQ_REACT, Func::assertEqualsFunc);
        table = infixlUnReactive(pure, table, ASSERT_EQ_UNREACT, Func::assertEqualsFunc);


        table = postfix(pure, table, DEC, (v, u) -> v.$dec());
        table = postfix(pure, table, INC, (v, u) -> v.$inc());

        table = prefix(pure, table, NEGATE, (v, u) -> v.$negate());
        table = prefix(pure, table, SIZE, (v, u) -> v.$size());

        table = prefix(pure, table, NOT, (v, u) -> DollarStatic.$not(v));
        table = prefix(pure, table, ERROR, (v, u) -> Func.errorFunc(v));
        table = prefix(pure, table, TRUTHY, (v, u) -> DollarStatic.$truthy(v));

        table = postfix(pure, table, MIN, (v, u) -> v.$min(false));
        table = postfix(pure, table, MAX, (v, u) -> v.$max(false));
        table = postfix(pure, table, SUM, (v, u) -> v.$sum(false));
        table = postfix(pure, table, PRODUCT, (v, u) -> v.$product(false));
        table = postfix(pure, table, SPLIT, (v, u) -> v.$list());
        table = prefix(pure, table, SORT, (v, u) -> v.$sort(false));
        table = postfix(pure, table, REVERSE, (v, u) -> v.$reverse(false));
        table = postfix(pure, table, UNIQUE, (v, u) -> v.$unique(false));
        table = postfix(pure, table, AVG, (v, u) -> v.$avg(false));

        table = prefixUnReactive(pure, table, FIX, (v1, v12) -> Func.fixFunc(v1));

        table = table.prefix(parallelOperator(ref, pure), PARALLEL.priority());
        table = table.prefix(serialOperator(ref, pure), SERIAL.priority());

        //More complex expression syntax
        table = table.postfix(pipeOperator(ref, pure), PIPE_OP.priority());
        table = table.postfix(isOperator(pure), EQ_PRIORITY);
        table = table.postfix(memberOperator(ref, pure), MEMBER.priority());
        table = table.postfix(subscriptOperator(ref, pure), SUBSCRIPT_OP.priority());
        table = table.postfix(castOperator(pure), CAST.priority());
        table = table.postfix(parameterOperator(ref, pure), PARAM_OP.priority());

        table = table.prefix(ifOperator(ref, pure), IF_OP.priority());
        table = table.prefix(forOperator(ref, pure), FOR_OP.priority());
        table = table.prefix(whileOperator(ref, pure), WHILE_OP.priority());


        table = table.prefix(assignmentOperator(ref, pure), ASSIGNMENT.priority());
        table = table.prefix(definitionOperator(ref, pure), DEFINITION.priority());

        if (!pure) {
            table = infixl(false, table, PUBLISH, Func::publishFunc);
            table = infixl(false, table, SUBSCRIBE, Func::subscribeFunc);
            table = infixl(false, table, WRITE_SIMPLE, Func::writeFunc);
            table = prefix(false, table, READ_SIMPLE, (v, u) -> Func.readFunc(v));

            table = prefix(false, table, DRAIN, (v, u) -> v.$drain());
            table = prefix(false, table, ALL, (v, u) -> v.$all());
            table = prefix(false, table, STOP, (v, u) -> v.$stop());
            table = prefix(false, table, START, (v, u) -> v.$start());
            table = prefix(false, table, PAUSE, (v, u) -> v.$pause());
            table = prefix(false, table, UNPAUSE, (v, u) -> v.$unpause());
            table = prefix(false, table, DESTROY, (v, u) -> v.$destroy());
            table = prefix(false, table, CREATE, (v, u) -> v.$create());
            table = prefix(false, table, STATE, (v, u) -> v.$state());

            table = prefix(false, table, OUT, (v, u) -> printFunc(this, u, OUT, Arrays.asList(v)));
            table = prefix(false, table, DEBUG, (v, u) -> printFunc(this, u, DEBUG, Arrays.asList(v)));
            table = prefix(false, table, ERR, (v, u) -> printFunc(this, u, ERR, Arrays.asList(v)));

            table = table.prefix(writeOperator(ref), WRITE_OP.priority());
            table = table.prefix(readOperator(), READ_OP.priority());
        }
        Parser<var> parser = table.build(main);
        ref.set(parser);
        return parser;
    }

    private Parser<Function<? super var, ? extends var>> forOperator(final @NotNull Parser.Reference<var> ref, boolean pure) {

        return array(KEYWORD(FOR_OP), IDENTIFIER, KEYWORD(IN), ref.lazy())
                       .token()
                       .map(token -> {
                           assert FOR_OP.validForPure(pure);
                           Object[] objects = (Object[]) token.value();
                           String varName = objects[1].toString();
                           var iterable = (var) objects[3];
                           return rhs -> util().reactiveNode(FOR_OP, pure, rhs, token, this,
                                                             i -> forFunc(pure, varName, iterable, rhs));
                       });
    }

    @NotNull
    private Parser<var> forkExpression(@NotNull Parser.Reference<var> ref, boolean pure) {
        return KEYWORD_NL(FORK)
                       .next(ref.lazy())
                       .token()
                       .map(token -> util().node(FORK, "fork-execute", pure,
                                                 this, token, Arrays.asList((var) token.value()),
                                                 i -> {
                                                     log.debug("Executing in background ...");
                                                     return executor.forkAndReturnId(new SourceCode(util().scope(), token),
                                                                                     (var) token.value(), in -> in.$fixDeep(false));
                                                 })
                       );
    }

    private Parser<var> functionCall(boolean pure) {
        return array(IDENTIFIER.or(BUILTIN).followedBy(OP(LEFT_PAREN).peek()))
                       .token()
                       .map(token -> {
                           Object[] objects = (Object[]) token.value();
                           var functionName = (var) objects[0];
                           return util().node(FUNCTION_NAME_OP, pure, this, token, singletonList(functionName), i -> functionName);
                       });
    }

    private Parser<Function<var, var>> ifOperator(@NotNull Parser.Reference<var> ref, boolean pure) {
        return KEYWORD_NL(IF_OP)
                       .next(ref.lazy())
                       .token()
                       .map(token -> {
                           var lhs = (var) token.value();
                           assert IF_OP.validForPure(pure);
                           return rhs -> util().node(IF_OP, pure, this, token, asList(lhs, rhs), i -> ifFunc(pure, lhs, rhs));
                       });
    }

    @NotNull
    private OperatorTable<var> infixl(boolean pure,
                                      @NotNull OperatorTable<var> table,
                                      @NotNull Op operator,
                                      @NotNull BiFunction<var, var, var> func) {
        return table.infixl(op(operator, new BinaryOp(this, operator, func, pure)), operator.priority());
    }

    @NotNull
    private OperatorTable<var> infixlReactive(boolean pure,
                                              @NotNull OperatorTable<var> table,
                                              @NotNull Op operator,
                                              @NotNull BiFunction<var, var, var> f) {
        return table.infixl(op(operator, new BinaryOp(false, operator, this, f, pure)),
                            operator.priority());
    }

    @NotNull
    private OperatorTable<var> infixlUnReactive(boolean pure,
                                                @NotNull OperatorTable<var> table,
                                                @NotNull Op operator,
                                                @NotNull BiFunction<var, var, var> f) {
        return table.infixl(op(operator, new BinaryOp(true, operator, this, f, pure)),
                            operator.priority());
    }

    private Parser<Function<? super var, ? extends var>> isOperator(boolean pure) {
        return KEYWORD(IS_OP)
                       .next(IDENTIFIER.sepBy(OP(COMMA)))
                       .token()
                       .map(token -> lhs -> {
                                assert IS_OP.validForPure(pure);
                                return util().reactiveNode(IS_OP, pure, lhs, token, this,
                                                           i -> isFunc(lhs, (List<var>) token.value())

                                );
                            }
                       );
    }

    private Parser<var> listExpression(@NotNull Parser<var> expression, boolean pure) {
        return array(
                OP_NL(PARALLEL).optional(null),
                OP_NL(LEFT_BRACKET).next(expression.sepBy(COMMA_OR_NEWLINE_TERMINATOR)).followedBy(
                        COMMA_OR_NEWLINE_TERMINATOR.optional(null))
                        .followedBy(NL_OP(RIGHT_BRACKET))
        ).token().map(
                token -> {
                    Object[] objects = (Object[]) token.value();
                    boolean parallel = objects[0] != null;
                    List<var> entries = (List<var>) objects[1];
                    final var node = util().node(LIST_OP, "list-" + util().shortHash(token), pure, this, token, entries,
                                                 vars -> {
                                                     log.info("Fixing list {}", parallel ? "parallel" : "serial");
                                                     return DollarFactory.fromList(entries).$fix(1, parallel);
                                                 }
                    );
                    entries.forEach(entry -> entry.$listen(i -> node.$notify()));
                    return node;

                });
    }

    @NotNull
    private Parser<var> mapExpression(@NotNull Parser<var> expression, boolean pure) {
        return array(
                OP_NL(PARALLEL).optional(null), OP_NL(LEFT_BRACE)
                                                        .next(
                                                                expression.sepBy(COMMA_TERMINATOR)
                                                        )
                                                        .followedBy(COMMA_TERMINATOR.optional(null))
                                                        .followedBy(NL_OP(RIGHT_BRACE))
        ).token().map(token -> {
            Object[] objects = (Object[]) token.value();
            boolean parallel = objects[0] != null;
            List<var> o = (List<var>) objects[1];
            final var node = util().node(MAP_OP, "map-" + util().shortHash(token), pure, this, token, o,
                                         i -> mapFunc(parallel, o));
            o.forEach(entry -> entry.$listen(i -> node.$notify()));
            return node;
        });
    }

    private Parser<Function<? super var, ? extends var>> memberOperator(@NotNull Parser.Reference<var> ref, boolean pure) {
        return OP(MEMBER).followedBy(OP(MEMBER).not())
                       .next(ref.lazy().between(OP(LEFT_PAREN), OP(RIGHT_PAREN)).or(IDENTIFIER))
                       .token()
                       .map(rhs -> lhs -> {
                           assert MEMBER.validForPure(pure);
                           var result = util().reactiveNode(MEMBER, pure, rhs, lhs, (var) rhs.value(), this,
                                                            i -> lhs.$get(
                                                                    DollarFactory.fromStringValue(
                                                                            VarKey.removePrefix(rhs.toString()))));
                           return result;
                       });
    }

    private Parser<var> moduleExpression(@NotNull Parser.Reference<var> ref) {
        final Parser<Object[]> param = array(IDENTIFIER.followedBy(OP(ASSIGNMENT)), ref.lazy());

        final Parser<List<var>> parameters =
                KEYWORD(WITH).optional(null).next((param).map(objects -> {
                    var result = (var) objects[1];
                    result.metaAttribute(NAMED_PARAMETER_META_ATTR, objects[0].toString());
                    return result;
                }).sepBy(OP(COMMA)).between(OP(LEFT_PAREN), OP(RIGHT_PAREN)));

        return array(KEYWORD(MODULE_OP), STRING_LITERAL.or(URL),
                     parameters.optional(null)).token()
                       .map(token -> {
                           Object[] objects = (Object[]) token.value();
                           return util().node(MODULE_OP, false, this,
                                              token, emptyList(),
                                              i -> moduleFunc(this, objects[1].toString(), (Iterable<var>) objects[2]));

                       });

    }

    private Parser<var> newExpression(@NotNull Parser.Reference<var> ref, boolean pure) {
        return KEYWORD_NL(NEW_OP)
                       .next(
                               array(IDENTIFIER,
                                     OP(LEFT_PAREN).next(
                                             or(array(IDENTIFIER.followedBy(OP(ASSIGNMENT)), ref.lazy()),
                                                array(OP(ASSIGNMENT).optional(null), ref.lazy())).map(
                                                     objects -> {
                                                         assert PARAM_OP.validForPure(pure);
                                                         //Is it a named parameter
                                                         if (objects[0] != null) {
                                                             //yes so let's add the name as metadata to the value
                                                             var result = (var) objects[1];
                                                             result.metaAttribute(NAMED_PARAMETER_META_ATTR, objects[0].toString());
                                                             return result;
                                                         } else {
                                                             //no, just use the value
                                                             return (var) objects[1];
                                                         }
                                                     }).sepBy(COMMA_TERMINATOR)).followedBy(OP(RIGHT_PAREN)))
                                       .token()
                                       .map(token -> {
                                           Object[] objects = (Object[]) token.value();
                                           String name = objects[0].toString();
                                           return util().node(NEW_OP, "new-" + objects[0], pure, this, token,
                                                              Arrays.asList((var) objects[0]),
                                                              i -> util().scope().dollarClassByName(name).instance(
                                                                      (List<var>) objects[1]));
                                       }));
    }

    private <T> Parser<T> op(@NotNull Op def, @NotNull T value) {
        return OP(def).token().map(new SourceMapper<>(value));

    }

    private Parser<Function<var, var>> parallelOperator(@NotNull Parser.Reference<var> ref, boolean pure) {
        return OP(PARALLEL)
                       .token()
                       .map(token -> rhs -> {
                           assert PARALLEL.validForPure(pure);
                           return util().node(PARALLEL, pure, this, token, singletonList(rhs), ns -> rhs.$fixDeep(true));
                       });
    }

    private Parser<Function<? super var, ? extends var>> parameterOperator(@NotNull Parser.Reference<var> ref,
                                                                           boolean pure) {

        return OP(LEFT_PAREN).next(
                or(array(IDENTIFIER.followedBy(OP(ASSIGNMENT)), ref.lazy()),
                   array(OP(ASSIGNMENT).optional(null), ref.lazy())).map(
                        objects -> {
                            assert PARAM_OP.validForPure(pure);
                            //Is it a named parameter
                            if (objects[0] != null) {
                                //yes so let's add the name as metadata to the value
                                var result = (var) objects[1];
                                result.metaAttribute(NAMED_PARAMETER_META_ATTR, objects[0].toString());
                                return result;
                            } else {
                                //no, just use the value
                                return (var) objects[1];
                            }
                        }).sepBy(COMMA_TERMINATOR)).followedBy(OP(RIGHT_PAREN))
                       .token().map(new ParameterOperator(this, pure));
    }

    @NotNull
    public var parseMarkdown(@NotNull File file) throws IOException {
        PegDownProcessor pegDownProcessor = new PegDownProcessor(Extensions.FENCED_CODE_BLOCKS);
        RootNode root =
                pegDownProcessor.parseMarkdown(asCharSource(file, Charset.forName("utf-8"))
                                                       .read().toCharArray());
        root.accept(new CodeExtractionVisitor());
        return $();
    }

    private Parser<Function<? super var, ? extends var>> pipeOperator(@NotNull Parser.Reference<var> ref, boolean pure) {
        return (OP(PIPE_OP).optional(null)).next(
                longest(BUILTIN,
                        IDENTIFIER,
                        functionCall(pure).postfix(parameterOperator(ref, pure)),
                        ref.lazy().between(OP(LEFT_PAREN), OP(RIGHT_PAREN))
                )).token()
                       .map(token -> {
                           assert PIPE_OP.validForPure(pure);
                           var rhs = (var) token.value();
                           return lhs -> util().reactiveNode(PIPE_OP, pure, rhs, token, this,
                                                             i -> pipeFunc(this, pure, token, rhs, lhs)
                           );
                       });
    }

    @NotNull
    private OperatorTable<var> postfix(boolean pure,
                                       @NotNull OperatorTable<var> table,
                                       @NotNull Op operator,
                                       @NotNull BiFunction<var, Source, var> f2) {

        OperatorTable<var> result = table;
        if (operator.symbol() != null) {
            result = result.postfix(DollarLexer.OPERATORS.token(operator.symbol()).token().map(
                    new SourceMapper<>(new UnaryOp(this, operator, f2, pure))),
                                    operator.priority());
        }
        if (operator.keyword() != null) {
            result = result.prefix(KEYWORDS.token(operator.keyword()).token().map(
                    new SourceMapper<>(new UnaryOp(this, operator, f2, pure))), operator.priority());
        }
        return result;
    }

    @NotNull
    private OperatorTable<var> prefix(boolean pure,
                                      @NotNull OperatorTable<var> table,
                                      @NotNull Op operator,
                                      @NotNull BiFunction<var, Source, var> f) {
        return table.prefix(op(operator, new UnaryOp(this, operator, f, pure)), operator.priority());
    }

    @NotNull
    private OperatorTable<var> prefixUnReactive(boolean pure,
                                                @NotNull OperatorTable<var> table,
                                                @NotNull Op operator,
                                                @NotNull BiFunction<var, Source, var> f) {
        return table.prefix(op(operator, new UnaryOp(true, f, operator, this, pure)), operator.priority());
    }

//    private Parser<var> printExpression(@NotNull Parser.Reference<var> ref, boolean pure) {
//        return array(or(KEYWORD(PRINT).map(i -> OUT), NL_OP(ERR).map(i -> ERR), NL_OP(DEBUG).map(i -> DEBUG)),
//
//                     ref.lazy().many1()
//        ).followedBy(or(SEMICOLON_TERMINATOR, COMMA_OR_NEWLINE_TERMINATOR).peek())
//                       .token()
//                       .map(token -> printFunc(this, new SourceCode(token), (Op) ((Object[]) token.value())[0],
//                                               (List<var>) ((Object[]) token.value())[1]));
//    }

    private Parser<var> pureDefinitionOperator(@NotNull Parser.Reference<var> ref) {
        assert PURE_OP.validForPure(true);

        return KEYWORD(PURE).next(or(
                array(
                        KEYWORD(EXPORT).optional(null),//0
                        IDENTIFIER.between(OP(LT), OP(GT)).optional(null),//1
                        OP(DOLLAR).next(ref.lazy().between(OP(LEFT_PAREN), OP(RIGHT_PAREN))).or(IDENTIFIER).token().map(
                                (Token token) -> {
                                    ((var) token.value()).meta(CONSTRAINT_SOURCE,
                                                               new SourceCode(util().scope(), token));
                                    return token.value();
                                }), //2
                        OP(DEFINITION),//3
                        expression(true)//4


                ).token().map(new PureDefinitionOperator(this, false)),

                KEYWORD(EXPORT).optional(null), //0
                IDENTIFIER.between(OP(LT), OP(GT)).optional(null), //1
                KEYWORD(DEF), //2
                IDENTIFIER, //3
                expression(true)//4


                                  ).token().map(new PureDefinitionOperator(this, true))

        );
    }

    private Parser<var> rangeExpression(@NotNull Parser.Reference<var> expression, boolean pure) {
        return array(or(OP(LEFT_BRACKET), OP(LEFT_PAREN)),//0
                     expression.lazy().optional(null),//1
                     OP(RANGE),//2
                     expression.lazy().optional(null),//3
                     or(OP(RIGHT_BRACKET), OP(RIGHT_PAREN))//4
        ).token()
                       .map(
                               token -> {
                                   Object[] objects = (Object[]) token.value();
                                   var upperBound = (var) objects[3];
                                   var lowerBound = (var) objects[1];
                                   return util().node(RANGE, pure, this, token, Arrays.asList(lowerBound, upperBound),
                                                      args -> {
                                                          boolean closedLeft = objects[0].toString().equals(LEFT_BRACKET.symbol());
                                                          boolean closedRight = objects[4].toString().equals(
                                                                  RIGHT_BRACKET.symbol());
                                                          boolean lowerBounds = lowerBound != null;
                                                          boolean upperBounds = upperBound != null;
                                                          if (!closedLeft && !closedRight && (lowerBound != null) && (upperBound != null)
                                                                      && lowerBound.$unwrap().equals(upperBound.$unwrap())) {
                                                              throw new DollarScriptException("Cannot create an open range with " +
                                                                                                      "identical upper and lower " +
                                                                                                      "bounds", new SourceCode
                                                                                                                        (util().scope(),
                                                                                                                         token));
                                                          } else {
                                                              return DollarFactory.wrap(
                                                                      new DollarRange(lowerBounds, upperBounds,
                                                                                      closedLeft, closedRight, lowerBound,
                                                                                      upperBound));
                                                          }
                                                      });

                               });
    }

    private Parser<Function<? super var, ? extends var>> readOperator() {
        return array(KEYWORD(READ_OP),
                     KEYWORD(BLOCK).optional(null),
                     KEYWORD(MUTATE).optional(null)
        ).followedBy(KEYWORD(FROM).optional(null))
                       .token()
                       .map(token -> {
                           assert READ_OP.validForPure(false);
                           Object[] objects = (Object[]) token.value();
                           boolean blocking = objects[1] != null;
                           boolean mutating = objects[2] != null;
                           return rhs -> {
                               List<var> in = asList((var) objects[1], (var) objects[2], rhs);
                               return util().node(READ_OP, false, this, token, in, i -> rhs.$read(blocking, mutating)
                               );
                           };
                       });
    }

    @NotNull
    private Parser<var> script() {
        log.debug("Starting Parse Phase");


        Parser.Reference<var> ref = Parser.newReference();
//        Parser<var> block = block(ref.lazy(), false).between(OP_NL(LEFT_BRACE), NL_OP(RIGHT_BRACE));
        Parser<var> expression = expression(false);
        Parser<var> parser = (TERMINATOR_SYMBOL.optional(null))
                                     .next(expression.followedBy(TERMINATOR_SYMBOL).many1())
                                     .map(expressions -> {
                                         log.debug("Ended Parse Phase");
                                         log.debug("Starting Runtime Phase");
                                         var resultVar = Func.blockFunc(Integer.MAX_VALUE, expressions);
                                         var fixedResult = resultVar.$fixDeep(false);
                                         log.debug("Ended Runtime Phase");
                                         return fixedResult;
                                     });
        ref.set(parser);
        return parser;

    }

    final Parser<var> scriptExpression(@NotNull Parser.Reference<var> ref, boolean pure) {
        return array(or(BUILTIN).or(IDENTIFIER),
                     token(new BacktickScriptMap()))
                       .token()
                       .map((Token token) -> {
                                Object[] objects = (Object[]) token.value();
                                return util().node(SCRIPT_OP, pure,
                                                   this, token, singletonList($void()),
                                                   i -> compile(String.valueOf(objects[0]), String.valueOf(objects[1]),
                                                                util().scope()));
                            }
                       );
    }

    private Parser<Function<var, var>> serialOperator(@NotNull Parser.Reference<var> ref, boolean pure) {
        return OP(SERIAL)
                       .token()
                       .map(token -> rhs -> {
                           assert SERIAL.validForPure(pure);
                           return util().node(SERIAL, pure, this, token, singletonList(rhs), ns -> rhs.$fixDeep(false));
                       });
    }

    private Parser<Function<? super var, ? extends var>> subscriptOperator(@NotNull Parser.Reference<var> ref, boolean pure) {

        return OP(LEFT_BRACKET).next(
                array(
                        ref.lazy().followedBy(OP(RIGHT_BRACKET)),
                        OP(ASSIGNMENT)
                                .next(ref.lazy()).optional(null))
        )
                       .token()
                       .map(token -> {
                           assert SUBSCRIPT_OP.validForPure(pure);
                           Object[] objects = (Object[]) token.value();
                           var expression = (var) objects[0];
                           var subscript = (var) objects[1];

                           return lhs -> {
                               SourceCode source = new SourceCode(util().scope(), token);
                               if (subscript == null) {
                                   return util().reactiveNode(SUBSCRIPT_OP, SUBSCRIPT_OP.name() + "-read", pure, NO_SCOPE, this,
                                                              source,
                                                              lhs, expression, args -> lhs.$get(expression));
                               } else {
                                   return util().node(SUBSCRIPT_OP, SUBSCRIPT_OP.name() + "-write", pure, NO_SCOPE, this, source,
                                                      null, asList(lhs, expression, subscript),
                                                      i -> lhs.$set(expression, subscript));
                               }
                           };
                       });
    }

    @NotNull
    private Parser<var> thisRef(boolean pure) {
        return KEYWORD_NL(THIS).followedBy(OP(LEFT_PAREN).not().peek())
                       .token()
                       .map(token -> util().variableNode(pure, VarKey.of(token.toString()), token, this));
    }

    private Parser<var> unitExpression(boolean pure) {
        return array(DECIMAL_LITERAL.or(INTEGER_LITERAL), BUILTIN)
                       .token()
                       .map(
                               token -> {
                                   Object[] objects = (Object[]) token.value();
                                   var quantity = (var) objects[0];
                                   var unit = (var) objects[1];
                                   return util().node(UNIT_OP, pure, this, token, asList(quantity, unit),
                                                      i -> {
                                                          String unitName = unit.toString();
                                                          if (Builtins.exists(unitName)) {
                                                              return Builtins.execute(unitName, singletonList(quantity), pure);
                                                          } else {
                                                              final var variable = util().variableNode(pure, VarKey.of(unitName),
                                                                                                       token, this);
                                                              util().scope().parameter(VarKey.ONE, quantity);
                                                              return util().fix(variable);
                                                          }
                                                      });
                               });
    }

    @NotNull
    private Parser<var> variableRef(boolean pure) {
        return identifier().followedBy(OP(LEFT_PAREN).not().peek())
                       .token()
                       .map(token -> util().variableNode(pure, VarKey.of(token.toString()), token, this));
    }

    @NotNull
    private Parser<var> variableUsageOperator(final @NotNull Parser.Reference<var> ref, boolean pure) {
        assert VAR_USAGE_OP.validForPure(pure);
        return or(
                OP(DOLLAR)
                        .next(ref.lazy().between(OP(LEFT_PAREN), OP(RIGHT_PAREN)))
                        .token()
                        .map(token -> {
                            @NotNull Pipeable callable = i -> util().variableNode(pure, VarKey.of(token.toString()), token,
                                                                                  this).$fix(2,
                                                                                             false);
                            return util().node(VAR_USAGE_OP, "dynamic-var-name", pure, NO_SCOPE, this,
                                               new SourceCode(util().scope(), token), null,
                                               singletonList((var) token.value()), callable);
                        }),
                OP(DOLLAR)
                        .next(INTEGER_LITERAL)
                        .token()
                        .map(token -> util().variableNode(pure, VarKey.of(token.toString()), true, null, token, this)));
    }

    private Parser<var> whenExpression(@NotNull Parser<var> expression, boolean pure) {
        return KEYWORD_NL(WHEN)
                       .next(array(expression, expression))
                       .token()
                       .map(token -> {
                           assert WHEN.validForPure(pure);
                           Object[] objects = (Object[]) token.value();
                           var lhs = (var) objects[0];
                           var rhs = (var) objects[1];
                           var lambda = util().node(WHEN, pure, this, token, asList(lhs, rhs),
                                                    i -> lhs.isTrue() ? $((Object) rhs.toJavaObject()) : $void()
                           );
                           lhs.$listen(i -> lhs.isTrue() ? $((Object) rhs.toJavaObject()) : $void());
                           return lambda;
                       });
    }

    private Parser<Function<? super var, ? extends var>> whileOperator(final @NotNull Parser.Reference<var> ref, boolean pure) {

        return KEYWORD(WHILE_OP)
                       .next(ref.lazy())
                       .token()
                       .map(token -> {
                           assert WHILE_OP.validForPure(pure);
                           var lhs = (var) token.value();
                           return rhs -> util().node(WHILE_OP, pure, this, token, asList(lhs, rhs),
                                                     i -> whileFunc(pure, lhs, rhs));
                       });
    }

    private Parser<var> windowExpression(@NotNull Parser.Reference<var> ref, boolean pure) {
        return KEYWORD_NL(WINDOW_OP)
                       .next(
                               array(ref.lazy(),//0
                                     KEYWORD(OVER).next(ref.lazy()),//1
                                     KEYWORD(PERIOD).next(ref.lazy()).optional(null),//2
                                     KEYWORD(UNLESS).next(ref.lazy()).optional(null),//3
                                     KEYWORD(UNTIL).next(ref.lazy()).optional(null),//4
                                     ref.lazy())//5

                       )
                       .token()
                       .map(new WindowOperator(this, pure));
    }

    private Parser<Function<? super var, ? extends var>> writeOperator(@NotNull Parser.Reference<var> ref) {
        return array(KEYWORD(WRITE_OP),
                     ref.lazy(),
                     KEYWORD(BLOCK).optional(null),
                     KEYWORD(MUTATE).optional(null)
        ).followedBy(KEYWORD(TO).optional(null))
                       .token()
                       .map(token -> {
                           assert WRITE_OP.validForPure(false);
                           Object[] objects = (Object[]) token.value();
                           var lhs = (var) objects[1];
                           boolean blocking = objects[2] != null;
                           boolean mutating = objects[3] != null;

                           return rhs -> util().node(WRITE_OP, false, this, token, Arrays.asList(lhs, rhs),
                                                     i -> rhs.$write(lhs, blocking, mutating)
                           );
                       });
    }

    private static class BacktickScriptMap implements TokenMap<String> {
        @Nullable
        @Override
        public String map(@NotNull Token token) {
            final Object val = token.value();
            if (val instanceof Tokens.Fragment) {
                Tokens.Fragment c = (Tokens.Fragment) val;
                if (!"backtick".equals(c.tag())) {
                    return null;
                }
                return c.text();
            } else {
                return null;
            }
        }

        @NotNull
        @Override
        public String toString() {
            return "backtick";
        }
    }

    private class SourceMapper<T> implements Function<Token, T> {
        @NotNull
        private final T value;


        SourceMapper(@NotNull T value) {
            this.value = value;
        }

        @NotNull
        @Override
        public T apply(@NotNull Token token) {
            if (value instanceof Operator) {
                ((Operator) value).setSource(new SourceCode(util().scope(), token));
            }
            return value;
        }
    }
}
