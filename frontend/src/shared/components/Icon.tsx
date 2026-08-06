/**
 * 선 아이콘. 홈 타일과 모달 닫기 버튼이 쓴다.
 *
 * <p>참고 디자인은 이모지를 썼지만 이모지는 안드로이드·iOS·윈도우에서 각각 다르게 그려지고
 * 크기·정렬도 제각각이라 격자가 흐트러진다. 여기서는 같은 굵기로 통일한 SVG를 쓴다.
 * 아이콘 라이브러리를 새로 넣을 만한 일이 아니라 필요한 것만 직접 둔다.
 */
const PATHS = {
  homework: "M4 5.5A1.5 1.5 0 015.5 4H14l6 6v8.5a1.5 1.5 0 01-1.5 1.5h-13A1.5 1.5 0 014 18.5zM14 4v6h6",
  video: "M3 6.5A1.5 1.5 0 014.5 5h10A1.5 1.5 0 0116 6.5v11a1.5 1.5 0 01-1.5 1.5h-10A1.5 1.5 0 013 17.5zM16 10l5-3v10l-5-3",
  folder: "M3 6.5A1.5 1.5 0 014.5 5h4l2 2.5h7A1.5 1.5 0 0119 9v8.5a1.5 1.5 0 01-1.5 1.5h-13A1.5 1.5 0 013 17.5z",
  test: "M7 4h10a1 1 0 011 1v14a1 1 0 01-1 1H7a1 1 0 01-1-1V5a1 1 0 011-1zM9 9h6M9 13h6M9 17h3",
  calendar: "M4 7.5A1.5 1.5 0 015.5 6h13A1.5 1.5 0 0120 7.5v11a1.5 1.5 0 01-1.5 1.5h-13A1.5 1.5 0 014 18.5zM4 11h16M8 4v4M16 4v4",
  clock: "M12 21a9 9 0 100-18 9 9 0 000 18zM12 7.5V12l3 2",
  megaphone: "M4 10v4a1 1 0 001 1h2l3.5 4V5L7 9H5a1 1 0 00-1 1zM15 8.5a5 5 0 010 7",
  chart: "M4 20V10M10 20V4M16 20v-7M22 20H2",
  close: "M6 6l12 12M18 6l-12 12",
  user: "M12 12a4 4 0 100-8 4 4 0 000 8zM4 20a8 8 0 0116 0",
} as const;

export type IconName = keyof typeof PATHS;

export function Icon({ name, className = "h-5 w-5" }: { name: IconName; className?: string }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className={className}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d={PATHS[name]} />
    </svg>
  );
}
