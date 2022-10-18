package org.apache.spark.substrait.expression

import io.substrait.function.{ParameterizedType, ParameterizedTypeVisitor}
import io.substrait.`type`.Type

class IgnoreNullableAndParameters(val typeToMatch: ParameterizedType)
  extends ParameterizedTypeVisitor[Boolean, RuntimeException] {

  override def visit(`type`: Type.Bool): Boolean = typeToMatch.isInstanceOf[Type.Bool]

  override def visit(`type`: Type.I8): Boolean = typeToMatch.isInstanceOf[Type.I8]

  override def visit(`type`: Type.I16): Boolean = typeToMatch.isInstanceOf[Type.I16]

  override def visit(`type`: Type.I32): Boolean = typeToMatch.isInstanceOf[Type.I32]

  override def visit(`type`: Type.I64): Boolean = typeToMatch.isInstanceOf[Type.I64]

  override def visit(`type`: Type.FP32): Boolean = typeToMatch.isInstanceOf[Type.FP32]

  override def visit(`type`: Type.FP64): Boolean = typeToMatch.isInstanceOf[Type.FP64]

  override def visit(`type`: Type.Str): Boolean = typeToMatch.isInstanceOf[Type.Str]

  override def visit(`type`: Type.Binary): Boolean = typeToMatch.isInstanceOf[Type.Binary]

  override def visit(`type`: Type.Date): Boolean = typeToMatch.isInstanceOf[Type.Date]

  override def visit(`type`: Type.Time): Boolean = typeToMatch.isInstanceOf[Type.Time]

  override def visit(`type`: Type.TimestampTZ): Boolean = typeToMatch.isInstanceOf[Type.TimestampTZ]

  override def visit(`type`: Type.Timestamp): Boolean = typeToMatch.isInstanceOf[Type.Timestamp]

  override def visit(`type`: Type.IntervalYear): Boolean = typeToMatch.isInstanceOf[Type.IntervalYear]

  override def visit(`type`: Type.IntervalDay): Boolean = typeToMatch.isInstanceOf[Type.IntervalDay]

  override def visit(`type`: Type.UUID): Boolean = typeToMatch.isInstanceOf[Type.UUID]

  override def visit(`type`: Type.FixedChar): Boolean =
    typeToMatch.isInstanceOf[Type.FixedChar] || typeToMatch.isInstanceOf[ParameterizedType.FixedChar]

  override def visit(`type`: Type.VarChar): Boolean =
    typeToMatch.isInstanceOf[Type.VarChar] || typeToMatch.isInstanceOf[ParameterizedType.VarChar]

  override def visit(`type`: Type.FixedBinary): Boolean =
    typeToMatch.isInstanceOf[Type.FixedBinary] || typeToMatch.isInstanceOf[ParameterizedType.FixedBinary]

  override def visit(`type`: Type.Decimal): Boolean =
    typeToMatch.isInstanceOf[Type.Decimal] || typeToMatch.isInstanceOf[ParameterizedType.Decimal]

  override def visit(`type`: Type.Struct): Boolean =
    typeToMatch.isInstanceOf[Type.Struct] || typeToMatch.isInstanceOf[ParameterizedType.Struct]

  override def visit(`type`: Type.ListType): Boolean =
    typeToMatch.isInstanceOf[Type.ListType] || typeToMatch.isInstanceOf[ParameterizedType.ListType]

  override def visit(`type`: Type.Map): Boolean =
    typeToMatch.isInstanceOf[Type.Map] || typeToMatch.isInstanceOf[ParameterizedType.Map]

  @throws[RuntimeException]
  override def visit(expr: ParameterizedType.FixedChar): Boolean =
    typeToMatch.isInstanceOf[Type.FixedChar] || typeToMatch.isInstanceOf[ParameterizedType.FixedChar]

  @throws[RuntimeException]
  override def visit(expr: ParameterizedType.VarChar): Boolean =
    typeToMatch.isInstanceOf[Type.VarChar] || typeToMatch.isInstanceOf[ParameterizedType.VarChar]

  @throws[RuntimeException]
  override def visit(expr: ParameterizedType.FixedBinary): Boolean =
    typeToMatch.isInstanceOf[Type.FixedBinary] || typeToMatch.isInstanceOf[ParameterizedType.FixedBinary]

  @throws[RuntimeException]
  override def visit(expr: ParameterizedType.Decimal): Boolean =
    typeToMatch.isInstanceOf[Type.Decimal] || typeToMatch.isInstanceOf[ParameterizedType.Decimal]

  @throws[RuntimeException]
  override def visit(expr: ParameterizedType.Struct): Boolean =
    typeToMatch.isInstanceOf[Type.Struct] || typeToMatch.isInstanceOf[ParameterizedType.Struct]

  @throws[RuntimeException]
  override def visit(expr: ParameterizedType.ListType): Boolean =
    typeToMatch.isInstanceOf[Type.ListType] || typeToMatch.isInstanceOf[ParameterizedType.ListType]

  @throws[RuntimeException]
  override def visit(expr: ParameterizedType.Map): Boolean =
    typeToMatch.isInstanceOf[Type.Map] || typeToMatch.isInstanceOf[ParameterizedType.Map]

  @throws[RuntimeException]
  override def visit(stringLiteral: ParameterizedType.StringLiteral) = false
}
