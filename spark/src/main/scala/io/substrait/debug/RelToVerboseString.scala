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
package io.substrait.debug

import io.substrait.spark.DefaultRelVisitor

import io.substrait.relation._
import io.substrait.util.EmptyVisitationContext

import scala.collection.mutable

class RelToVerboseString(addSuffix: Boolean) extends DefaultRelVisitor[String] {

  private val expressionStringConverter = new ExpressionToString

  private def stringBuilder(rel: Rel, remapLength: Int): mutable.StringBuilder = {
    val nodeName = rel.getClass.getSimpleName.replaceAll("Immutable", "")
    val builder: mutable.StringBuilder = new mutable.StringBuilder(s"$nodeName[")
    rel.getRemap.ifPresent(remap => builder.append("remap=").append(remap))
    if (builder.length > remapLength) builder.append(", ")
    builder
  }

  private def withBuilder(rel: Rel, remapLength: Int)(f: mutable.StringBuilder => Unit): String = {
    val builder = stringBuilder(rel, remapLength)
    f(builder)
    builder.append("]").toString
  }

  def apply(rel: Rel, maxFields: Int): String = {
    rel.accept(this, EmptyVisitationContext.INSTANCE)
  }

  override def visit(fetch: Fetch, context: EmptyVisitationContext): String = {
    withBuilder(fetch, 7)(
      builder => {
        builder.append("offset=").append(fetch.getOffset)
        fetch.getCount.ifPresent(
          count => {
            builder.append(", ")
            builder.append("count=").append(count)
          })
      })
  }
  override def visit(sort: Sort, context: EmptyVisitationContext): String = {
    withBuilder(sort, 5)(
      builder => {
        builder.append("sortFields=").append(sort.getSortFields)
      })
  }

  override def visit(join: Join, context: EmptyVisitationContext): String = {
    withBuilder(join, 5)(
      builder => {
        join.getCondition.ifPresent(
          condition => {
            builder.append("condition=").append(condition)
            builder.append(", ")
          })

        join.getPostJoinFilter.ifPresent(
          postJoinFilter => {
            builder.append("postJoinFilter=").append(postJoinFilter)
            builder.append(", ")
          })
        builder.append("joinType=").append(join.getJoinType)
      })
  }

  override def visit(filter: Filter, context: EmptyVisitationContext): String = {
    withBuilder(filter, 7)(
      builder => {
        builder.append(filter.getCondition.accept(expressionStringConverter, context))
      })
  }

  def fillReadRel(read: AbstractReadRel, builder: mutable.StringBuilder): Unit = {
    builder.append("initialSchema=").append(read.getInitialSchema)
    read.getFilter.ifPresent(
      filter => {
        builder.append(", ")
        builder.append("filter=").append(filter)
      })
    read.getCommonExtension.ifPresent(
      commonExtension => {
        builder.append(", ")
        builder.append("commonExtension=").append(commonExtension)
      })
  }

  override def visit(namedScan: NamedScan, context: EmptyVisitationContext): String = {
    withBuilder(namedScan, 10)(
      builder => {
        fillReadRel(namedScan, builder)
        builder.append(", ")
        builder.append("names=").append(namedScan.getNames)

        namedScan.getExtension.ifPresent(
          extension => {
            builder.append(", ")
            builder.append("extension=").append(extension)
          })
      })
  }

  override def visit(
      virtualTableScan: VirtualTableScan,
      context: EmptyVisitationContext): String = {
    withBuilder(virtualTableScan, 10)(
      builder => {
        fillReadRel(virtualTableScan, builder)
        builder.append(", ")
        builder.append("rows=").append(virtualTableScan.getRows)

        virtualTableScan.getExtension.ifPresent(
          extension => {
            builder.append(", ")
            builder.append("extension=").append(extension)
          })
      })
  }

  override def visit(emptyScan: EmptyScan, context: EmptyVisitationContext): String = {
    withBuilder(emptyScan, 10)(
      builder => {
        fillReadRel(emptyScan, builder)
      })
  }

  override def visit(project: Project, context: EmptyVisitationContext): String = {
    withBuilder(project, 8)(
      builder => {
        builder
          .append("expressions=")
          .append(project.getExpressions)
      })
  }

  override def visit(expand: Expand, context: EmptyVisitationContext): String = {
    withBuilder(expand, 8)(
      builder => {
        builder
          .append("fields=")
          .append(expand.getFields)
      })
  }

  override def visit(aggregate: Aggregate, context: EmptyVisitationContext): String = {
    withBuilder(aggregate, 10)(
      builder => {
        builder
          .append("groupings=")
          .append(aggregate.getGroupings)
          .append(", ")
          .append("measures=")
          .append(aggregate.getMeasures)
      })
  }

  override def visit(window: ConsistentPartitionWindow, context: EmptyVisitationContext): String = {
    withBuilder(window, 10)(
      builder => {
        builder
          .append("functions=")
          .append(window.getWindowFunctions)
          .append("partitions=")
          .append(window.getPartitionExpressions)
          .append("sorts=")
          .append(window.getSorts)
      })
  }

  override def visit(set: Set, context: EmptyVisitationContext): String = {
    withBuilder(set, 8)(
      builder => {
        builder
          .append("operation=")
          .append(set.getSetOp)
      })
  }

  override def visit(cross: Cross, context: EmptyVisitationContext): String = {
    withBuilder(cross, 10)(
      builder => {
        builder
          .append("left=")
          .append(cross.getLeft)
          .append("right=")
          .append(cross.getRight)
      })
  }

  override def visit(localFiles: LocalFiles, context: EmptyVisitationContext): String = {
    withBuilder(localFiles, 10)(
      builder => {
        builder
          .append("items=")
          .append(localFiles.getItems)
      })
  }
}

object RelToVerboseString {
  val verboseStringWithSuffix = new RelToVerboseString(true)
  val verboseString = new RelToVerboseString(false)
}
