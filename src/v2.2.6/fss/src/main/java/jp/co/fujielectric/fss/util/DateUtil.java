package jp.co.fujielectric.fss.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import org.apache.commons.lang3.StringUtils;

/**
 * 日付関連ユーティリティ
 */
public class DateUtil {

    /**
     * システム日付の取得
     *
     * @return	システム日付
     */
    public static Date getSysDate() {
        // システム日付の取得
        Date sysDate = new Date(System.currentTimeMillis());
        // 日付と時刻のフォーマット変換
        return sysDate;
    }

    /**
     * ミリ秒を除いたシステム日付
     *
     * @return ミリ秒を除いたシステム日付
     */
    public static Date getSysDateExcludeMillis() {
        // システム日付の取得
        Date _sysDate = new Date(System.currentTimeMillis());

        // ミリ秒を含まない日付
        SimpleDateFormat _sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        String _strDate = _sdf.format(_sysDate);

        // Date型変換
        try {
            Date formatDate = _sdf.parse(_strDate);
            return formatDate;
        } catch (ParseException ex) {
        }
        return null;
    }

    /**
     * 指定パラメータ分日付に加算します。<br>
     * 年月日それぞれを指定可能。複数指定は不可。
     *
     * @param date 対象日付
     * @param param パラメータ（ex. 1y, 2m, 7d,,,）
     * @return 日付
     */
    public static Date addDays(Date date, String param) {
        if (StringUtils.isEmpty(param)) {
            return date;
        } else {
            int value = 0;
            try {
                value = Integer.parseInt(param.substring(0, param.length() - 1));
            } catch (Exception e) {
                return date;
            }

            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            String unit = param.substring(param.length() - 1, param.length());

            switch (unit) {
                case "y":
                    cal.add(Calendar.YEAR, value);
                    break;
                case "m":
                    cal.add(Calendar.MONTH, value);
                    break;
                case "d":
                    cal.add(Calendar.DAY_OF_MONTH, value);
                    break;
                default:
            }

            // 期限日の設定でしか呼ばれないメソッドのため、
            // 時分秒を23:59:59に書き換える。
//            return new Date(cal.getTimeInMillis());
            return getDateExcludeMillisExpirationTime(new Date(cal.getTimeInMillis()));
        }
    }

    /**
     * Date型日時から時刻情報を省く
     *
     * @param dt
     * @return 時刻情報を省いたDate型日付
     */
    public static Date getDateExcludeTime(Date dt) {
        //時刻を含まない日付
        SimpleDateFormat sdfShort = new SimpleDateFormat("yyyyMMdd");
        String dateStr = sdfShort.format(dt);

        // Date型変換
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        try {
            Date formatDate = sdf.parse(dateStr + " 00:00:00");
            return formatDate;
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * 時分秒を23:59:59に変換したミリ秒を除くDate型を返す
     *
     * @param dt Date型日時
     * @return 時分秒を23:59:59に変換したミリ秒を除くDate型日付
     */
    public static Date getDateExcludeMillisExpirationTime(Date dt) {
        // 年月日を取得
        SimpleDateFormat _sdfShort = new SimpleDateFormat("yyyy/MM/dd");
        String _strDateShort = _sdfShort.format(dt);

        SimpleDateFormat _sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

        // Date型変換
        try {
            Date formatDate = _sdf.parse(_strDateShort + " 23:59:59");
            return formatDate;
        } catch (ParseException ex) {
        }
        return null;
    }

    /**
     * 年、月、日のintからDate型を返す（月は1～12）
     * @param year 年
     * @param month 月
     * @param day 日
     * @return 日付型
     */
    public static Date getDate(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }
}
