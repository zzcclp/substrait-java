/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.substrait.spark.expression

import io.substrait.spark.ToSubstraitType

import org.apache.spark.internal.Logging
import org.apache.spark.sql.catalyst.SQLConfHelper
import org.apache.spark.sql.catalyst.analysis.{AnsiTypeCoercion, TypeCoercion}
import org.apache.spark.sql.catalyst.expressions.{Expression, WindowExpression}
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression
import org.apache.spark.sql.types.DataType

import com.google.common.collect.{ArrayListMultimap, Multimap}
import io.substrait.`type`.Type
import io.substrait.expression.{EnumArg, Expression => SExpression, ExpressionCreator, FunctionArg}
import io.substrait.expression.Expression.FailureBehavior
import io.substrait.extension.SimpleExtension
import io.substrait.function.{ParameterizedType, ToTypeString}
import io.substrait.utils.Util

import java.{util => ju}

import scala.annotation.tailrec
import scala.collection.JavaConverters
import scala.collection.JavaConverters.collectionAsScalaIterableConverter

abstract class FunctionConverter[F <: SimpleExtension.Function, T](functions: Seq[F])
  extends Logging {

  protected val (signatures, substraitFuncKeyToSig) = init(functions)

  def generateBinding(
      sparkExp: Expression,
      function: F,
      arguments: Seq[FunctionArg],
      outputType: Type): T
  def getSigs: Seq[Sig]

  private def init(
      functions: Seq[F]): (ju.Map[Class[_], FunctionFinder[F, T]], Multimap[String, Sig]) = {
    val alm = ArrayListMultimap.create[String, F]()
    functions.foreach(f => alm.put(f.name().toLowerCase(ju.Locale.ROOT), f))

    val sparkExpressions = ArrayListMultimap.create[String, Sig]()
    getSigs.foreach(f => sparkExpressions.put(f.name, f))
    val matcherMap =
      new ju.IdentityHashMap[Class[_], FunctionFinder[F, T]]

    JavaConverters
      .asScalaSet(alm.keySet())
      .foreach(
        key => {
          val sigs = sparkExpressions.get(key)
          if (sigs == null) {
            logInfo("Dropping function due to no binding:" + key)
          } else {
            JavaConverters
              .asScalaBuffer(sigs)
              .foreach(
                sig => {
                  val implList = alm.get(key)
                  if (implList != null && !implList.isEmpty) {
                    matcherMap
                      .put(sig.expClass, createFinder(key, JavaConverters.asScalaBuffer(implList)))
                  }
                })
          }
        })
    val keyMap = ArrayListMultimap.create[String, Sig]

    alm.entries.asScala.foreach(
      entry =>
        sparkExpressions
          .get(entry.getKey)
          .asScala
          .foreach(keyMap.put(entry.getValue.key(), _)))

    (matcherMap, keyMap)
  }

  def getSparkExpressionFromSubstraitFunc(
      key: String,
      args: Seq[Expression]): Option[Expression] = {
    val candidates = substraitFuncKeyToSig.get(key).asScala.toList
    val sigs = if (candidates.length > 1) {
      // attempt to disambiguate with the key (if it's been set)
      val specific = candidates.filter {
        case SpecialSig(_, _, Some(sig), _) if sig == key => true
        case _ => false
      }
      if (specific.nonEmpty) {
        specific
      } else {
        // no matching signature, so select the generic one(s)
        candidates
      }
    } else {
      candidates
    }
    sigs.headOption.map(sig => sig.makeCall(args))
  }

  private def createFinder(name: String, functions: Seq[F]): FunctionFinder[F, T] = {
    new FunctionFinder[F, T](
      name,
      functions
        .flatMap(
          func =>
            if (func.requiredArguments().size() != func.args().size()) {
              Seq(
                func.key() -> func,
                SimpleExtension.Function.constructKey(name, func.requiredArguments()) -> func)
            } else {
              Seq(func.key() -> func)
            })
        .toMap,
      FunctionFinder.getSingularInputType(functions),
      parent = this
    )
  }
}

object FunctionFinder extends SQLConfHelper {

  /**
   * Returns the most general of a set of types (that is, one type to which they can all be cast),
   * or [[None]] if conversion is not possible. The result may be a new type that is less
   * restrictive than any of the input types, e.g. <code>leastRestrictive(INT, NUMERIC(3, 2))</code>
   * could be <code>NUMERIC(12, 2)</code>.
   *
   * @param types
   *   input types to be combined using union (not null, not empty)
   * @return
   *   canonical union type descriptor
   */
  def leastRestrictive(types: Seq[DataType]): Option[DataType] = {
    val typeCoercion = if (conf.ansiEnabled) {
      AnsiTypeCoercion
    } else {
      TypeCoercion
    }
    typeCoercion.findWiderCommonType(types)
  }

  /**
   * If some of the function variants for this function name have single, repeated argument type, we
   * will attempt to find matches using these patterns and least-restrictive casting.
   *
   * <p>If this exists, the function finder will attempt to find a least-restrictive match using
   * these.
   */
  def getSingularInputType[F <: SimpleExtension.Function](
      functions: Seq[F]): Option[SingularArgumentMatcher[F]] = {

    @tailrec
    def determineFirstType(
        first: ParameterizedType,
        index: Int,
        list: ju.List[SimpleExtension.Argument]): ParameterizedType =
      if (index >= list.size()) {
        first
      } else {
        list.get(index) match {
          case argument: SimpleExtension.ValueArgument =>
            val pt = argument.value()
            val first_or_pt = if (first == null) pt else first
            if (first == null || isMatch(first, pt)) {
              determineFirstType(first_or_pt, index + 1, list)
            } else {
              null
            }
          case _ => null
        }
      }

    val matchers = functions
      .map(f => (f, determineFirstType(null, 0, f.requiredArguments())))
      .filter(_._2 != null)
      .map(f => singular(f._1, f._2))

    matchers.size match {
      case 0 => None
      case 1 => Some(matchers.head)
      case _ => Some(chained(matchers))
    }
  }

  private def isMatch(
      inputType: ParameterizedType,
      parameterizedType: ParameterizedType): Boolean = {
    if (parameterizedType.isWildcard) {
      true
    } else {
      inputType.accept(new IgnoreNullableAndParameters(parameterizedType))
    }
  }

  private def isMatch(inputType: Type, parameterizedType: ParameterizedType): Boolean = {
    if (parameterizedType.isWildcard) {
      true
    } else {
      inputType.accept(new IgnoreNullableAndParameters(parameterizedType))
    }
  }

  def singular[F <: SimpleExtension.Function](
      function: F,
      t: ParameterizedType): SingularArgumentMatcher[F] =
    (inputType: Type, outputType: Type) => if (isMatch(inputType, t)) Some(function) else None

  def collectFirst[F <: SimpleExtension.Function](
      matchers: Seq[SingularArgumentMatcher[F]],
      inputType: Type,
      outputType: Type): Option[F] = {
    val iter = matchers.iterator
    while (iter.hasNext) {
      val s = iter.next()
      val result = s.apply(inputType, outputType)
      if (result.isDefined) {
        return result
      }
    }
    None
  }

  def chained[F <: SimpleExtension.Function](
      matchers: Seq[SingularArgumentMatcher[F]]): SingularArgumentMatcher[F] =
    (inputType: Type, outputType: Type) => collectFirst(matchers, inputType, outputType)
}

trait SingularArgumentMatcher[F <: SimpleExtension.Function] extends ((Type, Type) => Option[F])

class FunctionFinder[F <: SimpleExtension.Function, T](
    val name: String,
    val directMap: Map[String, F],
    val singularInputType: Option[SingularArgumentMatcher[F]],
    val parent: FunctionConverter[F, T]) {

  def attemptMatch(expression: Expression, operands: Seq[FunctionArg]): Option[T] = {
    val outputType = ToSubstraitType.apply(expression.dataType, expression.nullable)

    val opTypesStr = operands.map {
      case e: SExpression => e.getType.accept(ToTypeString.INSTANCE)
      case t: Type => t.accept(ToTypeString.INSTANCE)
      case _: EnumArg => "req"
    }

    val possibleKeys =
      Util.crossProduct(opTypesStr.map(s => Seq(s))).map(list => list.mkString("_"))

    val directMatchKey = possibleKeys
      .map(name + ":" + _)
      .find(k => directMap.contains(k))

    if (operands.isEmpty) {
      val variant = directMap(name + ":")
      // TODO validate the output type
      Option(parent.generateBinding(expression, variant, operands, outputType))
    } else if (directMatchKey.isDefined) {
      val variant = directMap(directMatchKey.get)
      // TODO validate the output type
      val funcArgs: Seq[FunctionArg] = operands
      Option(parent.generateBinding(expression, variant, funcArgs, outputType))
    } else if (singularInputType.isDefined) {
      val children = expression match {
        case agg: AggregateExpression => agg.aggregateFunction.children
        case win: WindowExpression => win.windowFunction.children
        case other => other.children
      }
      val types = children.map(_.dataType)
      val nullable = children.exists(e => e.nullable)
      FunctionFinder
        .leastRestrictive(types)
        .flatMap(
          leastRestrictive => {
            val leastRestrictiveSubstraitT =
              ToSubstraitType.apply(leastRestrictive, nullable = nullable)
            singularInputType
              .flatMap(f => f(leastRestrictiveSubstraitT, outputType))
              .map(
                declaration => {
                  val coercedArgs = coerceArguments(operands, leastRestrictiveSubstraitT)
                  // TODO validate the output type
                  val funcArgs: Seq[FunctionArg] = coercedArgs
                  parent.generateBinding(expression, declaration, funcArgs, outputType)
                })
          })
    } else {
      None
    }
  }

  /**
   * Coerced types according to an expected output type. Coercion is only done for type mismatches,
   * not for nullability or parameter mismatches.
   */
  private def coerceArguments(arguments: Seq[FunctionArg], t: Type): Seq[FunctionArg] = {
    arguments.map {
      case a: SExpression =>
        if (FunctionFinder.isMatch(t, a.getType)) {
          a
        } else {
          ExpressionCreator.cast(t, a, FailureBehavior.THROW_EXCEPTION)
        }
      case other => other
    }
  }
}
