/**
 * Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not
 * use this file except in compliance with the License. A copy of the License
 * is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */

package org.apache.spark.sql

import com.amazon.deequ.analyzers.DataTypeHistogram
import org.apache.spark.sql.expressions.{MutableAggregationBuffer, UserDefinedAggregateFunction}
import org.apache.spark.sql.types._

import scala.util.matching.Regex


private[sql] class StatefulDataType extends UserDefinedAggregateFunction {

  val SIZE_IN_BYTES = 48

  val NULL_POS = 0
  val FRACTIONAL_POS = 1
  val INTEGRAL_POS = 2
  val BOOLEAN_POS = 3
  val STRING_POS = 4
  val DATE_POS = 5

  val FRACTIONAL: Regex = """^(-|\+)? ?\d*\.\d*$""".r
  val INTEGRAL: Regex = """^(-|\+)? ?\d*$""".r
  val BOOLEAN: Regex = """^(true|false)$""".r
  val DATE: Regex = """^((2000|2400|2800|(19|2[0-9](0?[48]|[2468][048]|[13579][26])))-0?2-29)$
      ~|^(((19|2[0-9])[0-9]{2})-0?2-(0?[1-9]|1[0-9]|2[0-8]))$
      ~|^(((19|2[0-9])[0-9]{2})-(0?[13578]|10|12)-(0?[1-9]|[12][0-9]|3[01]))$
      ~|^(((19|2[0-9])[0-9]{2})-(0?[469]|11)-(0?[1-9]|[12][0-9]|30))$
      ~|^9999-12-31$""".stripMargin('~').replaceAll("\n", "").r

  override def inputSchema: StructType = StructType(StructField("value", StringType) :: Nil)

  override def bufferSchema: StructType = StructType(StructField("null", LongType) ::
    StructField("fractional", LongType) :: StructField("integral", LongType) ::
    StructField("boolean", LongType) :: StructField("string", LongType) ::
    StructField("date", LongType) :: Nil)

  override def dataType: types.DataType = BinaryType

  override def deterministic: Boolean = true

  override def initialize(buffer: MutableAggregationBuffer): Unit = {
    buffer(NULL_POS) = 0L
    buffer(FRACTIONAL_POS) = 0L
    buffer(INTEGRAL_POS) = 0L
    buffer(BOOLEAN_POS) = 0L
    buffer(STRING_POS) = 0L
    buffer(DATE_POS) = 0L
  }

  override def update(buffer: MutableAggregationBuffer, input: Row): Unit = {
    if (input.isNullAt(0)) {
      buffer(NULL_POS) = buffer.getLong(NULL_POS) + 1L
    } else {
      input.getString(0) match {
        case FRACTIONAL(_) => buffer(FRACTIONAL_POS) = buffer.getLong(FRACTIONAL_POS) + 1L
        case INTEGRAL(_) => buffer(INTEGRAL_POS) = buffer.getLong(INTEGRAL_POS) + 1L
        case BOOLEAN(_) => buffer(BOOLEAN_POS) = buffer.getLong(BOOLEAN_POS) + 1L
        case DATE(_*) => buffer(DATE_POS) = buffer.getLong(DATE_POS) + 1L
        case _ => buffer(STRING_POS) = buffer.getLong(STRING_POS) + 1L
      }
    }
  }

  override def merge(buffer1: MutableAggregationBuffer, buffer2: Row): Unit = {
    buffer1(NULL_POS) = buffer1.getLong(NULL_POS) + buffer2.getLong(NULL_POS)
    buffer1(FRACTIONAL_POS) = buffer1.getLong(FRACTIONAL_POS) + buffer2.getLong(FRACTIONAL_POS)
    buffer1(INTEGRAL_POS) = buffer1.getLong(INTEGRAL_POS) + buffer2.getLong(INTEGRAL_POS)
    buffer1(BOOLEAN_POS) = buffer1.getLong(BOOLEAN_POS) + buffer2.getLong(BOOLEAN_POS)
    buffer1(STRING_POS) = buffer1.getLong(STRING_POS) + buffer2.getLong(STRING_POS)
    buffer1(DATE_POS) = buffer1.getLong(DATE_POS) + buffer2.getLong(DATE_POS)
  }

  override def evaluate(buffer: Row): Any = {
    DataTypeHistogram.toBytes(buffer.getLong(NULL_POS), buffer.getLong(FRACTIONAL_POS),
      buffer.getLong(INTEGRAL_POS), buffer.getLong(BOOLEAN_POS), buffer.getLong(STRING_POS),
      buffer.getLong(DATE_POS))
  }
}
