import 'package:flutter/material.dart';

import '../../../shared/widgets/stub_page.dart';

/// B3 가 만든다.
class ParentScheduleStub extends StatelessWidget {
  const ParentScheduleStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(title: '일정', stage: 'B3');
}

/// B3 가 만든다. 주간 레포트는 그 주 수업·테스트·숙제를 한 장에 담는다.
class ParentLessonsStub extends StatelessWidget {
  const ParentLessonsStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(title: '주간 레포트', stage: 'B3');
}

/// B3 가 만든다.
class ParentScoresStub extends StatelessWidget {
  const ParentScoresStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(title: '성적', stage: 'B3');
}

/// B3 가 만든다.
///
/// **로그아웃을 지우지 마라** — 웹이 학부모 로그아웃을 두는 자리이고,
/// 실물로 바꿀 때도 그대로 들고 가야 한다.
class ParentMeStub extends StatelessWidget {
  const ParentMeStub({super.key, required this.onLogout});

  final Future<void> Function() onLogout;

  @override
  Widget build(BuildContext context) {
    return StubPage(
      title: '내 정보',
      stage: 'B3',
      bottom: TextButton(
        key: const Key('parent-logout'),
        onPressed: onLogout,
        child: const Text('로그아웃'),
      ),
    );
  }
}

// ── 홈 퀵 레일이 가는 곳 둘 ──────────────────────────────────
// 탭 다섯 밖이라 스텁이 없었고, 레일을 누르면 go_router 오류 화면이 떴다.
// **라우트는 셸 안(학부모 브랜치 0의 자식)이다** — 하단 탭 바가 그대로
// 있어야 한다. 학생 쪽 넷(student_stubs.dart)과 같은 방식이다.

/// B2 가 만든다.
class ParentHomeworksStub extends StatelessWidget {
  const ParentHomeworksStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(title: '숙제', stage: 'B2');
}

/// B3 가 만든다.
class ParentNoticesStub extends StatelessWidget {
  const ParentNoticesStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(title: '공지', stage: 'B3');
}
