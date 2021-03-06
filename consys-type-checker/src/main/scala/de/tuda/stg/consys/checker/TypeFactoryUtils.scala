package de.tuda.stg.consys.checker

import javax.lang.model.element.AnnotationMirror
import org.checkerframework.framework.`type`.AnnotatedTypeFactory
import org.checkerframework.framework.`type`.AnnotatedTypeMirror.AnnotatedDeclaredType
import org.checkerframework.javacutil.{AnnotationUtils, TypesUtils}

import javax.lang.model.`type`.DeclaredType

/**
	* Created on 06.03.19.
	*
	* @author Mirko Köhler
	*/
object TypeFactoryUtils {
	/*
		Annotation definitions
	 */
	def localAnnotation(implicit atypeFactory : AnnotatedTypeFactory) : AnnotationMirror = {
		AnnotationUtils.getAnnotationByName(atypeFactory.getQualifierHierarchy.getBottomAnnotations,"de.tuda.stg.consys.checker.qual.Local")
	}

	def inconsistentAnnotation(implicit atypeFactory : AnnotatedTypeFactory) : AnnotationMirror =
		AnnotationUtils.getAnnotationByName(atypeFactory.getQualifierHierarchy.getTopAnnotations, "de.tuda.stg.consys.checker.qual.Inconsistent")

	val checkerPackageName = s"de.tuda.stg.consys.checker"
	val japiPackageName = s"de.tuda.stg.consys.japi.next"
	def getQualifiedName(adt: AnnotatedDeclaredType): String = TypesUtils.getQualifiedName(adt.getUnderlyingType).toString
	def getQualifiedName(dt: DeclaredType): String = TypesUtils.getQualifiedName(dt).toString
}
