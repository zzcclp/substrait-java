package io.substrait.isthmus;

import static io.substrait.isthmus.SqlConverterBase.EXTENSION_COLLECTION;
import static io.substrait.isthmus.SubstraitTypeSystem.YEAR_MONTH_INTERVAL;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.common.collect.ImmutableMap;
import io.substrait.expression.Expression;
import io.substrait.expression.Expression.IntervalDayLiteral;
import io.substrait.expression.Expression.IntervalYearLiteral;
import io.substrait.expression.Expression.Literal;
import io.substrait.expression.Expression.TimestampLiteral;
import io.substrait.expression.ExpressionCreator;
import io.substrait.isthmus.SubstraitRelNodeConverter.Context;
import io.substrait.isthmus.expression.ExpressionRexConverter;
import io.substrait.isthmus.expression.RexExpressionConverter;
import io.substrait.isthmus.expression.ScalarFunctionConverter;
import io.substrait.type.TypeCreator;
import io.substrait.util.DecimalUtil;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlIntervalQualifier;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.DateString;
import org.apache.calcite.util.TimeString;
import org.apache.calcite.util.TimestampString;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

public class CalciteLiteralTest extends CalciteObjs {

  private final ScalarFunctionConverter scalarFunctionConverter =
      new ScalarFunctionConverter(EXTENSION_COLLECTION.scalarFunctions(), type);

  private final ExpressionRexConverter expressionRexConverter =
      new ExpressionRexConverter(type, scalarFunctionConverter, null, TypeConverter.DEFAULT);

  private final RexExpressionConverter rexExpressionConverter = new RexExpressionConverter();

  @Test
  void nullLiteral() {
    bitest(
        ExpressionCreator.typedNull(TypeCreator.NULLABLE.varChar(10)),
        rex.makeNullLiteral(tN(SqlTypeName.VARCHAR, 10)));
  }

  @Test
  void tI8() {
    bitest(ExpressionCreator.i8(false, 4), c(4, SqlTypeName.TINYINT));
  }

  @Test
  void tI16() {
    bitest(ExpressionCreator.i16(false, 4), c(4, SqlTypeName.SMALLINT));
  }

  @Test
  void tI32() {
    bitest(ExpressionCreator.i32(false, 4), c(4, SqlTypeName.INTEGER));
  }

  @Test
  void tI64() {
    bitest(ExpressionCreator.i64(false, 1234L), c(1234L, SqlTypeName.BIGINT));
  }

  @Test
  void tFP32() {
    bitest(ExpressionCreator.fp32(false, 4.44F), c(4.44F, SqlTypeName.REAL));
  }

  @Test
  void tFP64() {
    bitest(ExpressionCreator.fp64(false, 4.45F), c(4.45F, SqlTypeName.DOUBLE));
  }

  @Test
  void tFloatFP64() {
    test(ExpressionCreator.fp64(false, 4.45F), c(4.45F, SqlTypeName.FLOAT));
  }

  @Test
  void tStr() {
    bitest(ExpressionCreator.string(false, "my test"), c("my test", SqlTypeName.VARCHAR));
  }

  @Test
  void tBinary() {
    byte[] val = "my test".getBytes(StandardCharsets.UTF_8);
    bitest(
        ExpressionCreator.binary(false, val),
        c(new org.apache.calcite.avatica.util.ByteString(val), SqlTypeName.VARBINARY));
  }

  @Test
  void tTime() {
    bitest(
        ExpressionCreator.time(false, (14L * 60 * 60 + 22 * 60 + 47) * 1000 * 1000),
        rex.makeTimeLiteral(new TimeString(14, 22, 47), 6));
  }

  @Test
  void tTimeWithMicroSecond() {
    long microSec = (14L * 60 * 60 + 22 * 60 + 47) * 1000 * 1000 + 123456;
    long seconds = TimeUnit.MICROSECONDS.toSeconds(microSec);
    int fracSecondsInNano =
        (int) (TimeUnit.MICROSECONDS.toNanos(microSec) - TimeUnit.SECONDS.toNanos(seconds));
    assertEquals(
        TimeString.fromMillisOfDay((int) TimeUnit.SECONDS.toMillis(seconds))
            .withNanos(fracSecondsInNano),
        new TimeString("14:22:47.123456"));

    bitest(
        ExpressionCreator.time(false, (14L * 60 * 60 + 22 * 60 + 47) * 1000 * 1000 + 123456),
        rex.makeTimeLiteral(new TimeString("14:22:47.123456"), 6));
  }

  @Test
  void tTimeWithNanoSecond() {
    assertEquals(
        rex.makeTimeLiteral(new TimeString("14:22:47.123456789"), 9),
        rex.makeTimeLiteral(new TimeString("14:22:47.123456"), 6));
  }

  @Test
  void tDate() {
    bitest(
        ExpressionCreator.date(false, (int) LocalDate.of(2002, 2, 14).toEpochDay()),
        rex.makeDateLiteral(new DateString(2002, 2, 14)));
  }

  @Test
  void tTimestamp() {
    TimestampLiteral ts = ExpressionCreator.timestamp(false, 2002, 2, 14, 16, 20, 47, 123);
    int nano = (int) TimeUnit.MICROSECONDS.toNanos(123);
    TimestampString tsx = new TimestampString(2002, 2, 14, 16, 20, 47).withNanos(nano);
    bitest(ts, rex.makeTimestampLiteral(tsx, 6));
  }

  @Test
  void tTimestampWithMilliMacroSeconds() {
    TimestampLiteral ts = ExpressionCreator.timestamp(false, 2002, 2, 14, 16, 20, 47, 123456);
    int nano = (int) TimeUnit.MICROSECONDS.toNanos(123456);
    TimestampString tsx = new TimestampString(2002, 2, 14, 16, 20, 47).withNanos(nano);
    bitest(ts, rex.makeTimestampLiteral(tsx, 6));
  }

  @Disabled("Not clear what the right literal mapping is.")
  @Test
  void tTimestampTZ() {
    // Calcite has TimestampWithTimeZoneString but it doesn't appear to be available as a literal or
    // data type.
    // (Doesn't exist in SqlTypeName.)
  }

  @Test
  void tIntervalYearMonth() {
    BigDecimal bd = new BigDecimal(3 * 12 + 5); // '3-5' year to month
    RexLiteral intervalYearMonth = rex.makeIntervalLiteral(bd, YEAR_MONTH_INTERVAL);
    IntervalYearLiteral intervalYearMonthExpr = ExpressionCreator.intervalYear(false, 3, 5);
    bitest(intervalYearMonthExpr, intervalYearMonth);
  }

  @Test
  void tIntervalYearMonthWithPrecision() {
    BigDecimal bd = new BigDecimal(123 * 12 + 5); // '123-5' year to month
    RexLiteral intervalYearMonth =
        rex.makeIntervalLiteral(
            bd,
            new SqlIntervalQualifier(
                org.apache.calcite.avatica.util.TimeUnit.YEAR,
                3,
                org.apache.calcite.avatica.util.TimeUnit.MONTH,
                -1,
                SqlParserPos.QUOTED_ZERO));
    IntervalYearLiteral intervalYearMonthExpr = ExpressionCreator.intervalYear(false, 123, 5);

    // rex --> expression
    assertEquals(intervalYearMonthExpr, intervalYearMonth.accept(rexExpressionConverter));

    // expression -> rex
    RexLiteral convertedRex =
        (RexLiteral) intervalYearMonthExpr.accept(expressionRexConverter, Context.newContext());

    // Compare value only. Ignore the precision in SqlIntervalQualifier (which is used to parse
    // input string).
    assertEquals(
        intervalYearMonth.getValueAs(BigDecimal.class).longValue(),
        convertedRex.getValueAs(BigDecimal.class).longValue());
  }

  @Test
  void tIntervalMillisecond() {
    // Calcite stores milliseconds since Epoch, so test only millisecond precision
    BigDecimal bd =
        new BigDecimal(
            TimeUnit.DAYS.toMillis(3)
                + TimeUnit.HOURS.toMillis(5)
                + TimeUnit.MINUTES.toMillis(7)
                + TimeUnit.SECONDS.toMillis(9)
                + 500); // '3-5:7:9.500' day to second (6)
    RexLiteral intervalDaySecond =
        rex.makeIntervalLiteral(
            bd,
            new SqlIntervalQualifier(
                org.apache.calcite.avatica.util.TimeUnit.DAY,
                -1,
                org.apache.calcite.avatica.util.TimeUnit.SECOND,
                3,
                SqlParserPos.ZERO));
    IntervalDayLiteral intervalDaySecondExpr =
        ExpressionCreator.intervalDay(false, 3, 5 * 3600 + 7 * 60 + 9, 500_000, 6);
    bitest(intervalDaySecondExpr, intervalDaySecond);
  }

  @Test
  void tIntervalDay() {
    // Calcite always uses milliseconds
    BigDecimal bd = new BigDecimal(TimeUnit.DAYS.toMillis(5));
    RexLiteral intervalDayLiteral =
        rex.makeIntervalLiteral(
            bd,
            new SqlIntervalQualifier(
                org.apache.calcite.avatica.util.TimeUnit.DAY, -1, null, -1, SqlParserPos.ZERO));
    IntervalDayLiteral intervalDayExpr = ExpressionCreator.intervalDay(false, 5, 0, 0, 6);

    // rex --> expression
    Expression convertedExpr = intervalDayLiteral.accept(rexExpressionConverter);
    assertEquals(intervalDayExpr, convertedExpr);

    // expression -> rex
    RexLiteral convertedRex =
        (RexLiteral) intervalDayExpr.accept(expressionRexConverter, Context.newContext());

    // Compare value only. Ignore the precision in SqlIntervalQualifier in comparison.
    assertEquals(
        intervalDayLiteral.getValueAs(BigDecimal.class), convertedRex.getValueAs(BigDecimal.class));
  }

  @Test
  void tIntervalYear() {
    BigDecimal bd = new BigDecimal(123 * 12); // '123' year(3)
    RexLiteral intervalYear =
        rex.makeIntervalLiteral(
            bd,
            new SqlIntervalQualifier(
                org.apache.calcite.avatica.util.TimeUnit.YEAR,
                3,
                null,
                -1,
                SqlParserPos.QUOTED_ZERO));
    IntervalYearLiteral intervalYearExpr = ExpressionCreator.intervalYear(false, 123, 0);
    // rex --> expression
    assertEquals(intervalYearExpr, intervalYear.accept(rexExpressionConverter));

    // expression -> rex
    RexLiteral convertedRex =
        (RexLiteral) intervalYearExpr.accept(expressionRexConverter, Context.newContext());

    // Compare value only. Ignore the precision in SqlIntervalQualifier in comparison.
    assertEquals(
        intervalYear.getValueAs(BigDecimal.class).longValue(),
        convertedRex.getValueAs(BigDecimal.class).longValue());
  }

  @Test
  void tIntervalMonth() {
    BigDecimal bd = new BigDecimal(123); // '123' month(3)
    RexLiteral intervalMonth =
        rex.makeIntervalLiteral(
            bd,
            new SqlIntervalQualifier(
                org.apache.calcite.avatica.util.TimeUnit.MONTH,
                3,
                null,
                -1,
                SqlParserPos.QUOTED_ZERO));
    IntervalYearLiteral intervalMonthExpr =
        ExpressionCreator.intervalYear(false, 123 / 12, 123 % 12);
    // rex --> expression
    assertEquals(intervalMonthExpr, intervalMonth.accept(rexExpressionConverter));

    // expression -> rex
    RexLiteral convertedRex =
        (RexLiteral) intervalMonthExpr.accept(expressionRexConverter, Context.newContext());

    // Compare value only. Ignore the precision in SqlIntervalQualifier in comparison.
    assertEquals(
        intervalMonth.getValueAs(BigDecimal.class).longValue(),
        convertedRex.getValueAs(BigDecimal.class).longValue());
  }

  @Test
  void tFixedChar() {
    bitest(ExpressionCreator.fixedChar(false, "hello "), c("hello ", SqlTypeName.CHAR));
  }

  @Test
  void tVarChar() {
    bitest(ExpressionCreator.varChar(false, "hello ", 10), c("hello ", SqlTypeName.VARCHAR, 10));
  }

  @Test
  void tDecimalLiteral() {
    List<BigDecimal> decimalList =
        List.of(
            new BigDecimal("-123.457890"),
            new BigDecimal("123.457890"),
            new BigDecimal("123.450000"),
            new BigDecimal("-123.450000"));
    for (BigDecimal bd : decimalList) {
      bitest(ExpressionCreator.decimal(false, bd, 32, 6), c(bd, SqlTypeName.DECIMAL, 32, 6));
    }
  }

  @Test
  void tDecimalLiteral2() {
    List<BigDecimal> decimalList =
        List.of(
            new BigDecimal("-99.123456789123456789123456789123456789"), // scale = 36, precision =38
            new BigDecimal("99.123456789123456789123456789123456789") // scale = 36, precision = 38
            );
    for (BigDecimal bd : decimalList) {
      bitest(ExpressionCreator.decimal(false, bd, 38, 36), c(bd, SqlTypeName.DECIMAL, 38, 36));
    }
  }

  @Test
  void tDecimalUtil() {
    long[] values =
        new long[] {Long.MIN_VALUE, Integer.MIN_VALUE, 0, Integer.MAX_VALUE, Long.MAX_VALUE};
    for (long value : values) {
      BigDecimal bd = BigDecimal.valueOf(value);
      byte[] encoded = DecimalUtil.encodeDecimalIntoBytes(bd, 0, 16);
      BigDecimal bd2 = DecimalUtil.getBigDecimalFromBytes(encoded, 0, 16);
      System.out.println(bd2);
      assertEquals(bd, bd2);
    }
  }

  @Test
  void tMap() {
    ImmutableMap<Literal, Literal> ss =
        ImmutableMap.of(
            ExpressionCreator.string(false, "foo"),
            ExpressionCreator.i32(false, 4),
            ExpressionCreator.string(false, "bar"),
            ExpressionCreator.i32(false, -1));
    RexNode calcite =
        rex.makeLiteral(
            ImmutableMap.of("foo", 4, "bar", -1),
            type.createMapType(t(SqlTypeName.VARCHAR), t(SqlTypeName.INTEGER)),
            true,
            false);
    bitest(ExpressionCreator.map(false, ss), calcite);
  }

  @Test
  void tList() {
    bitest(
        ExpressionCreator.list(
            false, ExpressionCreator.i32(false, 4), ExpressionCreator.i32(false, -1)),
        rex.makeLiteral(
            Arrays.asList(4, -1), type.createArrayType(t(SqlTypeName.INTEGER), -1), false, false));
  }

  @Test
  void tStruct() {
    test(
        ExpressionCreator.struct(
            false, ExpressionCreator.i32(false, 4), ExpressionCreator.i32(false, -1)),
        rex.makeLiteral(
            Arrays.asList(4, -1),
            type.createStructType(
                Arrays.asList(t(SqlTypeName.INTEGER), t(SqlTypeName.INTEGER)),
                Arrays.asList("c1", "c2")),
            false,
            false));
  }

  @Test
  void tFixedBinary() {
    byte[] val = "my test".getBytes(StandardCharsets.UTF_8);
    bitest(
        ExpressionCreator.fixedBinary(false, val),
        c(new org.apache.calcite.avatica.util.ByteString(val), SqlTypeName.BINARY));
  }

  public void test(Expression expression, RexNode rex) {
    assertEquals(expression, rex.accept(new RexExpressionConverter()));
  }

  // bi-directional test : 1) rex -> substrait,  substrait -> rex2.  Compare rex == rex2
  public void bitest(Expression expression, RexNode rex) {
    assertEquals(expression, rex.accept(rexExpressionConverter));
    RexNode convertedRex = expression.accept(expressionRexConverter, Context.newContext());
    assertEquals(rex, convertedRex);
  }
}
