import 'package:flutter/material.dart';

import '../../../shared/widgets/stub_page.dart';

/// B4 가 만든다.
class StudentQnaStub extends StatelessWidget {
  const StudentQnaStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(role: '학생', title: '질문', stage: 'B4');
}

// ── 홈 퀵 레일이 가는 곳 넷 중 B4 몫 둘(출석·공지는 B3 가 실물로 바꿨다) ──
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
