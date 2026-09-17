import 'package:flutter/material.dart';

/// B단계가 채운다. 하단 탭은 `홈 / 숙제 / 수업 / 성적 / 질문`,
/// 상단 레일은 `숙제 / 수업 / 스케쥴 / 성적 / 테스트 / 출석 / 공지 / 질문`이다
/// (2026-09-10 회의 확정). **여기서 탭 구성을 발명하지 마라.**
class StudentShell extends StatelessWidget {
  const StudentShell({super.key, required this.name, required this.onLogout});

  final String name;
  final Future<void> Function() onLogout;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('학생')),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('$name 님, 로그인됐습니다.'),
            const SizedBox(height: 8),
            const Text('화면은 B단계에서 만듭니다.'),
            const SizedBox(height: 24),
            TextButton(
              key: const Key('student-logout'),
              onPressed: onLogout,
              child: const Text('로그아웃'),
            ),
          ],
        ),
      ),
    );
  }
}
