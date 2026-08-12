package com.njwenglish.common.util;

import java.time.LocalDate;

/**
 * "몇 월 몇 주차"의 <b>정본</b>. 달 안에서 1일부터 7일씩 끊어 1~5주차로 본다.
 *
 * <p>달 경계에 걸친 주는 세는 방식이 갈린다(ISO 주차·월요일 시작 등). 이 서비스는 학원 운영
 * 감각에 맞춰 "그 달 며칠이냐"로만 센다 — 수업은 선생님이 화면에서 고칠 수 있고,
 * 클리닉처럼 고칠 일이 없는 곳은 이 계산을 그대로 쓴다.
 *
 * <p><b>라벨을 프론트에서 조립하지 마라.</b> 화면마다 "8월 2주" · "8월 2주차" · "2주차"로
 * 갈라진다. 서버가 만든 문자열을 그대로 그려라.
 */
public final class MonthWeeks {

    private MonthWeeks() {
    }

    /** 저장할 주차 값. DB CHECK가 1~5라 이 계산도 5를 넘지 않는다(31일 ÷ 7 = 5). */
    public static short of(LocalDate date) {
        return (short) ((date.getDayOfMonth() - 1) / 7 + 1);
    }

    /** "8월 2주". 이미 저장된 month·week가 있을 때 쓴다. */
    public static String label(short month, short week) {
        return month + "월 " + week + "주";
    }

    /** "8월 2주". 날짜에서 바로 만든다 — 클리닉처럼 주차를 저장하지 않는 쪽이 쓴다. */
    public static String label(LocalDate date) {
        return label((short) date.getMonthValue(), of(date));
    }

    /** 그 주차의 첫날. 2주차면 8일이다. */
    public static LocalDate startOf(int year, int month, int week) {
        return LocalDate.of(year, month, (week - 1) * 7 + 1);
    }

    /**
     * 그 주차의 마지막 날. 5주차는 달 길이에 따라 29~31일에서 끝나므로 잘라 준다 —
     * 자르지 않으면 LocalDate.of(2026, 2, 35)로 터진다.
     */
    public static LocalDate endOf(int year, int month, int week) {
        LocalDate start = startOf(year, month, week);
        return start.withDayOfMonth(Math.min(week * 7, start.lengthOfMonth()));
    }
}
