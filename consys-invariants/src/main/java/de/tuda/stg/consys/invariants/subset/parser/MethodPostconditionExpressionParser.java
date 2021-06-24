package de.tuda.stg.consys.invariants.subset.parser;

import com.google.common.collect.Lists;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Sort;
import de.tuda.stg.consys.invariants.exceptions.UnsupportedJMLExpression;
import de.tuda.stg.consys.invariants.subset.model.AbstractMethodModel;
import de.tuda.stg.consys.invariants.subset.model.ClassModel;
import de.tuda.stg.consys.invariants.subset.model.FieldModel;
import de.tuda.stg.consys.invariants.subset.model.MethodModel;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.NameReference;
import org.eclipse.jdt.internal.compiler.ast.Reference;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.jmlspecs.jml4.ast.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MethodPostconditionExpressionParser extends MethodExpressionParser {

	private Expr oldConst;
	private Expr resultConst; // Can be null, if method has no result.

	/**
	 * @param ctx
	 * @param classModel
	 * @param methodModel
	 * @param thisConst   Substitute all `this` references with the given const. The const needs to have
	 */
	public MethodPostconditionExpressionParser(Context ctx, ClassModel classModel, AbstractMethodModel<?> methodModel, Expr thisConst, Expr oldConst, Expr resultConst) {
		super(ctx, classModel, methodModel, thisConst);

		this.oldConst = oldConst;
		this.resultConst = resultConst;
	}

	@Override
	public Expr parseExpression(Expression expression) {
		// "\old(...)"
		if (expression instanceof JmlOldExpression) {
			return visitOldExpression((JmlOldExpression) expression);
		}

		// \result is the result reference
    	if (expression instanceof JmlResultReference) {
    		return parseJmlResultReference((JmlResultReference) expression);
		}

		return super.parseExpression(expression);
	}


	public Expr visitOldExpression(JmlOldExpression jmlOldExpression) {
		// Change the resolution of `this` to the const for old.
		return withThisReference(oldConst, () -> {
			return parseExpression(jmlOldExpression.expression);
		});
	}

	public Expr parseJmlResultReference(JmlResultReference jmlResultReference) {
		if (resultConst == null || methodModel.returnsVoid())
			throw new IllegalArgumentException("\\result can not be used when method does return void.");

		return resultConst;
	}



	/**
	 * This visit method translates an assignable clause into a postcondition. {@code assignable
	 * \nothing} translates to setting all prestate constants equal to the poststate constants. {@code
	 * assignable a} does the same but leaves {@code a} out, also {@code assignable a[3]} but for
	 * a[3].
	 *
	 * @return the created postcondition from the assignable clause
	 */
	public BoolExpr parseJmlAssignableClause(JmlAssignableClause jmlAssignableClause) {

		if (jmlAssignableClause.expr.equals(JmlKeywordExpression.NOTHING)) {
			// assignable \nothing ==> \old(this) == this
			return ctx.mkEq(oldConst, getThisConst());
		} else if (jmlAssignableClause.expr instanceof JmlStoreRefListExpression) {
			// assignable (a | a[n])

			// collect all java variable references from the assignable clause
			Expression[] references = ((JmlStoreRefListExpression) jmlAssignableClause.expr).exprList;
			BoolExpr result = ctx.mkTrue();

			// collect all names of the variables that are mentioned
			for (FieldModel field : getClassModel().getFields()) {

				if (field.isArray()) {
					// Handle array assignments
					boolean allIndicesAssigned = false;
					List<Expr> assignedIndices = Lists.newLinkedList();

					for (Expression expr : references) {
						if (expr instanceof NameReference) {
							Reference ref = (Reference) expr;

							// Check that ref references a field.
							if (ref.fieldBinding() == null) {
								throw new UnsupportedJMLExpression(ref);
							}

							// Check whether the binding references field.
							if (ref.fieldBinding().equals(field.getDecl().binding)) {
								// The binding is assigned. We cannot make any assertions on that field.
								allIndicesAssigned = true;
								break;
							}
						} else if (expr instanceof JmlArrayRangeStoreRef) {
							// Only handle a[*] expressions for now.
							// TODO: How to handle different array ranges?
							throw new UnsupportedJMLExpression(expr);
						} else if (expr instanceof JmlArrayReference) {
							JmlArrayReference arrayRef = (JmlArrayReference) expr;

							if (arrayRef.receiver instanceof NameReference) {
								Reference receiver = (Reference) arrayRef.receiver;

								// Array does not reference a field
								if (receiver.fieldBinding() == null) {
									throw new UnsupportedJMLExpression(arrayRef);
								}

								if (receiver.fieldBinding().equals(field.getDecl().binding)) {
									Expr index = parseExpression(arrayRef.position);
									assignedIndices.add(index);
								}
							}
						}
					}

					if (allIndicesAssigned) {
						// Do nothing. We can not add any assertions.
					} else if (!assignedIndices.isEmpty()) {
						// Only some indices are assigned.

						// index of the forall expression
						Expr forIndex = ctx.mkFreshConst("i", ctx.getIntSort());

						// i != index1, i != index2, ...
						Expr[] assignmentExprs = assignedIndices.stream()
								.map(index -> ctx.mkNot(ctx.mkEq(forIndex, index)))
								.toArray(length -> new Expr[length]);

						//(i != index1 & i != index2) => arr[i] == \old(arr[i])
						Expr forBody = ctx.mkImplies(
								ctx.mkAnd(assignmentExprs),
								ctx.mkEq(
										ctx.mkSelect(field.getAccessor().apply(getThisConst()), forIndex),
										ctx.mkSelect(field.getAccessor().apply(oldConst), forIndex)
								)
						);

						Expr forall = ctx.mkForall(new Expr[] {forIndex}, forBody, 1, null, null, null, null);
						result = ctx.mkAnd(result, forall);

					} else if (!allIndicesAssigned) {
						//if the field is not assigned then we add an equality expression.
						result = ctx.mkAnd(result, ctx.mkEq(
								field.getAccessor().apply(oldConst),
								field.getAccessor().apply(getThisConst()))
						);
					}

				} else {
					// Handle single assignments
					boolean isAssigned = false;

					for (Expression ref : references) {
						if (ref instanceof JmlSingleNameReference) {
							Binding b = ((JmlSingleNameReference) ref).binding;
							if (b.equals(field.getDecl().binding)) {
								// The binding is assigned. We cannot make any assertions on that field.
								isAssigned = true;
								break;
							}
						}
					}

					if (!isAssigned) {
						//if the field is not assigned then we add an equality expression.
						result = ctx.mkAnd(result, ctx.mkEq(
								field.getAccessor().apply(oldConst),
								field.getAccessor().apply(getThisConst()))
						);
					}
				}
			}
			return result;
		}

		throw new IllegalArgumentException(jmlAssignableClause.toString());
	}
}
