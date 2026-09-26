import 'package:flutter/material.dart';

import '../../../shared/widgets/stub_page.dart';

/// B3 가 만든다.
class StudentLessonsStub extends StatelessWidget {
  const StudentLessonsStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(role: '학생', title: '수업', stage: 'B3');
}

/// B3 가 만든다.
///
/// **제목이 「내 정보 · 성적」이다.** 탭 라벨만 「성적」이고, 들어가면 내 정보와
/// 로그아웃이 보여야 공용 PC 나 형제 폰에서 계정을 내려놓을 수 있다.
/// **로그아웃을 지우지 마라** — 실물로 바꿀 때도 그대로 들고 가야 한다.
class StudentScoresStub extends StatelessWidget {
  const StudentScoresStub({super.key, required this.onLogout});

  final Future<void> Function() onLogout;

  @override
  Widget build(BuildContext context) {
    return StubPage(
      role: '학생',
      title: '내 정보 · 성적',
      stage: 'B3',
      bottom: TextButton(
        key: const Key('student-logout'),
        onPressed: onLogout,
        child: const Text('로그아웃'),
      ),
    );
  }
}

/// B4 가 만든다.
class StudentQnaStub extends StatelessWidget {
  const StudentQnaStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(role: '학생', title: '질문', stage: 'B4');
}

// ── 홈 퀵 레일이 가는 곳 넷 ──────────────────────────────────
// 탭 다섯 밖이라 스텁이 없었고, 레일을 누르면 go_router 오류 화면이 떴다.
// **라우트는 셸 안(학생 브랜치 0의 자식)이다** — 하단 탭 바가 그대로 있어야
// 한다. 웹도 이 화면들이 학생 레이아웃 안에 있다.

/// B4 가 만든다. 레일 라벨은 「스케줄」이고 경로는 `/student/clinics` 다 —
/// 클리닉이 학생에게는 「스케줄」로 보인다(웹 QUICK_ITEMS 와 같다).
class StudentClinicsStub extends StatelessWidget {
  const StudentClinicsStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(role: '학생', title: '스케줄', stage: 'B4');
}

/// B4 가 만든다.
class StudentOnlineTestsStub extends StatelessWidget {
  const StudentOnlineTestsStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(role: '학생', title: '테스트', stage: 'B4');
}

/// B3 가 만든다.
class StudentAttendancesStub extends StatelessWidget {
  const StudentAttendancesStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(role: '학생', title: '출석', stage: 'B3');
}

/// B3 가 만든다.
class StudentNoticesStub extends StatelessWidget {
  const StudentNoticesStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(role: '학생', title: '공지', stage: 'B3');
}
