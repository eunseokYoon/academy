import 'package:flutter/material.dart';

/// C단계가 채운다. 화면은 `홈 / 스케줄 / 숙제 / 레포트 / 성적 / 공지 / 내정보` 일곱이다.
class ParentShell extends StatelessWidget {
  const ParentShell({super.key, required this.name, required this.onLogout});

  final String name;
  final Future<void> Function() onLogout;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('학부모')),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('$name 님, 로그인됐습니다.'),
            const SizedBox(height: 8),
            const Text('화면은 C단계에서 만듭니다.'),
            const SizedBox(height: 24),
            TextButton(
              key: const Key('parent-logout'),
              onPressed: onLogout,
              child: const Text('로그아웃'),
            ),
          ],
        ),
      ),
    );
  }
}
