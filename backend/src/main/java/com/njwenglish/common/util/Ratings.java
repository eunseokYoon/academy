package com.njwenglish.common.util;

/**
 * 별점의 변환·검증 정본. <b>API는 0.5~5.0의 실수, DB는 SMALLINT 1~10이다.</b>
 *
 * <p>정수로 저장하는 이유는 반올림 사고를 없애기 위해서다. NUMERIC(2,1)이면
 * 4.5가 4.4999로 들어오는 경로가 생기고, 그게 평균을 조용히 어긋나게 한다.
 *
 * <p><b>이 변환을 서비스나 프론트에 복사하지 마라.</b> 두 곳에 생기면 반드시 갈라진다.
 */
public final class Ratings {

    /** 0은 "별점 안 줌"이 아니라 무효다. 별점은 필수다. */
    private static final double MIN = 0.5;
    private static final double MAX = 5.0;
    private static final double EPSILON = 1e-9;

    private Ratings() {
    }

    /** 범위 안이고 0.5의 배수인지. 4.3은 여기서 걸린다. */
    public static boolean isValid(double rating) {
        if (rating < MIN || rating > MAX) {
            return false;
        }
        double doubled = rating * 2;
        return Math.abs(doubled - Math.round(doubled)) < EPSILON;
    }

    public static short toStored(double rating) {
        return (short) Math.round(rating * 2);
    }

    public static double toDisplay(short stored) {
        return stored / 2.0;
    }

    /**
     * 저장값 평균을 화면 값으로. <b>입력이 null이면 null이다</b> —
     * 후기가 한 건도 없을 때 0.0을 내려주면 화면이 "별점 0점"으로 읽는다.
     */
    public static Double averageToDisplay(Double storedAverage) {
        if (storedAverage == null) {
            return null;
        }
        return Math.round(storedAverage / 2.0 * 10) / 10.0;
    }
}
