/**
 * 앱바 워드마크에 들어가는 이름. 2026-08-11 확정.
 *
 * <p>로고와 같은 잠금이라 두 조각으로 나눠 둔다 — 한글은 흰색, LAB만 주황이다.
 * 화면에 그릴 때는 {@link Wordmark}가 조립하고, 읽어 주는 기계에는
 * ACADEMY_NAME 하나로 나간다(sr-only). 붙여 쓰는 이유는 상호가 "남지원영어LAB"이라
 * 사이에 공백이 없기 때문이다.
 */
export const ACADEMY_NAME_HEAD = "남지원영어";
export const ACADEMY_NAME_TAIL = "LAB";

export const ACADEMY_NAME = `${ACADEMY_NAME_HEAD}${ACADEMY_NAME_TAIL}`;
