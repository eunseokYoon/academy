import '../../shared/icons/icon_paths.dart';
import 'auth_redirect.dart';

/// 하단 탭 하나. 아이콘은 [AppIconName] 이고 라벨은 웹과 같다.
class BottomTab {
  const BottomTab({
    required this.route,
    required this.icon,
    required this.label,
  });

  final String route;
  final AppIconName icon;
  final String label;
}

/// 학생 하단 탭 다섯. **여섯 번째를 넣지 마라** — 넣는 순간 스크롤이 필요해지고,
/// 하단 바의 값어치인 「숙제는 항상 왼쪽에서 두 번째」라는 위치 기억이 사라진다.
/// 넘치는 것은 홈의 QuickRail 이 받는다.
///
/// `/student/scores` 는 실제로는 「내 정보 · 성적」이고 **학생의 유일한 로그아웃
/// 경로**다. 탭 라벨만 「성적」이고 **화면 제목은 「내 정보 · 성적」을 유지한다.**
const List<BottomTab> kStudentTabs = [
  BottomTab(route: AppRoutes.student, icon: AppIconName.home, label: '홈'),
  BottomTab(
    route: AppRoutes.studentHomeworks,
    icon: AppIconName.homework,
    label: '숙제',
  ),
  BottomTab(
    route: AppRoutes.studentLessons,
    icon: AppIconName.video,
    label: '수업',
  ),
  BottomTab(
    route: AppRoutes.studentScores,
    icon: AppIconName.chart,
    label: '성적',
  ),
  BottomTab(
    route: AppRoutes.studentQna,
    icon: AppIconName.question,
    label: '질문',
  ),
];

/// 학부모 하단 탭 다섯. 세 번째가 숙제가 아니라 주간 레포트인 이유 — 레포트가
/// 그 주 수업·테스트·숙제를 한 장에 담아서 학부모가 매주 여는 화면이 이쪽이다.
/// 숙제만 따로 보는 것은 레일에 남아 있고 미완료 개수 점도 거기 붙는다.
const List<BottomTab> kParentTabs = [
  BottomTab(route: AppRoutes.parent, icon: AppIconName.home, label: '홈'),
  BottomTab(
    route: AppRoutes.parentSchedule,
    icon: AppIconName.calendar,
    label: '일정',
  ),
  BottomTab(
    route: AppRoutes.parentLessons,
    icon: AppIconName.book,
    label: '레포트',
  ),
  BottomTab(
    route: AppRoutes.parentScores,
    icon: AppIconName.chart,
    label: '성적',
  ),
  BottomTab(route: AppRoutes.parentMe, icon: AppIconName.user, label: '내 정보'),
];
