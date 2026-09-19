import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import 'app_card.dart';

/// B2~B4 가 실물로 교체할 자리표시자.
///
/// **탭 다섯이 모두 눌려야 한다.** 하단 바의 값어치는 「숙제는 항상 왼쪽에서
/// 두 번째」라는 위치 기억인데, 절반이 비활성이면 그게 안 생긴다.
///
/// [bottom] 은 학생 성적·학부모 내 정보 스텁이 **로그아웃을** 넣는 자리다.
/// **교체할 때 로그아웃을 같이 지우지 마라** — 웹은 그 두 화면에 로그아웃을
/// 두고, 실물이 오기 전까지 여기가 앱의 유일한 경로다.
class StubPage extends StatelessWidget {
  const StubPage({
    super.key,
    required this.title,
    required this.stage,
    this.bottom,
  });

  final String title;

  /// 「B2」·「B3」처럼 어느 단계가 만드는지.
  final String stage;

  final Widget? bottom;

  @override
  Widget build(BuildContext context) {
    final bottom = this.bottom;
    return Scaffold(
      backgroundColor: AppColors.paper,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 384),
              child: AppCard(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.w600,
                        color: AppColors.slate900,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '이 화면은 $stage 단계에서 만듭니다.',
                      style: const TextStyle(
                        fontSize: 14,
                        color: AppColors.slate500,
                      ),
                    ),
                    if (bottom != null) ...[
                      const SizedBox(height: 24),
                      KeyedSubtree(
                        key: const Key('stub-bottom'),
                        child: bottom,
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
