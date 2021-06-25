package de.tuda.stg.consys.invariants.subset.parser;

import com.microsoft.z3.Expr;
import de.tuda.stg.consys.invariants.exceptions.UnsupportedJMLExpression;
import de.tuda.stg.consys.invariants.exceptions.WrongJMLArguments;
import de.tuda.stg.consys.invariants.subset.model.ClassModel;
import de.tuda.stg.consys.invariants.subset.model.MergeMethodModel;
import de.tuda.stg.consys.invariants.subset.utils.Z3Binding;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.jmlspecs.jml4.ast.JmlQualifiedNameReference;
import org.jmlspecs.jml4.ast.JmlSingleNameReference;

public class MergeMethodPreconditionExpressionParser extends MethodExpressionParser {

	private final Expr otherConst;

	public MergeMethodPreconditionExpressionParser(Z3Binding smt, ClassModel classModel, MergeMethodModel methodModel, Expr thisConst, Expr otherConst) {
		super(smt, classModel, methodModel, thisConst);
		this.otherConst = otherConst;
	}

	@Override
	public Expr parseExpression(Expression expression) {
		if (expression instanceof JmlQualifiedNameReference) {
			return parseJmlQualifiedNameReference((JmlQualifiedNameReference) expression);
		}

		return super.parseExpression(expression);
	}

	@Override
	public Expr parseJmlSingleReference(JmlSingleNameReference jmlSingleNameReference) {
		Argument mergeArg = getMergeMethod().getArgument();

		if (jmlSingleNameReference.binding.equals(mergeArg.binding)) {
			return otherConst;
		}

		return super.parseJmlSingleReference(jmlSingleNameReference);
	}

	public Expr parseJmlQualifiedNameReference(JmlQualifiedNameReference jmlQualifiedNameReference) {
		Argument mergeArg = getMergeMethod().getArgument();

		if (jmlQualifiedNameReference.binding.equals(mergeArg.binding)) {
			FieldBinding fieldBinding = jmlQualifiedNameReference.otherBindings[0];

			return getClassModel().getField(fieldBinding)
					.map(field -> field.getAccessor().apply(otherConst))
					.orElseThrow(() -> new WrongJMLArguments(jmlQualifiedNameReference));
		}

		throw new UnsupportedJMLExpression(jmlQualifiedNameReference);
	}

	public MergeMethodModel getMergeMethod() {
		return (MergeMethodModel) methodModel;
	}
}
