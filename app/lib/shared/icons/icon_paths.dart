/// 웹 `frontend/src/shared/components/Icon.tsx` 의 `PATHS` 를 그대로 옮긴 것이다.
///
/// **값을 여기서 고치지 마라.** 정본은 웹이고, 웹이 아이콘을 바꾸면 이 문자열만
/// 갈아 끼운다. 참고 디자인은 이모지를 썼지만 이모지는 플랫폼마다 다르게 그려지고
/// 크기·정렬이 제각각이라 격자가 흐트러진다.
///
/// **Material 아이콘으로 바꾸지 마라.** `book` 은 같은 레일의 `homework` 와,
/// `question` 은 `megaphone` 과 구분되려고 일부러 그 모양이다 — 웹 주석이 그렇게 적혀 있다.
enum AppIconName {
  homework,
  video,
  folder,
  test,
  calendar,
  clock,
  megaphone,
  chart,
  close,
  user,
  home,
  book,
  question,
}

const Map<AppIconName, String> kIconPaths = {
  AppIconName.homework: 'M4 5.5A1.5 1.5 0 015.5 4H14l6 6v8.5a1.5 1.5 0 01-1.5 1.5h-13A1.5 1.5 0 014 18.5zM14 4v6h6',
  AppIconName.video: 'M3 6.5A1.5 1.5 0 014.5 5h10A1.5 1.5 0 0116 6.5v11a1.5 1.5 0 01-1.5 1.5h-10A1.5 1.5 0 013 17.5zM16 10l5-3v10l-5-3',
  AppIconName.folder: 'M3 6.5A1.5 1.5 0 014.5 5h4l2 2.5h7A1.5 1.5 0 0119 9v8.5a1.5 1.5 0 01-1.5 1.5h-13A1.5 1.5 0 013 17.5z',
  AppIconName.test: 'M7 4h10a1 1 0 011 1v14a1 1 0 01-1 1H7a1 1 0 01-1-1V5a1 1 0 011-1zM9 9h6M9 13h6M9 17h3',
  AppIconName.calendar: 'M4 7.5A1.5 1.5 0 015.5 6h13A1.5 1.5 0 0120 7.5v11a1.5 1.5 0 01-1.5 1.5h-13A1.5 1.5 0 014 18.5zM4 11h16M8 4v4M16 4v4',
  AppIconName.clock: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 7.5V12l3 2',
  AppIconName.megaphone:
      'M4 10v4a1 1 0 001 1h2l3.5 4V5L7 9H5a1 1 0 00-1 1zM15 8.5a5 5 0 010 7',
  AppIconName.chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  AppIconName.close: 'M6 6l12 12M18 6l-12 12',
  AppIconName.user: 'M12 12a4 4 0 100-8 4 4 0 000 8zM4 20a8 8 0 0116 0',

  /// 하단 탭 바의 홈. 지붕과 문이 있어야 다른 아이콘과 안 헷갈린다
  AppIconName.home:
      'M4 10.5L12 4l8 6.5V19a1 1 0 01-1 1h-4v-6h-6v6H5a1 1 0 01-1-1z',

  /// 수업 레포트. 숙제와 같은 레일에 서므로 문서가 아니라 책이어야 구분된다
  AppIconName.book: 'M4 5.5A1.5 1.5 0 015.5 4H11v16H5.5A1.5 1.5 0 014 18.5zM20 5.5A1.5 1.5 0 0018.5 4H13v16h5.5a1.5 1.5 0 001.5-1.5z',

  /// 질의응답. 공지와 같은 레일에 서므로 확성기가 아니라 말풍선이어야 구분된다
  AppIconName.question: 'M4 6.5A1.5 1.5 0 015.5 5h13A1.5 1.5 0 0120 6.5v8a1.5 1.5 0 01-1.5 1.5H9l-5 4z',
};
