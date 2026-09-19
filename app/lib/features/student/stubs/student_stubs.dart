import 'package:flutter/material.dart';

import '../../../shared/widgets/stub_page.dart';

/// B2 가 만든다.
class StudentHomeworksStub extends StatelessWidget {
  const StudentHomeworksStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(title: '숙제', stage: 'B2');
}

/// B3 가 만든다.
class StudentLessonsStub extends StatelessWidget {
  const StudentLessonsStub({super.key});

  @override
  Widget build(BuildContext context) =>
      const StubPage(title: '수업', stage: 'B3');
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
      const StubPage(title: '질문', stage: 'B4');
}
