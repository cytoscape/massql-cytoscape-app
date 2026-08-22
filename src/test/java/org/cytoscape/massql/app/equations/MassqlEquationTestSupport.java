package org.cytoscape.massql.app.equations;

import org.cytoscape.equations.EquationCompiler;
import org.cytoscape.equations.Interpreter;
import org.cytoscape.equations.internal.EquationCompilerImpl;
import org.cytoscape.equations.internal.EquationParserImpl;
import org.cytoscape.equations.internal.interpreter.InterpreterImpl;
import org.cytoscape.model.NetworkTestSupport;

import static org.mockito.Mockito.when;

/**
 * A {@link NetworkTestSupport} whose tables can actually evaluate equations.
 *
 * <p>The stock harness stubs a <em>mock</em> {@link Interpreter}, so a cell holding an equation
 * returns nothing. Its {@code serviceRegistrar} is a Mockito mock and {@code CyTableFactoryImpl}
 * resolves the interpreter once per {@code createTable} rather than at construction, so re-stubbing
 * here -- before any test asks for a network -- is enough to get a real one.
 */
public class MassqlEquationTestSupport extends NetworkTestSupport {

    private final EquationCompilerImpl compiler;

    public MassqlEquationTestSupport() {
        EquationParserImpl parser = new EquationParserImpl(serviceRegistrar);
        // registerFunctionInternal is what the OSGi service listener calls; registerFunction is its
        // deprecated public twin and would warn.
        parser.registerFunctionInternal(new MassqlParseFunction());
        compiler = new EquationCompilerImpl(parser);

        when(serviceRegistrar.getService(Interpreter.class)).thenReturn(new InterpreterImpl());
        when(serviceRegistrar.getService(EquationCompiler.class)).thenReturn(compiler);
    }

    /** A compiler that knows about {@code MASSQL_PARSE}. */
    public EquationCompilerImpl compiler() {
        return compiler;
    }
}
