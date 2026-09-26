import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/router/auth_redirect.dart';
import 'package:academy_app/core/router/routes.dart';

void main() {
  test('탭은 역할당 정확히 다섯이다', () {
    // 여섯 번째를 넣으면 스크롤되고, 하단 바의 값어치인 위치 기억이 사라진다.
    expect(kStudentTabs.length, 5);
    expect(kParentTabs.length, 5);
  });

  test('학생 탭 순서가 웹과 같다', () {
    // 2026-09-10 에 순서를 바꿨다 — 테스트·출석이 레일로 내려가고 수업·질문이 올라왔다.
    expect(kStudentTabs.map((t) => t.label).toList(), [
      '홈',
      '숙제',
      '수업',
      '성적',
      '질문',
    ]);
    expect(kStudentTabs.map((t) => t.route).toList(), [
      AppRoutes.student,
      AppRoutes.studentHomeworks,
      AppRoutes.studentLessons,
      AppRoutes.studentScores,
      AppRoutes.studentQna,
    ]);
  });

  test('학부모 탭 순서가 웹과 같다', () {
    // 세 번째가 숙제가 아니라 주간 레포트다 — 학부모가 매주 여는 화면이 이쪽이다.
    expect(kParentTabs.map((t) => t.label).toList(), [
      '홈',
      '일정',
      '레포트',
      '성적',
      '내 정보',
    ]);
    expect(kParentTabs.map((t) => t.route).toList(), [
      AppRoutes.parent,
      AppRoutes.parentSchedule,
      AppRoutes.parentLessons,
      AppRoutes.parentScores,
      AppRoutes.parentMe,
    ]);
  });

  test('모든 탭 경로가 자기 역할 아래에 있다', () {
    // redirectFor 의 ready 분기가 `location == home || startsWith('$home/')` 로
    // 판정한다. 역할 밖 경로를 탭에 넣으면 그 탭이 눌리는 순간 홈으로 튕긴다.
    for (final t in kStudentTabs) {
      expect(
        t.route == AppRoutes.student ||
            t.route.startsWith('${AppRoutes.student}/'),
        isTrue,
        reason: '${t.route} 가 /student 밖이다',
      );
    }
    for (final t in kParentTabs) {
      expect(
        t.route == AppRoutes.parent ||
            t.route.startsWith('${AppRoutes.parent}/'),
        isTrue,
        reason: '${t.route} 가 /parent 밖이다',
      );
    }
  });

  test('경로가 중복되지 않는다', () {
    final all = [...kStudentTabs, ...kParentTabs].map((t) => t.route).toList();
    expect(all.toSet().length, all.length);
  });
}
