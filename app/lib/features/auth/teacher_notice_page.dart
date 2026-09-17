import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';

/// 선생님은 앱 대상이 아니다(스펙 확정 사항 3).
///
/// 이유는 화면 수가 아니라 입력 형태다 — 선생님 화면의 중심은 반 × 주차 성적
/// 그리드와 반 × 수업일 숙제 그리드이고, 가로로 넓은 표에 값을 찍는 작업이다.
/// 강사가 1명이고 책상에서 한다.
///
/// 그래도 **로그인은 성공한다.** 빈 화면을 주면 앱이 고장난 것으로 보이므로
/// 안내로 받는다.
class TeacherNoticePage extends StatelessWidget {
  const TeacherNoticePage({super.key, required this.onLogout});

  final Future<void> Function() onLogout;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text(
                  '선생님은 웹에서 이용해 주세요.',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w700,
                    color: AppColors.brand900,
                  ),
                ),
                const SizedBox(height: 12),
                const Text(
                  '성적·숙제 그리드는 넓은 화면에서 훨씬 빠릅니다.\n'
                  '이 앱은 학생과 학부모용입니다.',
                  textAlign: TextAlign.center,
                  style: TextStyle(height: 1.5, color: Color(0xFF475569)),
                ),
                const SizedBox(height: 28),
                TextButton(
                  key: const Key('teacher-logout'),
                  onPressed: onLogout,
                  child: const Text('로그아웃'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
