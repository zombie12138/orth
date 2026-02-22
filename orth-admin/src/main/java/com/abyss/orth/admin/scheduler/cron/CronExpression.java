package com.abyss.orth.admin.scheduler.cron;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TreeSet;

/**
 * CRON Expression Parser and Evaluator
 *
 * <p>Provides a parser and evaluator for unix-like cron expressions. Cron expressions enable
 * complex time specifications such as "At 8:00am every Monday through Friday" or "At 1:30am every
 * last Friday of the month".
 *
 * <p>Cron expressions are comprised of 6 required fields and one optional field separated by white
 * space. The fields respectively are described as follows:
 *
 * <table>
 * <caption>Examples of cron expressions and their meanings.</caption>
 * <tr>
 * <th>Field Name</th>
 * <th>&nbsp;</th>
 * <th>Allowed Values</th>
 * <th>&nbsp;</th>
 * <th>Allowed Special Characters</th>
 * </tr>
 * <tr>
 * <td><code>Seconds</code></td>
 * <td>&nbsp;</td>
 * <td><code>0-59</code></td>
 * <td>&nbsp;</td>
 * <td><code>, - * /</code></td>
 * </tr>
 * <tr>
 * <td><code>Minutes</code></td>
 * <td>&nbsp;</td>
 * <td><code>0-59</code></td>
 * <td>&nbsp;</td>
 * <td><code>, - * /</code></td>
 * </tr>
 * <tr>
 * <td><code>Hours</code></td>
 * <td>&nbsp;</td>
 * <td><code>0-23</code></td>
 * <td>&nbsp;</td>
 * <td><code>, - * /</code></td>
 * </tr>
 * <tr>
 * <td><code>Day-of-month</code></td>
 * <td>&nbsp;</td>
 * <td><code>1-31</code></td>
 * <td>&nbsp;</td>
 * <td><code>, - * ? / L W</code></td>
 * </tr>
 * <tr>
 * <td><code>Month</code></td>
 * <td>&nbsp;</td>
 * <td><code>0-11 or JAN-DEC</code></td>
 * <td>&nbsp;</td>
 * <td><code>, - * /</code></td>
 * </tr>
 * <tr>
 * <td><code>Day-of-Week</code></td>
 * <td>&nbsp;</td>
 * <td><code>1-7 or SUN-SAT</code></td>
 * <td>&nbsp;</td>
 * <td><code>, - * ? / L #</code></td>
 * </tr>
 * <tr>
 * <td><code>Year (Optional)</code></td>
 * <td>&nbsp;</td>
 * <td><code>empty, 1970-2199</code></td>
 * <td>&nbsp;</td>
 * <td><code>, - * /</code></td>
 * </tr>
 * </table>
 *
 * <p>The '*' character is used to specify all values. For example, &quot;*&quot; in the minute
 * field means &quot;every minute&quot;.
 *
 * <p>The '?' character is allowed for the day-of-month and day-of-week fields. It is used to
 * specify 'no specific value'. This is useful when you need to specify something in one of the two
 * fields, but not the other.
 *
 * <p>The '-' character is used to specify ranges For example &quot;10-12&quot; in the hour field
 * means &quot;the hours 10, 11 and 12&quot;.
 *
 * <p>The ',' character is used to specify additional values. For example &quot;MON,WED,FRI&quot; in
 * the day-of-week field means &quot;the days Monday, Wednesday, and Friday&quot;.
 *
 * <p>The '/' character is used to specify increments. For example &quot;0/15&quot; in the seconds
 * field means &quot;the seconds 0, 15, 30, and 45&quot;. And &quot;5/15&quot; in the seconds field
 * means &quot;the seconds 5, 20, 35, and 50&quot;. Specifying '*' before the '/' is equivalent to
 * specifying 0 is the value to start with. Essentially, for each field in the expression, there is
 * a set of numbers that can be turned on or off. For seconds and minutes, the numbers range from 0
 * to 59. For hours 0 to 23, for days of the month 0 to 31, and for months 0 to 11 (JAN to DEC). The
 * &quot;/&quot; character simply helps you turn on every &quot;nth&quot; value in the given set.
 * Thus &quot;7/6&quot; in the month field only turns on month &quot;7&quot;, it does NOT mean every
 * 6th month, please note that subtlety.
 *
 * <p>The 'L' character is allowed for the day-of-month and day-of-week fields. This character is
 * short-hand for &quot;last&quot;, but it has different meaning in each of the two fields. For
 * example, the value &quot;L&quot; in the day-of-month field means &quot;the last day of the
 * month&quot; - day 31 for January, day 28 for February on non-leap years. If used in the
 * day-of-week field by itself, it simply means &quot;7&quot; or &quot;SAT&quot;. But if used in the
 * day-of-week field after another value, it means &quot;the last xxx day of the month&quot; - for
 * example &quot;6L&quot; means &quot;the last friday of the month&quot;. You can also specify an
 * offset from the last day of the month, such as "L-3" which would mean the third-to-last day of
 * the calendar month. <i>When using the 'L' option, it is important not to specify lists, or ranges
 * of values, as you'll get confusing/unexpected results.</i>
 *
 * <p>The 'W' character is allowed for the day-of-month field. This character is used to specify the
 * weekday (Monday-Friday) nearest the given day. As an example, if you were to specify
 * &quot;15W&quot; as the value for the day-of-month field, the meaning is: &quot;the nearest
 * weekday to the 15th of the month&quot;. So if the 15th is a Saturday, the trigger will fire on
 * Friday the 14th. If the 15th is a Sunday, the trigger will fire on Monday the 16th. If the 15th
 * is a Tuesday, then it will fire on Tuesday the 15th. However if you specify &quot;1W&quot; as the
 * value for day-of-month, and the 1st is a Saturday, the trigger will fire on Monday the 3rd, as it
 * will not 'jump' over the boundary of a month's days. The 'W' character can only be specified when
 * the day-of-month is a single day, not a range or list of days.
 *
 * <p>The 'L' and 'W' characters can also be combined for the day-of-month expression to yield 'LW',
 * which translates to &quot;last weekday of the month&quot;.
 *
 * <p>The '#' character is allowed for the day-of-week field. This character is used to specify
 * &quot;the nth&quot; XXX day of the month. For example, the value of &quot;6#3&quot; in the
 * day-of-week field means the third Friday of the month (day 6 = Friday and &quot;#3&quot; = the
 * 3rd one in the month). Other examples: &quot;2#1&quot; = the first Monday of the month and
 * &quot;4#5&quot; = the fifth Wednesday of the month. Note that if you specify &quot;#5&quot; and
 * there is not 5 of the given day-of-week in the month, then no firing will occur that month. If
 * the '#' character is used, there can only be one expression in the day-of-week field
 * (&quot;3#1,6#3&quot; is not valid, since there are two expressions).
 * <!--The 'C' character is allowed for the day-of-month and day-of-week fields.
 * This character is short-hand for "calendar". This means values are
 * calculated against the associated calendar, if any. If no calendar is
 * associated, then it is equivalent to having an all-inclusive calendar. A
 * value of "5C" in the day-of-month field means "the first day included by the
 * calendar on or after the 5th". A value of "1C" in the day-of-week field
 * means "the first day included by the calendar on or after Sunday".-->
 *
 * <p>The legal characters and the names of months and days of the week are not case sensitive.
 *
 * <p><b>NOTES:</b>
 *
 * <ul>
 *   <li>Support for specifying both a day-of-week and a day-of-month value is not complete (you'll
 *       need to use the '?' character in one of these fields).
 *   <li>Overflowing ranges is supported - that is, having a larger number on the left hand side
 *       than the right. You might do 22-2 to catch 10 o'clock at night until 2 o'clock in the
 *       morning, or you might have NOV-FEB. It is very important to note that overuse of
 *       overflowing ranges creates ranges that don't make sense and no effort has been made to
 *       determine which interpretation CronExpression chooses. An example would be "0 0 14-6 ? *
 *       FRI-MON".
 * </ul>
 *
 * @author Sharada Jambula, James House
 * @author Contributions from Mads Henderson
 * @author Refactoring from CronTrigger to CronExpression by Aaron Craven
 * @since 3.3.0 (adapted from Quartz v2.5.0 for Orth scheduler)
 */
public final class CronExpression implements Serializable, Cloneable {

    private static final long serialVersionUID = 12423409423L;

    // Field type constants (array indices for cron field parsing)
    protected static final int SECOND = 0;
    protected static final int MINUTE = 1;
    protected static final int HOUR = 2;
    protected static final int DAY_OF_MONTH = 3;
    protected static final int MONTH = 4;
    protected static final int DAY_OF_WEEK = 5;
    protected static final int YEAR = 6;

    // Special value markers (sentinel values for '*' and '?')
    protected static final int ALL_SPEC_INT = 99; // '*' (all values)
    protected static final int NO_SPEC_INT = 98; // '?' (no specific value)
    protected static final Integer ALL_SPEC = ALL_SPEC_INT;
    protected static final Integer NO_SPEC = NO_SPEC_INT;

    // Last day offset configuration (for "L-N" syntax)
    protected static final int MAX_LAST_DAY_OFFSET = 30; // Max days before end of month
    protected static final int LAST_DAY_OFFSET_START = 32; // Encoding offset for "L-N"
    protected static final int LAST_DAY_OFFSET_END =
            LAST_DAY_OFFSET_START + MAX_LAST_DAY_OFFSET; // Maximum encoded value

    // Field value ranges (for validation and iteration)
    protected static final int SECOND_MIN = 0;
    protected static final int SECOND_MAX = 59;
    protected static final int MINUTE_MIN = 0;
    protected static final int MINUTE_MAX = 59;
    protected static final int HOUR_MIN = 0;
    protected static final int HOUR_MAX = 23;
    protected static final int DAY_OF_MONTH_MIN = 1;
    protected static final int DAY_OF_MONTH_MAX = 31;
    protected static final int MONTH_MIN = 1;
    protected static final int MONTH_MAX = 12;
    protected static final int DAY_OF_WEEK_MIN = 1;
    protected static final int DAY_OF_WEEK_MAX = 7;
    protected static final int YEAR_MIN = 1970;

    // Increment validation limits
    protected static final int MAX_SECOND_MINUTE_INCREMENT = 59;
    protected static final int MAX_HOUR_INCREMENT = 23;
    protected static final int MAX_DAY_OF_MONTH_INCREMENT = 31;
    protected static final int MAX_DAY_OF_WEEK_INCREMENT = 7;
    protected static final int MAX_MONTH_INCREMENT = 12;

    // Modulus values for overflow handling (wrapping ranges)
    protected static final int SECOND_MODULUS = 60;
    protected static final int MINUTE_MODULUS = 60;
    protected static final int HOUR_MODULUS = 24;
    protected static final int MONTH_MODULUS = 12;
    protected static final int DAY_OF_WEEK_MODULUS = 7;
    protected static final int DAY_OF_MONTH_MODULUS = 31;

    // Calendar navigation constants
    protected static final int MILLISECONDS_PER_SECOND = 1000;
    protected static final int MAX_YEAR_SEARCH_LIMIT = 2999; // Prevent infinite loops
    protected static final int DAYS_IN_WEEK = 7;

    // Parsing constants
    protected static final int MONTH_NAME_LENGTH = 3; // "JAN", "FEB", etc.
    protected static final int DAY_NAME_LENGTH = 3; // "MON", "TUE", etc.
    protected static final int MAX_CRON_FIELDS = 7; // Including optional year
    protected static final int SINGLE_DIGIT_THRESHOLD = 10;
    protected static final int NTH_DAY_MIN = 1;
    protected static final int NTH_DAY_MAX = 5; // Max 5th occurrence in month

    // Weekday adjustment constants (for 'W' modifier)
    protected static final int WEEKDAY_SATURDAY_OFFSET = -1;
    protected static final int WEEKDAY_SUNDAY_FORWARD_OFFSET = 1;
    protected static final int WEEKDAY_SUNDAY_BACKWARD_OFFSET = -2;
    protected static final int WEEKDAY_FIRST_DAY_SATURDAY_OFFSET = 2;

    protected static final Map<String, Integer> monthMap = new HashMap<>(20);
    protected static final Map<String, Integer> dayMap = new HashMap<>(60);

    static {
        monthMap.put("JAN", 0);
        monthMap.put("FEB", 1);
        monthMap.put("MAR", 2);
        monthMap.put("APR", 3);
        monthMap.put("MAY", 4);
        monthMap.put("JUN", 5);
        monthMap.put("JUL", 6);
        monthMap.put("AUG", 7);
        monthMap.put("SEP", 8);
        monthMap.put("OCT", 9);
        monthMap.put("NOV", 10);
        monthMap.put("DEC", 11);

        dayMap.put("SUN", 1);
        dayMap.put("MON", 2);
        dayMap.put("TUE", 3);
        dayMap.put("WED", 4);
        dayMap.put("THU", 5);
        dayMap.put("FRI", 6);
        dayMap.put("SAT", 7);
    }

    private final String cronExpression;
    private TimeZone timeZone = null;
    protected transient TreeSet<Integer> seconds;
    protected transient TreeSet<Integer> minutes;
    protected transient TreeSet<Integer> hours;
    protected transient TreeSet<Integer> daysOfMonth;
    protected transient TreeSet<Integer> nearestWeekdays;
    protected transient TreeSet<Integer> months;
    protected transient TreeSet<Integer> daysOfWeek;
    protected transient TreeSet<Integer> years;

    protected transient boolean lastDayOfWeek = false;
    protected transient int nthDayOfWeek = 0;
    protected transient boolean expressionParsed = false;

    public static final int MAX_YEAR = Calendar.getInstance().get(Calendar.YEAR) + 100;

    /**
     * Constructs a new CronExpression from a cron string.
     *
     * <p><b>Format:</b> "second minute hour day-of-month month day-of-week [year]"
     *
     * <p><b>Example:</b> "0 0/5 * * * ?" = every 5 minutes
     *
     * @param cronExpression String representation of the cron expression
     * @throws ParseException if the string expression cannot be parsed into a valid cron expression
     * @throws IllegalArgumentException if cronExpression is null
     */
    public CronExpression(String cronExpression) throws ParseException {
        if (cronExpression == null) {
            throw new IllegalArgumentException("cronExpression cannot be null");
        }

        this.cronExpression = cronExpression.toUpperCase(Locale.US);

        buildExpression(this.cronExpression);
    }

    /**
     * Constructs a new {@code CronExpression} as a copy of an existing instance.
     *
     * @param expression The existing cron expression to be copied
     */
    public CronExpression(CronExpression expression) {
        /*
         * We don't call the other constructor here since we need to swallow the
         * ParseException. We also elide some of the sanity checking as it is
         * not logically trippable.
         */
        this.cronExpression = expression.getCronExpression();
        try {
            buildExpression(cronExpression);
        } catch (ParseException ex) {
            throw new AssertionError("Could not parse expression!", ex);
        }
        if (expression.getTimeZone() != null) {
            setTimeZone((TimeZone) expression.getTimeZone().clone());
        }
    }

    /**
     * Tests whether a given date satisfies the cron expression.
     *
     * <p><b>Important:</b> Milliseconds are ignored. Two Dates on the same second are equivalent.
     *
     * <p><b>Algorithm:</b>
     *
     * <ol>
     *   <li>Truncate milliseconds from input date
     *   <li>Get next valid time after (date - 1 second)
     *   <li>If next time equals original date, expression is satisfied
     * </ol>
     *
     * @param date the date to evaluate
     * @return true if the date satisfies the cron expression, false otherwise
     */
    public boolean isSatisfiedBy(Date date) {
        Calendar testDateCal = Calendar.getInstance(getTimeZone());
        testDateCal.setTime(date);
        testDateCal.set(Calendar.MILLISECOND, 0);
        Date originalDate = testDateCal.getTime();

        // Move back one second to test if original is the next valid time
        testDateCal.add(Calendar.SECOND, -1);

        Date timeAfter = getTimeAfter(testDateCal.getTime());

        return ((timeAfter != null) && (timeAfter.equals(originalDate)));
    }

    /**
     * Returns the next date/time after the given date that satisfies the cron expression.
     *
     * <p><b>Usage:</b> Find next job execution time
     *
     * @param date the date/time to start searching from
     * @return the next valid date/time, or null if no more valid times exist
     */
    public Date getNextValidTimeAfter(Date date) {
        return getTimeAfter(date);
    }

    /**
     * Returns the next date/time after the given date that does NOT satisfy the expression.
     *
     * <p><b>Algorithm:</b> Iteratively find next valid times until gap exceeds 1 second. The last
     * valid time + 1 second is returned.
     *
     * <p><b>Performance Warning:</b> This can be slow for expressions with consecutive seconds. Use
     * sparingly.
     *
     * @param date the date/time to start searching from
     * @return the next invalid date/time
     */
    public Date getNextInvalidTimeAfter(Date date) {
        long difference = MILLISECONDS_PER_SECOND;

        // Truncate to nearest second for accurate difference calculation
        Calendar adjustCal = Calendar.getInstance(getTimeZone());
        adjustCal.setTime(date);
        adjustCal.set(Calendar.MILLISECOND, 0);
        Date lastDate = adjustCal.getTime();

        Date newDate;

        // Keep advancing until gap > 1 second (indicates non-matching time)
        while (difference == MILLISECONDS_PER_SECOND) {
            newDate = getTimeAfter(lastDate);
            if (newDate == null) {
                break;
            }

            difference = newDate.getTime() - lastDate.getTime();

            if (difference == MILLISECONDS_PER_SECOND) {
                lastDate = newDate;
            }
        }

        return new Date(lastDate.getTime() + MILLISECONDS_PER_SECOND);
    }

    /**
     * Returns the time zone for cron expression evaluation.
     *
     * @return the time zone (defaults to system time zone if not set)
     */
    public TimeZone getTimeZone() {
        if (timeZone == null) {
            timeZone = TimeZone.getDefault();
        }

        return timeZone;
    }

    /**
     * Sets the time zone for cron expression evaluation.
     *
     * <p><b>Important:</b> Changing timezone after construction affects next fire time
     * calculations.
     *
     * @param timeZone the time zone to use
     */
    public void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
    }

    /**
     * Returns the string representation of the <CODE>CronExpression</CODE>
     *
     * @return a string representation of the <CODE>CronExpression</CODE>
     */
    @Override
    public String toString() {
        return cronExpression;
    }

    /**
     * Tests whether a cron expression string is valid.
     *
     * @param cronExpression the expression to validate
     * @return true if valid, false if parsing fails
     */
    public static boolean isValidExpression(String cronExpression) {
        try {
            new CronExpression(cronExpression);
        } catch (ParseException pe) {
            return false;
        }

        return true;
    }

    /**
     * Validates a cron expression, throwing exception if invalid.
     *
     * @param cronExpression the expression to validate
     * @throws ParseException if expression is invalid
     */
    public static void validateExpression(String cronExpression) throws ParseException {
        new CronExpression(cronExpression);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Expression Parsing Functions
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Builds the internal representation of the cron expression.
     *
     * <p><b>Parsing Algorithm:</b>
     *
     * <ol>
     *   <li>Initialize all field sets (seconds, minutes, hours, etc.)
     *   <li>Tokenize expression by whitespace
     *   <li>Parse each field sequentially (SECOND -> MINUTE -> HOUR -> ...)
     *   <li>Validate field constraints (L, #, day-of-week/month conflicts)
     *   <li>Default year to '*' if not specified
     * </ol>
     *
     * <p><b>Validation Rules:</b>
     *
     * <ul>
     *   <li>Maximum 7 fields (6 required + optional year)
     *   <li>'L' cannot be combined with other day-of-week values
     *   <li>'#' can only appear once per day-of-week field
     *   <li>Cannot specify both day-of-week AND day-of-month (one must be '?')
     * </ul>
     *
     * @param expression the cron expression string to parse
     * @throws ParseException if expression is malformed or violates constraints
     */
    protected void buildExpression(String expression) throws ParseException {
        expressionParsed = true;

        try {
            // Initialize field sets
            if (seconds == null) {
                seconds = new TreeSet<>();
            }
            if (minutes == null) {
                minutes = new TreeSet<>();
            }
            if (hours == null) {
                hours = new TreeSet<>();
            }
            if (daysOfMonth == null) {
                daysOfMonth = new TreeSet<>();
            }
            if (nearestWeekdays == null) {
                nearestWeekdays = new TreeSet<>();
            }
            if (months == null) {
                months = new TreeSet<>();
            }
            if (daysOfWeek == null) {
                daysOfWeek = new TreeSet<>();
            }
            if (years == null) {
                years = new TreeSet<>();
            }

            int exprOn = SECOND;

            // Tokenize by whitespace
            StringTokenizer exprsTok = new StringTokenizer(expression, " \t", false);

            if (exprsTok.countTokens() > MAX_CRON_FIELDS) {
                throw new ParseException(
                        "Invalid expression has too many terms: " + expression, -1);
            }

            // Parse each field sequentially
            while (exprsTok.hasMoreTokens() && exprOn <= YEAR) {
                String expr = exprsTok.nextToken().trim();

                // Validate 'L' modifier (last day of week)
                if (exprOn == DAY_OF_WEEK
                        && expr.indexOf('L') != -1
                        && expr.length() > 1
                        && expr.contains(",")) {
                    throw new ParseException(
                            "Support for specifying 'L' with other days of the week is not implemented",
                            -1);
                }

                // Validate '#' modifier (nth day of week)
                if (exprOn == DAY_OF_WEEK
                        && expr.indexOf('#') != -1
                        && expr.indexOf('#', expr.indexOf('#') + 1) != -1) {
                    throw new ParseException(
                            "Support for specifying multiple \"nth\" days is not implemented.", -1);
                }

                // Parse comma-separated values
                StringTokenizer vTok = new StringTokenizer(expr, ",");
                while (vTok.hasMoreTokens()) {
                    String v = vTok.nextToken();
                    storeExpressionVals(0, v, exprOn);
                }

                exprOn++;
            }

            // Validate minimum required fields
            if (exprOn <= DAY_OF_WEEK) {
                throw new ParseException("Unexpected end of expression.", expression.length());
            }

            // Default year to '*' if not specified
            if (exprOn <= YEAR) {
                storeExpressionVals(0, "*", YEAR);
            }

            // Validate day-of-week vs day-of-month mutual exclusivity
            TreeSet<Integer> dow = getSet(DAY_OF_WEEK);
            TreeSet<Integer> dom = getSet(DAY_OF_MONTH);

            boolean dayOfMSpec = !dom.contains(NO_SPEC);
            boolean dayOfWSpec = !dow.contains(NO_SPEC);

            if (!dayOfMSpec || dayOfWSpec) {
                if (!dayOfWSpec || dayOfMSpec) {
                    throw new ParseException(
                            "Support for specifying both a day-of-week AND a day-of-month parameter is not implemented.",
                            0);
                }
            }
        } catch (ParseException pe) {
            throw pe;
        } catch (Exception e) {
            throw new ParseException("Illegal cron expression format (" + e + ")", 0);
        }
    }

    protected int storeExpressionVals(int pos, String s, int type) throws ParseException {

        int incr = 0;
        int i = skipWhiteSpace(pos, s);
        if (i >= s.length()) {
            return i;
        }
        char c = s.charAt(i);
        if ((c >= 'A')
                && (c <= 'Z')
                && (!s.equals("L"))
                && (!s.equals("LW"))
                && (!s.matches("^L-[0-9]*[W]?"))) {
            String sub = s.substring(i, i + 3);
            int sval = -1;
            int eval = -1;
            if (type == MONTH) {
                sval = getMonthNumber(sub) + 1;
                if (sval <= 0) {
                    throw new ParseException("Invalid Month value: '" + sub + "'", i);
                }
                if (s.length() > i + 3) {
                    c = s.charAt(i + 3);
                    if (c == '-') {
                        i += 4;
                        sub = s.substring(i, i + 3);
                        eval = getMonthNumber(sub) + 1;
                        if (eval <= 0) {
                            throw new ParseException("Invalid Month value: '" + sub + "'", i);
                        }
                    }
                }
            } else if (type == DAY_OF_WEEK) {
                sval = getDayOfWeekNumber(sub);
                if (sval < 0) {
                    throw new ParseException("Invalid Day-of-Week value: '" + sub + "'", i);
                }
                if (s.length() > i + 3) {
                    c = s.charAt(i + 3);
                    if (c == '-') {
                        i += 4;
                        sub = s.substring(i, i + 3);
                        eval = getDayOfWeekNumber(sub);
                        if (eval < 0) {
                            throw new ParseException("Invalid Day-of-Week value: '" + sub + "'", i);
                        }
                    } else if (c == '#') {
                        try {
                            i += 4;
                            nthDayOfWeek = Integer.parseInt(s.substring(i));
                            if (nthDayOfWeek < NTH_DAY_MIN || nthDayOfWeek > NTH_DAY_MAX) {
                                throw new Exception();
                            }
                        } catch (Exception e) {
                            throw new ParseException(
                                    "A numeric value between 1 and 5 must follow the '#' option",
                                    i);
                        }
                    } else if (c == 'L') {
                        lastDayOfWeek = true;
                        i++;
                    }
                }

            } else {
                throw new ParseException("Illegal characters for this position: '" + sub + "'", i);
            }
            if (eval != -1) {
                incr = 1;
            }
            addToSet(sval, eval, incr, type);
            return (i + 3);
        }

        if (c == '?') {
            i++;
            if ((i + 1) < s.length() && (s.charAt(i) != ' ' && s.charAt(i) != '\t')) {
                throw new ParseException("Illegal character after '?': " + s.charAt(i), i);
            }
            if (type != DAY_OF_WEEK && type != DAY_OF_MONTH) {
                throw new ParseException(
                        "'?' can only be specified for Day-of-Month or Day-of-Week.", i);
            }
            if (type == DAY_OF_WEEK) {
                if (!daysOfMonth.isEmpty() && daysOfMonth.last() == NO_SPEC_INT) {
                    throw new ParseException(
                            "'?' can only be specified for Day-of-Month -OR- Day-of-Week.", i);
                }
            }

            addToSet(NO_SPEC_INT, -1, 0, type);
            return i;
        }

        if (c == '*' || c == '/') {
            if (c == '*' && (i + 1) >= s.length()) {
                addToSet(ALL_SPEC_INT, -1, incr, type);
                return i + 1;
            } else if (c == '/'
                    && ((i + 1) >= s.length()
                            || s.charAt(i + 1) == ' '
                            || s.charAt(i + 1) == '\t')) {
                throw new ParseException("'/' must be followed by an integer.", i);
            } else if (c == '*') {
                i++;
            }
            c = s.charAt(i);
            if (c == '/') { // is an increment specified?
                i++;
                if (i >= s.length()) {
                    throw new ParseException("Unexpected end of string.", i);
                }

                incr = getNumericValue(s, i);

                // Advance position based on number of digits
                i++;
                if (incr >= SINGLE_DIGIT_THRESHOLD) {
                    i++;
                }
                checkIncrementRange(incr, type, i);
            } else {
                incr = 1;
            }

            addToSet(ALL_SPEC_INT, -1, incr, type);
            return i;
        } else if (c == 'L') {
            i++;
            if (type == DAY_OF_WEEK) {
                // 'L' in day-of-week field means Saturday (7)
                addToSet(DAY_OF_WEEK_MAX, DAY_OF_WEEK_MAX, 0, type);
            }
            if (type == DAY_OF_MONTH) {
                // 'L' in day-of-month field means last day of month
                int dom = LAST_DAY_OFFSET_END;
                boolean nearestWeekday = false;
                if (s.length() > i) {
                    c = s.charAt(i);
                    if (c == '-') {
                        // 'L-N' syntax: N days before end of month
                        ValueSet vs = getValue(0, s, i + 1);
                        int offset = vs.value;
                        if (offset > MAX_LAST_DAY_OFFSET) {
                            throw new ParseException(
                                    "Offset from last day must be <= " + MAX_LAST_DAY_OFFSET,
                                    i + 1);
                        }
                        dom -= offset;
                        i = vs.pos;
                    }
                    if (s.length() > i) {
                        c = s.charAt(i);
                        if (c == 'W') {
                            // 'LW' = last weekday of month
                            nearestWeekday = true;
                            i++;
                        }
                    }
                }
                if (nearestWeekday) {
                    nearestWeekdays.add(dom);
                } else {
                    daysOfMonth.add(dom);
                }
            }
            return i;
        } else if (c >= '0' && c <= '9') {
            int val = Integer.parseInt(String.valueOf(c));
            i++;
            if (i >= s.length()) {
                addToSet(val, -1, -1, type);
            } else {
                c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    ValueSet vs = getValue(val, s, i);
                    val = vs.value;
                    i = vs.pos;
                }
                i = checkNext(i, s, val, type);
                return i;
            }
        } else {
            throw new ParseException("Unexpected character: " + c, i);
        }

        return i;
    }

    /**
     * Validates increment value for a field type.
     *
     * <p>Ensures increment doesn't exceed field's maximum value (e.g., seconds/minutes <= 59).
     *
     * @param incr the increment value
     * @param type the field type (SECOND, MINUTE, etc.)
     * @param idxPos position in expression (for error reporting)
     * @throws ParseException if increment exceeds field maximum
     */
    private void checkIncrementRange(int incr, int type, int idxPos) throws ParseException {
        if (incr > MAX_SECOND_MINUTE_INCREMENT && (type == SECOND || type == MINUTE)) {
            throw new ParseException("Increment > 60 : " + incr, idxPos);
        } else if (incr > MAX_HOUR_INCREMENT && (type == HOUR)) {
            throw new ParseException("Increment > 24 : " + incr, idxPos);
        } else if (incr > MAX_DAY_OF_MONTH_INCREMENT && (type == DAY_OF_MONTH)) {
            throw new ParseException("Increment > 31 : " + incr, idxPos);
        } else if (incr > MAX_DAY_OF_WEEK_INCREMENT && (type == DAY_OF_WEEK)) {
            throw new ParseException("Increment > 7 : " + incr, idxPos);
        } else if (incr > MAX_MONTH_INCREMENT && (type == MONTH)) {
            throw new ParseException("Increment > 12 : " + incr, idxPos);
        }
    }

    protected int checkNext(int pos, String s, int val, int type) throws ParseException {

        int end = -1;
        int i = pos;

        if (i >= s.length()) {
            addToSet(val, end, -1, type);
            return i;
        }

        char c = s.charAt(pos);

        if (c == 'L') {
            // 'L' modifier: "last" (e.g., "5L" = last Friday of month)
            if (type == DAY_OF_WEEK) {
                if (val < DAY_OF_WEEK_MIN || val > DAY_OF_WEEK_MAX) {
                    throw new ParseException("Day-of-Week values must be between 1 and 7", -1);
                }
                lastDayOfWeek = true;
            } else {
                throw new ParseException("'L' option is not valid here. (pos=" + i + ")", i);
            }
            TreeSet<Integer> set = getSet(type);
            set.add(val);
            i++;
            return i;
        }

        if (c == 'W') {
            // 'W' modifier: nearest weekday (e.g., "15W" = weekday nearest 15th)
            if (type != DAY_OF_MONTH) {
                throw new ParseException("'W' option is not valid here. (pos=" + i + ")", i);
            }
            if (val > DAY_OF_MONTH_MAX) {
                throw new ParseException(
                        "The 'W' option does not make sense with values larger than 31 (max number of days in a month)",
                        i);
            }
            nearestWeekdays.add(val);
            i++;
            return i;
        }

        if (c == '#') {
            // '#' modifier: Nth occurrence (e.g., "5#3" = 3rd Friday of month)
            if (type != DAY_OF_WEEK) {
                throw new ParseException("'#' option is not valid here. (pos=" + i + ")", i);
            }
            i++;
            try {
                nthDayOfWeek = Integer.parseInt(s.substring(i));
                if (nthDayOfWeek < NTH_DAY_MIN || nthDayOfWeek > NTH_DAY_MAX) {
                    throw new Exception();
                }
            } catch (Exception e) {
                throw new ParseException(
                        "A numeric value between 1 and 5 must follow the '#' option", i);
            }

            TreeSet<Integer> set = getSet(type);
            set.add(val);
            i++;
            return i;
        }

        if (c == '-') {
            i++;
            c = s.charAt(i);
            int v = Integer.parseInt(String.valueOf(c));
            end = v;
            i++;
            if (i >= s.length()) {
                addToSet(val, end, 1, type);
                return i;
            }
            c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                ValueSet vs = getValue(v, s, i);
                end = vs.value;
                i = vs.pos;
            }
            if (i < s.length() && ((c = s.charAt(i)) == '/')) {
                i++;
                c = s.charAt(i);
                int v2 = Integer.parseInt(String.valueOf(c));
                i++;
                if (i >= s.length()) {
                    addToSet(val, end, v2, type);
                    return i;
                }
                c = s.charAt(i);
                if (c >= '0' && c <= '9') {
                    ValueSet vs = getValue(v2, s, i);
                    int v3 = vs.value;
                    addToSet(val, end, v3, type);
                    i = vs.pos;
                    return i;
                } else {
                    addToSet(val, end, v2, type);
                    return i;
                }
            } else {
                addToSet(val, end, 1, type);
                return i;
            }
        }

        if (c == '/') {
            if ((i + 1) >= s.length() || s.charAt(i + 1) == ' ' || s.charAt(i + 1) == '\t') {
                throw new ParseException("'/' must be followed by an integer.", i);
            }

            i++;
            c = s.charAt(i);
            int v2 = Integer.parseInt(String.valueOf(c));
            i++;
            if (i >= s.length()) {
                checkIncrementRange(v2, type, i);
                addToSet(val, end, v2, type);
                return i;
            }
            c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                ValueSet vs = getValue(v2, s, i);
                int v3 = vs.value;
                checkIncrementRange(v3, type, i);
                addToSet(val, end, v3, type);
                i = vs.pos;
                return i;
            } else {
                throw new ParseException("Unexpected character '" + c + "' after '/'", i);
            }
        }

        addToSet(val, end, 0, type);
        i++;
        return i;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public String getExpressionSummary() {
        StringBuilder buf = new StringBuilder();

        buf.append("seconds: ");
        buf.append(getExpressionSetSummary(seconds));
        buf.append("\n");
        buf.append("minutes: ");
        buf.append(getExpressionSetSummary(minutes));
        buf.append("\n");
        buf.append("hours: ");
        buf.append(getExpressionSetSummary(hours));
        buf.append("\n");
        buf.append("daysOfMonth: ");
        buf.append(getExpressionSetSummary(daysOfMonth));
        buf.append("\n");
        buf.append("nearestWeekdays: ");
        buf.append(getExpressionSetSummary(nearestWeekdays));
        buf.append("\n");
        buf.append("months: ");
        buf.append(getExpressionSetSummary(months));
        buf.append("\n");
        buf.append("daysOfWeek: ");
        buf.append(getExpressionSetSummary(daysOfWeek));
        buf.append("\n");
        buf.append("lastDayOfWeek: ");
        buf.append(lastDayOfWeek);
        buf.append("\n");
        buf.append("NthDayOfWeek: ");
        buf.append(nthDayOfWeek);
        buf.append("\n");
        buf.append("years: ");
        buf.append(getExpressionSetSummary(years));
        buf.append("\n");

        return buf.toString();
    }

    protected String getExpressionSetSummary(java.util.Set<Integer> set) {

        if (set.contains(NO_SPEC)) {
            return "?";
        }
        if (set.contains(ALL_SPEC)) {
            return "*";
        }

        StringBuilder buf = new StringBuilder();

        Iterator<Integer> itr = set.iterator();
        boolean first = true;
        while (itr.hasNext()) {
            Integer iVal = itr.next();
            String val = iVal.toString();
            if (!first) {
                buf.append(",");
            }
            buf.append(val);
            first = false;
        }

        return buf.toString();
    }

    protected String getExpressionSetSummary(java.util.ArrayList<Integer> list) {

        if (list.contains(NO_SPEC)) {
            return "?";
        }
        if (list.contains(ALL_SPEC)) {
            return "*";
        }

        StringBuilder buf = new StringBuilder();

        Iterator<Integer> itr = list.iterator();
        boolean first = true;
        while (itr.hasNext()) {
            Integer iVal = itr.next();
            String val = iVal.toString();
            if (!first) {
                buf.append(",");
            }
            buf.append(val);
            first = false;
        }

        return buf.toString();
    }

    protected int skipWhiteSpace(int i, String s) {
        for (; i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t'); i++) {}

        return i;
    }

    protected int findNextWhiteSpace(int i, String s) {
        for (; i < s.length() && (s.charAt(i) != ' ' || s.charAt(i) != '\t'); i++) {}

        return i;
    }

    /**
     * Adds value(s) to a field's value set, handling ranges and increments.
     *
     * <p><b>Supports:</b>
     *
     * <ul>
     *   <li>Single value: val=5, end=-1, incr=0 -> adds 5
     *   <li>Range: val=5, end=10, incr=1 -> adds 5,6,7,8,9,10
     *   <li>Increment: val=0, end=-1, incr=5 -> adds 0,5,10,15,...
     *   <li>Range with increment: val=5, end=20, incr=3 -> adds 5,8,11,14,17,20
     * </ul>
     *
     * <p><b>Overflow Handling:</b> Ranges like "22-2" (10pm to 2am) overflow into next day by
     * adding modulus value.
     *
     * @param val start value (or single value if end=-1)
     * @param end end value (-1 for no range)
     * @param incr increment value (0 or -1 for single value, >0 for step)
     * @param type field type (SECOND, MINUTE, etc.)
     * @throws ParseException if values are out of range for field type
     */
    protected void addToSet(int val, int end, int incr, int type) throws ParseException {

        TreeSet<Integer> set = getSet(type);

        // Validate value ranges for each field type
        if (type == SECOND || type == MINUTE) {
            if ((val < SECOND_MIN || val > SECOND_MAX || end > SECOND_MAX)
                    && (val != ALL_SPEC_INT)) {
                throw new ParseException("Minute and Second values must be between 0 and 59", -1);
            }
        } else if (type == HOUR) {
            if ((val < HOUR_MIN || val > HOUR_MAX || end > HOUR_MAX) && (val != ALL_SPEC_INT)) {
                throw new ParseException("Hour values must be between 0 and 23", -1);
            }
        } else if (type == DAY_OF_MONTH) {
            if ((val < DAY_OF_MONTH_MIN || val > DAY_OF_MONTH_MAX || end > DAY_OF_MONTH_MAX)
                    && (val != ALL_SPEC_INT)
                    && (val != NO_SPEC_INT)) {
                throw new ParseException("Day of month values must be between 1 and 31", -1);
            }
        } else if (type == MONTH) {
            if ((val < MONTH_MIN || val > MONTH_MAX || end > MONTH_MAX) && (val != ALL_SPEC_INT)) {
                throw new ParseException("Month values must be between 1 and 12", -1);
            }
        } else if (type == DAY_OF_WEEK) {
            if ((val == 0 || val > DAY_OF_WEEK_MAX || end > DAY_OF_WEEK_MAX)
                    && (val != ALL_SPEC_INT)
                    && (val != NO_SPEC_INT)) {
                throw new ParseException("Day-of-Week values must be between 1 and 7", -1);
            }
        }

        // Handle single value (no range/increment)
        if ((incr == 0 || incr == -1) && val != ALL_SPEC_INT) {
            if (val != -1) {
                set.add(val);
            } else {
                set.add(NO_SPEC);
            }

            return;
        }

        int startAt = val;
        int stopAt = end;

        // For '*' with no increment, default to step of 1
        if (val == ALL_SPEC_INT && incr <= 0) {
            incr = 1;
            set.add(ALL_SPEC); // marker for '*' (also fills values below)
        }

        // Set default start/end values based on field type
        if (type == SECOND || type == MINUTE) {
            if (stopAt == -1) {
                stopAt = SECOND_MAX;
            }
            if (startAt == -1 || startAt == ALL_SPEC_INT) {
                startAt = SECOND_MIN;
            }
        } else if (type == HOUR) {
            if (stopAt == -1) {
                stopAt = HOUR_MAX;
            }
            if (startAt == -1 || startAt == ALL_SPEC_INT) {
                startAt = HOUR_MIN;
            }
        } else if (type == DAY_OF_MONTH) {
            if (stopAt == -1) {
                stopAt = DAY_OF_MONTH_MAX;
            }
            if (startAt == -1 || startAt == ALL_SPEC_INT) {
                startAt = DAY_OF_MONTH_MIN;
            }
        } else if (type == MONTH) {
            if (stopAt == -1) {
                stopAt = MONTH_MAX;
            }
            if (startAt == -1 || startAt == ALL_SPEC_INT) {
                startAt = MONTH_MIN;
            }
        } else if (type == DAY_OF_WEEK) {
            if (stopAt == -1) {
                stopAt = DAY_OF_WEEK_MAX;
            }
            if (startAt == -1 || startAt == ALL_SPEC_INT) {
                startAt = DAY_OF_WEEK_MIN;
            }
        } else if (type == YEAR) {
            if (stopAt == -1) {
                stopAt = MAX_YEAR;
            }
            if (startAt == -1 || startAt == ALL_SPEC_INT) {
                startAt = YEAR_MIN;
            }
        }

        // Handle overflow ranges (e.g., "22-2" for hours wraps around midnight)
        int max = -1;
        if (stopAt < startAt) {
            switch (type) {
                case SECOND:
                    max = SECOND_MODULUS;
                    break;
                case MINUTE:
                    max = MINUTE_MODULUS;
                    break;
                case HOUR:
                    max = HOUR_MODULUS;
                    break;
                case MONTH:
                    max = MONTH_MODULUS;
                    break;
                case DAY_OF_WEEK:
                    max = DAY_OF_WEEK_MODULUS;
                    break;
                case DAY_OF_MONTH:
                    max = DAY_OF_MONTH_MODULUS;
                    break;
                case YEAR:
                    throw new IllegalArgumentException("Start year must be less than stop year");
                default:
                    throw new IllegalArgumentException("Unexpected type encountered");
            }
            stopAt += max;
        }

        // Populate set with values in range, applying increment
        for (int i = startAt; i <= stopAt; i += incr) {
            if (max == -1) {
                // No overflow, add value directly
                set.add(i);
            } else {
                // Overflow range: apply modulus to wrap around
                int i2 = i % max;

                // 1-indexed fields: 0 wraps to max (e.g., month 0 -> 12)
                if (i2 == 0 && (type == MONTH || type == DAY_OF_WEEK || type == DAY_OF_MONTH)) {
                    i2 = max;
                }

                set.add(i2);
            }
        }
    }

    TreeSet<Integer> getSet(int type) {
        switch (type) {
            case SECOND:
                return seconds;
            case MINUTE:
                return minutes;
            case HOUR:
                return hours;
            case DAY_OF_MONTH:
                return daysOfMonth;
            case MONTH:
                return months;
            case DAY_OF_WEEK:
                return daysOfWeek;
            case YEAR:
                return years;
            default:
                return null;
        }
    }

    protected ValueSet getValue(int v, String s, int i) {
        char c = s.charAt(i);
        StringBuilder s1 = new StringBuilder(String.valueOf(v));
        while (c >= '0' && c <= '9') {
            s1.append(c);
            i++;
            if (i >= s.length()) {
                break;
            }
            c = s.charAt(i);
        }
        ValueSet val = new ValueSet();

        val.pos = (i < s.length()) ? i : i + 1;
        val.value = Integer.parseInt(s1.toString());
        return val;
    }

    protected int getNumericValue(String s, int i) {
        int endOfVal = findNextWhiteSpace(i, s);
        String val = s.substring(i, endOfVal);
        return Integer.parseInt(val);
    }

    protected int getMonthNumber(String s) {
        Integer integer = monthMap.get(s);

        if (integer == null) {
            return -1;
        }

        return integer;
    }

    protected int getDayOfWeekNumber(String s) {
        Integer integer = dayMap.get(s);

        if (integer == null) {
            return -1;
        }

        return integer;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Computation Functions
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Computes the next date/time after the given date that satisfies the cron expression.
     *
     * <p><b>Core Algorithm:</b>
     *
     * <ol>
     *   <li>Advance 1 second past input time
     *   <li>Truncate milliseconds (cron operates on second precision)
     *   <li>Iterate through fields: second -> minute -> hour -> day -> month -> year
     *   <li>For each field, find next valid value >= current value
     *   <li>If field advances, reset all smaller fields to their minimum
     *   <li>Validate day-of-week/day-of-month constraints
     *   <li>Return first valid date, or null if no valid time exists
     * </ol>
     *
     * <p><b>Special Handling:</b>
     *
     * <ul>
     *   <li>Day-of-week: handles 'L' (last), '#' (nth), and nearest weekday
     *   <li>Day-of-month: handles 'L' (last day), 'W' (nearest weekday)
     *   <li>DST transitions: hour adjustments for daylight saving
     *   <li>Overflow protection: stops at year 2999 to prevent infinite loops
     * </ul>
     *
     * @param afterTime the reference time (next valid time must be strictly after this)
     * @return the next valid date/time, or null if no more valid times exist
     */
    public Date getTimeAfter(Date afterTime) {

        // Computation is based on Gregorian calendar
        Calendar cl = new java.util.GregorianCalendar(getTimeZone());

        // Advance 1 second (we need time AFTER the given time)
        afterTime = new Date(afterTime.getTime() + MILLISECONDS_PER_SECOND);

        // Truncate milliseconds (cron expressions work at second precision)
        cl.setTime(afterTime);
        cl.set(Calendar.MILLISECOND, 0);

        boolean gotOne = false;
        // Loop until we find a valid time (all fields match expression)
        while (!gotOne) {

            // Prevent infinite loops (e.g., invalid expressions or year exhaustion)
            if (cl.get(Calendar.YEAR) > MAX_YEAR_SEARCH_LIMIT) {
                return null;
            }

            SortedSet<Integer> st = null;
            int t = 0;

            int sec = cl.get(Calendar.SECOND);
            int min = cl.get(Calendar.MINUTE);

            // Step 1: Find next valid second >= current second
            st = seconds.tailSet(sec);
            if (st != null && !st.isEmpty()) {
                sec = st.first();
            } else {
                // No valid second in current minute, wrap to next minute
                sec = seconds.first();
                min++;
                cl.set(Calendar.MINUTE, min);
            }
            cl.set(Calendar.SECOND, sec);

            min = cl.get(Calendar.MINUTE);
            int hr = cl.get(Calendar.HOUR_OF_DAY);
            t = -1;

            // Step 2: Find next valid minute >= current minute
            st = minutes.tailSet(min);
            if (st != null && !st.isEmpty()) {
                t = min;
                min = st.first();
            } else {
                // No valid minute in current hour, wrap to next hour
                min = minutes.first();
                hr++;
            }
            if (min != t) {
                // Minute changed, reset seconds and restart loop
                cl.set(Calendar.SECOND, 0);
                cl.set(Calendar.MINUTE, min);
                setCalendarHour(cl, hr);
                continue;
            }
            cl.set(Calendar.MINUTE, min);

            hr = cl.get(Calendar.HOUR_OF_DAY);
            int day = cl.get(Calendar.DAY_OF_MONTH);
            t = -1;

            // Step 3: Find next valid hour >= current hour
            st = hours.tailSet(hr);
            if (st != null && !st.isEmpty()) {
                t = hr;
                hr = st.first();
            } else {
                // No valid hour today, wrap to next day
                hr = hours.first();
                day++;
            }
            if (hr != t) {
                // Hour changed, reset seconds and minutes and restart loop
                cl.set(Calendar.SECOND, 0);
                cl.set(Calendar.MINUTE, 0);
                cl.set(Calendar.DAY_OF_MONTH, day);
                setCalendarHour(cl, hr);
                continue;
            }
            cl.set(Calendar.HOUR_OF_DAY, hr);

            day = cl.get(Calendar.DAY_OF_MONTH);
            int mon = cl.get(Calendar.MONTH) + 1;
            // +1 because Calendar.MONTH is 0-based (0=Jan), but cron uses 1-based (1=Jan)
            t = -1;
            int tmon = mon;

            // Step 4: Find next valid day (complex logic due to day-of-week/month)
            boolean dayOfMSpec = !daysOfMonth.contains(NO_SPEC);
            boolean dayOfWSpec = !daysOfWeek.contains(NO_SPEC);
            if (dayOfMSpec && !dayOfWSpec) { // get day by day of month rule
                Optional<Integer> smallestDay =
                        findSmallestDay(day, mon, cl.get(Calendar.YEAR), daysOfMonth);
                Optional<Integer> smallestDayForWeekday =
                        findSmallestDay(day, mon, cl.get(Calendar.YEAR), nearestWeekdays);
                t = day;
                day = -1;
                if (smallestDayForWeekday.isPresent()) {
                    day = smallestDayForWeekday.get();

                    java.util.Calendar tcal = java.util.Calendar.getInstance(getTimeZone());
                    tcal.set(Calendar.SECOND, 0);
                    tcal.set(Calendar.MINUTE, 0);
                    tcal.set(Calendar.HOUR_OF_DAY, 0);
                    tcal.set(Calendar.DAY_OF_MONTH, day);
                    tcal.set(Calendar.MONTH, mon - 1);
                    tcal.set(Calendar.YEAR, cl.get(Calendar.YEAR));

                    int ldom = getLastDayOfMonth(mon, cl.get(Calendar.YEAR));
                    int dow = tcal.get(Calendar.DAY_OF_WEEK);

                    // Adjust to nearest weekday (Monday-Friday)
                    if (dow == Calendar.SATURDAY && day == 1) {
                        // Saturday on 1st -> Monday 3rd
                        day += WEEKDAY_FIRST_DAY_SATURDAY_OFFSET;
                    } else if (dow == Calendar.SATURDAY) {
                        // Saturday -> Friday before
                        day += WEEKDAY_SATURDAY_OFFSET;
                    } else if (dow == Calendar.SUNDAY && day == ldom) {
                        // Sunday on last day -> Friday before
                        day += WEEKDAY_SUNDAY_BACKWARD_OFFSET;
                    } else if (dow == Calendar.SUNDAY) {
                        // Sunday -> Monday after
                        day += WEEKDAY_SUNDAY_FORWARD_OFFSET;
                    }

                    tcal.set(Calendar.SECOND, sec);
                    tcal.set(Calendar.MINUTE, min);
                    tcal.set(Calendar.HOUR_OF_DAY, hr);
                    tcal.set(Calendar.DAY_OF_MONTH, day);
                    tcal.set(Calendar.MONTH, mon - 1);
                    Date nTime = tcal.getTime();
                    if (nTime.before(afterTime)) {
                        day = -1;
                    }
                }
                if (smallestDay.isPresent()) {
                    if (day == -1 || smallestDay.get() < day) {
                        day = smallestDay.get();
                    }
                } else if (day == -1) {
                    day = 1;
                    mon++;
                }
                if (day != t || mon != tmon) {
                    cl.set(Calendar.SECOND, 0);
                    cl.set(Calendar.MINUTE, 0);
                    cl.set(Calendar.HOUR_OF_DAY, 0);
                    cl.set(Calendar.DAY_OF_MONTH, day);
                    cl.set(Calendar.MONTH, mon - 1);
                    // '- 1' because calendar is 0-based for this field, and we
                    // are 1-based
                    continue;
                }
            } else if (dayOfWSpec && !dayOfMSpec) { // get day by day of week rule
                if (lastDayOfWeek) { // are we looking for the last XXX day of
                    // the month?
                    int dow = daysOfWeek.first(); // desired
                    // d-o-w
                    int cDow = cl.get(Calendar.DAY_OF_WEEK); // current d-o-w
                    int daysToAdd = 0;
                    if (cDow < dow) {
                        daysToAdd = dow - cDow;
                    }
                    if (cDow > dow) {
                        daysToAdd = dow + (DAYS_IN_WEEK - cDow);
                    }

                    int lDay = getLastDayOfMonth(mon, cl.get(Calendar.YEAR));

                    if (day + daysToAdd > lDay) {
                        // Desired day-of-week doesn't exist this month, advance to next
                        cl.set(Calendar.SECOND, 0);
                        cl.set(Calendar.MINUTE, 0);
                        cl.set(Calendar.HOUR_OF_DAY, 0);
                        cl.set(Calendar.DAY_OF_MONTH, 1);
                        cl.set(Calendar.MONTH, mon);
                        // No -1 because we're advancing month
                        continue;
                    }

                    // Find LAST occurrence of this day-of-week in month
                    while ((day + daysToAdd + DAYS_IN_WEEK) <= lDay) {
                        daysToAdd += DAYS_IN_WEEK;
                    }

                    day += daysToAdd;

                    if (daysToAdd > 0) {
                        cl.set(Calendar.SECOND, 0);
                        cl.set(Calendar.MINUTE, 0);
                        cl.set(Calendar.HOUR_OF_DAY, 0);
                        cl.set(Calendar.DAY_OF_MONTH, day);
                        cl.set(Calendar.MONTH, mon - 1);
                        // '- 1' here because we are not promoting the month
                        continue;
                    }

                } else if (nthDayOfWeek != 0) {
                    // Looking for Nth occurrence of day-of-week (e.g., "3rd Friday")
                    int dow = daysOfWeek.first(); // desired day-of-week
                    int cDow = cl.get(Calendar.DAY_OF_WEEK); // current day-of-week
                    int daysToAdd = 0;

                    // Calculate days to reach next occurrence of desired day
                    if (cDow < dow) {
                        daysToAdd = dow - cDow;
                    } else if (cDow > dow) {
                        daysToAdd = dow + (DAYS_IN_WEEK - cDow);
                    }

                    boolean dayShifted = daysToAdd > 0;

                    day += daysToAdd;

                    // Calculate which week of month we're in
                    int weekOfMonth = day / DAYS_IN_WEEK;
                    if (day % DAYS_IN_WEEK > 0) {
                        weekOfMonth++;
                    }

                    // Adjust to reach Nth occurrence
                    daysToAdd = (nthDayOfWeek - weekOfMonth) * DAYS_IN_WEEK;
                    day += daysToAdd;
                    if (daysToAdd < 0 || day > getLastDayOfMonth(mon, cl.get(Calendar.YEAR))) {
                        cl.set(Calendar.SECOND, 0);
                        cl.set(Calendar.MINUTE, 0);
                        cl.set(Calendar.HOUR_OF_DAY, 0);
                        cl.set(Calendar.DAY_OF_MONTH, 1);
                        cl.set(Calendar.MONTH, mon);
                        // no '- 1' here because we are promoting the month
                        continue;
                    } else if (daysToAdd > 0 || dayShifted) {
                        cl.set(Calendar.SECOND, 0);
                        cl.set(Calendar.MINUTE, 0);
                        cl.set(Calendar.HOUR_OF_DAY, 0);
                        cl.set(Calendar.DAY_OF_MONTH, day);
                        cl.set(Calendar.MONTH, mon - 1);
                        // '- 1' here because we are NOT promoting the month
                        continue;
                    }
                } else {
                    // Standard day-of-week matching
                    int cDow = cl.get(Calendar.DAY_OF_WEEK); // current day-of-week
                    int dow = daysOfWeek.first(); // desired day-of-week

                    // Find next valid day-of-week >= current
                    st = daysOfWeek.tailSet(cDow);
                    if (st != null && !st.isEmpty()) {
                        dow = st.first();
                    }

                    int daysToAdd = 0;
                    if (cDow < dow) {
                        daysToAdd = dow - cDow;
                    }
                    if (cDow > dow) {
                        // Wrap to next week
                        daysToAdd = dow + (DAYS_IN_WEEK - cDow);
                    }

                    int lDay = getLastDayOfMonth(mon, cl.get(Calendar.YEAR));

                    if (day + daysToAdd > lDay) { // will we pass the end of
                        // the month?
                        cl.set(Calendar.SECOND, 0);
                        cl.set(Calendar.MINUTE, 0);
                        cl.set(Calendar.HOUR_OF_DAY, 0);
                        cl.set(Calendar.DAY_OF_MONTH, 1);
                        cl.set(Calendar.MONTH, mon);
                        // no '- 1' here because we are promoting the month
                        continue;
                    } else if (daysToAdd > 0) { // are we switching days?
                        cl.set(Calendar.SECOND, 0);
                        cl.set(Calendar.MINUTE, 0);
                        cl.set(Calendar.HOUR_OF_DAY, 0);
                        cl.set(Calendar.DAY_OF_MONTH, day + daysToAdd);
                        cl.set(Calendar.MONTH, mon - 1);
                        // '- 1' because calendar is 0-based for this field,
                        // and we are 1-based
                        continue;
                    }
                }
            } else { // dayOfWSpec && !dayOfMSpec
                throw new UnsupportedOperationException(
                        "Support for specifying both a day-of-week AND a day-of-month parameter is not implemented.");
            }
            cl.set(Calendar.DAY_OF_MONTH, day);

            mon = cl.get(Calendar.MONTH) + 1;
            // +1: Calendar uses 0-based months, cron uses 1-based
            int year = cl.get(Calendar.YEAR);
            t = -1;

            // Check if year exceeded maximum (prevent invalid expressions looping forever)
            if (year > MAX_YEAR) {
                return null;
            }

            // Step 5: Find next valid month >= current month
            st = months.tailSet(mon);
            if (st != null && !st.isEmpty()) {
                t = mon;
                mon = st.first();
            } else {
                // No valid month this year, wrap to next year
                mon = months.first();
                year++;
            }
            if (mon != t) {
                // Month changed, reset all smaller fields and restart loop
                cl.set(Calendar.SECOND, 0);
                cl.set(Calendar.MINUTE, 0);
                cl.set(Calendar.HOUR_OF_DAY, 0);
                cl.set(Calendar.DAY_OF_MONTH, 1);
                cl.set(Calendar.MONTH, mon - 1); // -1: convert back to 0-based
                cl.set(Calendar.YEAR, year);
                continue;
            }
            cl.set(Calendar.MONTH, mon - 1); // -1: convert back to 0-based

            year = cl.get(Calendar.YEAR);
            t = -1;

            // Step 6: Find next valid year >= current year
            st = years.tailSet(year);
            if (st != null && !st.isEmpty()) {
                t = year;
                year = st.first();
            } else {
                return null; // No more valid years
            }

            if (year != t) {
                // Year changed, reset all smaller fields and restart loop
                cl.set(Calendar.SECOND, 0);
                cl.set(Calendar.MINUTE, 0);
                cl.set(Calendar.HOUR_OF_DAY, 0);
                cl.set(Calendar.DAY_OF_MONTH, 1);
                cl.set(Calendar.MONTH, 0); // January
                cl.set(Calendar.YEAR, year);
                continue;
            }
            cl.set(Calendar.YEAR, year);

            // All fields match expression constraints
            gotOne = true;
        } // while( !gotOne )

        return cl.getTime();
    }

    /**
     * Sets calendar hour with DST (Daylight Saving Time) adjustment.
     *
     * <p><b>DST Handling:</b> When DST transition occurs, setting hour may fail (e.g., 2am doesn't
     * exist on spring forward). This method detects mismatch and adjusts +1 hour.
     *
     * @param cal the calendar to operate on
     * @param hour the hour to set (0-23)
     */
    protected void setCalendarHour(Calendar cal, int hour) {
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour);

        // Check if DST caused hour to skip (e.g., 2am -> 3am on spring forward)
        if (cal.get(java.util.Calendar.HOUR_OF_DAY) != hour && hour != HOUR_MODULUS) {
            cal.set(java.util.Calendar.HOUR_OF_DAY, hour + 1);
        }
    }

    /**
     * NOT YET IMPLEMENTED: Returns the time before the given time that matches the expression.
     *
     * <p><b>Status:</b> Not implemented (requires reverse iteration algorithm)
     *
     * @param endTime the reference time
     * @return null (not implemented)
     */
    public Date getTimeBefore(Date endTime) {
        // Future enhancement: implement backward time search
        return null;
    }

    /**
     * NOT YET IMPLEMENTED: Returns the final time that the expression will match.
     *
     * <p><b>Status:</b> Not implemented (requires parsing year constraints)
     *
     * @return null (not implemented)
     */
    public Date getFinalFireTime() {
        // Future enhancement: calculate last fire time based on year range
        return null;
    }

    /**
     * Tests whether a year is a leap year.
     *
     * <p><b>Leap Year Rules:</b>
     *
     * <ul>
     *   <li>Divisible by 4: leap year
     *   <li>Divisible by 100: NOT leap year (exception)
     *   <li>Divisible by 400: leap year (exception to exception)
     * </ul>
     *
     * @param year the year to test
     * @return true if leap year, false otherwise
     */
    protected boolean isLeapYear(int year) {
        return ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0));
    }

    /**
     * Returns the last day of a given month/year.
     *
     * <p><b>Days per month:</b> Jan=31, Feb=28/29, Mar=31, Apr=30, May=31, Jun=30, Jul=31, Aug=31,
     * Sep=30, Oct=31, Nov=30, Dec=31
     *
     * @param monthNum the month (1-12)
     * @param year the year (for leap year calculation)
     * @return the last day of the month (28-31)
     * @throws IllegalArgumentException if monthNum is not 1-12
     */
    protected int getLastDayOfMonth(int monthNum, int year) {

        switch (monthNum) {
            case 1: // January
                return 31;
            case 2: // February (leap year aware)
                return (isLeapYear(year)) ? 29 : 28;
            case 3: // March
                return 31;
            case 4: // April
                return 30;
            case 5: // May
                return 31;
            case 6: // June
                return 30;
            case 7: // July
                return 31;
            case 8: // August
                return 31;
            case 9: // September
                return 30;
            case 10: // October
                return 31;
            case 11: // November
                return 30;
            case 12: // December
                return 31;
            default:
                throw new IllegalArgumentException("Illegal month number: " + monthNum);
        }
    }

    /**
     * Finds the smallest valid day >= current day from a set of day specifications.
     *
     * <p><b>Handles two encodings:</b>
     *
     * <ul>
     *   <li>Direct days: 1-31 (normal day-of-month)
     *   <li>Last day offsets: 32-62 (encoded as LAST_DAY_OFFSET_START + offset)
     * </ul>
     *
     * <p><b>Algorithm:</b>
     *
     * <ol>
     *   <li>Decode "L-N" values (last day minus N)
     *   <li>Find smallest direct day >= current day (capped at month end)
     *   <li>Return minimum of both results
     * </ol>
     *
     * @param day current day of month
     * @param mon current month (1-12)
     * @param year current year
     * @param set set of day specifications (may include encoded last-day values)
     * @return smallest valid day, or empty if none found
     */
    private Optional<Integer> findSmallestDay(int day, int mon, int year, TreeSet<Integer> set) {
        if (set.isEmpty()) {
            return Optional.empty();
        }

        final int lastDay = getLastDayOfMonth(mon, year);

        // Decode "L-N" syntax (e.g., "L-3" = 3 days before end of month)
        int smallestDay =
                Optional.ofNullable(set.ceiling(LAST_DAY_OFFSET_END - (lastDay - day)))
                        .map(d -> d - LAST_DAY_OFFSET_START + 1)
                        .orElse(Integer.MAX_VALUE);

        // Find smallest direct day-of-month >= current day
        SortedSet<Integer> st = set.subSet(day, LAST_DAY_OFFSET_START);

        // Ensure day doesn't exceed month length (e.g., Feb 31 is invalid)
        if (!st.isEmpty() && st.first() < smallestDay && st.first() <= lastDay) {
            smallestDay = st.first();
        }

        return smallestDay == Integer.MAX_VALUE ? Optional.empty() : Optional.of(smallestDay);
    }

    /**
     * Custom deserialization handler.
     *
     * <p>Rebuilds transient field sets after deserialization.
     *
     * @param stream the input stream
     * @throws java.io.IOException if I/O error occurs
     * @throws ClassNotFoundException if class not found
     */
    private void readObject(java.io.ObjectInputStream stream)
            throws java.io.IOException, ClassNotFoundException {

        stream.defaultReadObject();
        try {
            buildExpression(cronExpression);
        } catch (Exception ignore) {
            // Should never happen (expression was valid during serialization)
        }
    }

    /**
     * Creates a copy of this CronExpression.
     *
     * @deprecated Use copy constructor instead: {@code new CronExpression(original)}
     * @return a clone of this expression
     */
    @Override
    @Deprecated
    public Object clone() {
        return new CronExpression(this);
    }
}

/**
 * Internal helper class for parsing numeric values.
 *
 * <p>Holds both the parsed value and the position where parsing stopped.
 */
class ValueSet {
    public int value;
    public int pos;
}
