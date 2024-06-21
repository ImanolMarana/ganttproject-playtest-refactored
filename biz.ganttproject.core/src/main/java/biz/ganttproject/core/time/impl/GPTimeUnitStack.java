/*
 * Created on 08.11.2004
 */
package biz.ganttproject.core.time.impl;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;

import biz.ganttproject.core.time.TimeDuration;
import biz.ganttproject.core.time.TimeDurationImpl;
import biz.ganttproject.core.time.TimeUnit;
import biz.ganttproject.core.time.TimeUnitGraph;
import biz.ganttproject.core.time.TimeUnitPair;
import biz.ganttproject.core.time.TimeUnitStack;


/**
 * @author bard
 */
public class GPTimeUnitStack implements TimeUnitStack {
  private static TimeUnitGraph ourGraph = new TimeUnitGraph();

  private static final TimeUnit HOUR = ourGraph.createAtomTimeUnit("hour");
  public static final TimeUnit DAY;

  public static final TimeUnit WEEK;

  public static final TimeUnit MONTH;

  public static final TimeUnit QUARTER;

  public static final TimeUnit YEAR;

  private final TimeUnitPair[] myPairs;

  static {
    TimeUnit atom = ourGraph.createAtomTimeUnit("atom");
    DAY = ourGraph.createDateFrameableTimeUnit("day", atom, 1, new FramerImpl(Calendar.DATE));
    MONTH = ourGraph.createTimeUnitFunctionOfDate("month", DAY, new FramerImpl(Calendar.MONTH));
    WEEK = ourGraph.createDateFrameableTimeUnit("week", DAY, 7, new WeekFramerImpl());
    QUARTER = ourGraph.createTimeUnitFunctionOfDate("quarter", MONTH, new FramerImpl(Calendar.MONTH));
    YEAR = ourGraph.createTimeUnitFunctionOfDate("year", DAY, new FramerImpl(Calendar.YEAR));
  }

  public GPTimeUnitStack() {
    myPairs = new TimeUnitPair[] { new TimeUnitPair(WEEK, DAY, this, 65), new TimeUnitPair(WEEK, DAY, this, 55),
        new TimeUnitPair(MONTH, DAY, this, 44), new TimeUnitPair(MONTH, DAY, this, 34),
        new TimeUnitPair(MONTH, WEEK, this, 24), new TimeUnitPair(MONTH, WEEK, this, 21),
        new TimeUnitPair(YEAR, WEEK, this, 13), new TimeUnitPair(YEAR, WEEK, this, 8),
        new TimeUnitPair(YEAR, MONTH, this, 5), new TimeUnitPair(YEAR, MONTH, this, 3),
    /*
     * The last pair is reused for the next steps, so it is needed only once.
     */
    /* new TimeUnitPair(YEAR, QUARTER, this, 1) */};
  }

  @Override
  public String getName() {
    return "default";
  }

  @Override
  public TimeUnit getDefaultTimeUnit() {
    return DAY;
  }

  @Override
  public TimeUnitPair[] getTimeUnitPairs() {
    return myPairs;
  }

  @Override
  public DateFormat[] getDateFormats() {
    DateFormat[] result;
    if (HOUR.isConstructedFrom(getDefaultTimeUnit())) {
      result = new DateFormat[] { DateFormat.getDateInstance(DateFormat.MEDIUM),
          DateFormat.getDateInstance(DateFormat.MEDIUM), DateFormat.getDateInstance(DateFormat.SHORT), };
    } else {
      result = new DateFormat[] { DateFormat.getDateInstance(DateFormat.LONG),
          DateFormat.getDateInstance(DateFormat.MEDIUM), DateFormat.getDateInstance(DateFormat.SHORT), };
    }
    return result;
  }

  @Override
  public DateFormat getTimeFormat() {
    if (HOUR.isConstructedFrom(getDefaultTimeUnit())) {
      return DateFormat.getTimeInstance(DateFormat.SHORT);
    }
    return null;
  }

  @Override
  public TimeUnit findTimeUnit(String code) {
    assert code != null;
    code = code.trim();
    if (isHour(code)) {
      return HOUR;
    }
    if (isDay(code)) {
      return DAY;
    }
    if (isWeek(code)) {
      return WEEK;
    }
    return null;
  }

  private boolean isWeek(String code) {
    return "w".equalsIgnoreCase(code);
  }

  private boolean isDay(String code) {
    return "d".equalsIgnoreCase(code);
  }

  private boolean isHour(String code) {
    return "h".equalsIgnoreCase(code);
  }

  @Override
  public String encode(TimeUnit timeUnit) {
    if (timeUnit == HOUR) {
      return "h";
    }
    if (timeUnit == DAY) {
      return "d";
    }
    if (timeUnit == WEEK) {
      return "w";
    }
    throw new IllegalArgumentException();
  }

  @Override
  public TimeDuration createDuration(TimeUnit timeUnit, int count) {
    return createLength(timeUnit, count);
  }

  @Override
  public TimeDuration createDuration(TimeUnit timeUnit, Date startDate, Date endDate) {
    TimeDuration result;
    int sign = 1;
    if (endDate.before(startDate)) {
      sign = -1;
      Date temp = endDate;
      endDate = startDate;
      startDate = temp;
    }
    int unitCount = 0;
    for (; startDate.before(endDate); unitCount++) {
      startDate = timeUnit.adjustRight(startDate);
    }
    result = new TimeDurationImpl(timeUnit, unitCount * sign);
    return result;
  }

  @Override
  public TimeDuration parseDuration(String lengthAsString) throws ParseException {
    if (lengthAsString == null) {
      throw new ParseException("Input string cannot be null", 0);
    }
    return parseDuration(lengthAsString, 0, new StringBuffer(), null, null);
  }
  
  private TimeDuration parseDuration(String lengthAsString, int i, StringBuffer valueBuffer, Integer currentValue,
      TimeDuration currentLength) throws ParseException {
    lengthAsString += " ";
    for (; i < lengthAsString.length(); i++) {
      char nextChar = lengthAsString.charAt(i);
      if (Character.isDigit(nextChar)) {
        valueBuffer, currentValue = handleDigit(lengthAsString, i, valueBuffer, currentValue);
      } else if (Character.isWhitespace(nextChar)) {
        valueBuffer, currentValue, currentLength = handleWhitespace(lengthAsString, i, valueBuffer,
            currentValue, currentLength);
      } else {
        valueBuffer, currentValue = handleNonDigitWhitespace(lengthAsString, i, valueBuffer, currentValue);
      }
    }
    return handleEndParsing(valueBuffer, currentValue, currentLength);
  }
  
  private TimeDuration handleEndParsing(StringBuffer valueBuffer, Integer currentValue, TimeDuration currentLength) {
    if (currentValue != null) {
      currentValue = Integer.valueOf(valueBuffer.toString());
      TimeUnit dayUnit = findTimeUnit("d");
      currentLength = createLength(dayUnit, currentValue.floatValue());
    }
    return currentLength;
  }

  private StringBuffer[] handleDigit(String lengthAsString, int i, StringBuffer valueBuffer, Integer currentValue) throws ParseException {
    switch (currentValue != null ? 1 : 0) {
      case 0:
        if (currentValue != null) {
          throw new ParseException(lengthAsString, i);
        }
        currentValue = 0;
        valueBuffer.setLength(0);
      case 1:
        valueBuffer.append(lengthAsString.charAt(i));
        break;
      case 2:
        TimeUnit timeUnit = findTimeUnit(valueBuffer.toString());
        if (timeUnit == null) {
          throw new ParseException(lengthAsString, i);
        }
        assert currentValue != null;
        TimeDuration localResult = createLength(timeUnit, currentValue.floatValue());
        
        currentValue = 0;
        valueBuffer.setLength(0);
        valueBuffer.append(lengthAsString.charAt(i));
        break;
    }
    return new StringBuffer[] {valueBuffer, valueBuffer};
  }
  
  private Object[] handleWhitespace(String lengthAsString, int i, StringBuffer valueBuffer, Integer currentValue,
      TimeDuration currentLength) throws ParseException {
    switch (currentValue != null ? 1 : 0) {
      case 0:
        break;
      case 1:
        currentValue = Integer.valueOf(valueBuffer.toString());
        break;
      case 2:
        TimeUnit timeUnit = findTimeUnit(valueBuffer.toString());
        if (timeUnit == null) {
          throw new ParseException(lengthAsString, i);
        }
        assert currentValue != null;
        TimeDuration localResult = createLength(timeUnit, currentValue.floatValue());
        if (currentLength == null) {
          currentLength = localResult;
        } else {
          if (currentLength.getTimeUnit().isConstructedFrom(timeUnit)) {
            float recalculatedLength = currentLength.getLength(timeUnit);
            currentLength = createLength(timeUnit, localResult.getValue() + recalculatedLength);
          } else {
            throw new ParseException(lengthAsString, i);
          }
        }
        currentValue = null;
        break;
    }
    return new Object[] {valueBuffer, currentValue, currentLength};
  }
  
  private StringBuffer[] handleNonDigitWhitespace(String lengthAsString, int i, StringBuffer valueBuffer,
      Integer currentValue) throws ParseException {
    switch (currentValue != null ? 1 : 0) {
      case 1:
        currentValue = Integer.valueOf(valueBuffer.toString());
      case 0:
        if (currentValue == null) {
          throw new ParseException(lengthAsString, i);
        }
        valueBuffer.setLength(0);
      case 2:
        valueBuffer.append(lengthAsString.charAt(i));
        break;
    }
    return new StringBuffer[] {valueBuffer, valueBuffer};
  }

//Refactoring end
            if (timeUnit == null) {
              throw new ParseException(lengthAsString, i);
            }
            assert currentValue != null;
            TimeDuration localResult = createLength(timeUnit, currentValue.floatValue());
            if (currentLength == null) {
              currentLength = localResult;
            } else {
              if (currentLength.getTimeUnit().isConstructedFrom(timeUnit)) {
                float recalculatedLength = currentLength.getLength(timeUnit);
                currentLength = createLength(timeUnit, localResult.getValue() + recalculatedLength);
              } else {
                throw new ParseException(lengthAsString, i);
              }
            }
            state = 0;
            currentValue = null;
            break;
          }
        } else {
          switch (state) {
          case 1:
            currentValue = Integer.valueOf(valueBuffer.toString());
          case 0:
            if (currentValue == null) {
              throw new ParseException(lengthAsString, i);
            }
            state = 2;
            valueBuffer.setLength(0);
          case 2:
            valueBuffer.append(nextChar);
            break;
          }
        }
      }
      if (currentValue != null) {
        currentValue = Integer.valueOf(valueBuffer.toString());
        TimeUnit dayUnit = findTimeUnit("d");
        currentLength = createLength(dayUnit, currentValue.floatValue());
      }
      return currentLength;
    }
  
  public static TimeDuration createLength(TimeUnit unit, float length) {
    return new TimeDurationImpl(unit, length);
  }

}
