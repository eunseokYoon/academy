import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import 'app_bar_band.dart';
import 'home_layout.dart';

/// 홈이 아닌 화면(숙제·수업·성적…)의 틀. 웹 `StudentLayout` 의 `<AppBar>` +
/// `main.p-4` 다.
///
/// **앱바가 스크롤 맨 위에 있다** — 셸은 앱바를 안 그린다(`RoleShell` 주석,
/// CLAUDE.md 14-9). 홈과 달리 겹침(`-40`)이 없어서 [Transform] 도 없고,
/// [ListView] 의 자식으로 펴도 히트 테스트가 안 깨진다.
///
/// 끝 여백은 탭 바 높이(`Scaffold` 가 넘기는 아래 여백) + [kHomeTailGap] 이다 —
/// 홈과 같은 틈을 두되, 끌어올린 양이 없으니 빼지 않는다. **고정값을 넣지 마라.**
///
/// [onRefresh] 가 있으면 당겨서 새로고침이 붙는다. 목록이 짧아도 당겨지도록
/// 항상 스크롤 가능하게 둔다.
class SubPageScroll extends StatelessWidget {
  const SubPageScroll({
    super.key,
    required this.role,
    required this.children,
    this.onRefresh,
  });

  /// 앱바 칩. 「학생」 · 「학부모」
  final String role;
  final List<Widget> children;
  final Future<void> Function()? onRefresh;

  @override
  Widget build(BuildContext context) {
    final list = ListView(
      padding: EdgeInsets.zero,
      physics: const AlwaysScrollableScrollPhysics(),
      children: [
        AppBarBand(role: role),
        // 웹 `main` 의 `p-4` 윗변.
        const SizedBox(height: 16),
        for (final child in children) homeConstrain(child),
        SizedBox(height: MediaQuery.paddingOf(context).bottom + kHomeTailGap),
      ],
    );
    final onRefresh = this.onRefresh;
    return onRefresh == null
        ? list
        : RefreshIndicator(onRefresh: onRefresh, child: list);
  }
}

/// 하위 화면의 제목. 웹 `Section.tsx` 의 `PageTitle`.
///
/// **남색 띠 아래, 밝은 면 위에 앉는다**(웹 2026-08-18 확정 — 띠 안에 끌어
/// 올렸더니 2px 삐져나와 경계에 걸쳤다). 그래서 음수 여백이 없다.
///
/// [action] 은 오른쪽 자리다. 밝은 면 위라 남색 계열로 그려라.
class PageTitle extends StatelessWidget {
  const PageTitle({super.key, required this.title, this.action});

  final String title;
  final Widget? action;

  @override
  Widget build(BuildContext context) {
    final action = this.action;
    return Padding(
      // 웹 `mb-4 min-h-[26px]`.
      padding: const EdgeInsets.only(bottom: 16),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 26),
        child: Row(
          children: [
            Expanded(
              child: Text(
                title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 19,
                  fontWeight: FontWeight.w800,
                  // 웹 `tracking-[-0.03em]` × 19px.
                  letterSpacing: -0.57,
                  color: AppColors.brand900,
                ),
              ),
            ),
            if (action != null) ...[const SizedBox(width: 12), action],
          ],
        ),
      ),
    );
  }
}

/// 상세 화면의 「← 목록」 줄. 웹 `Section.tsx` 의 `BackLink` 이고 [PageTitle]
/// 과 같은 자리에 앉는다.
///
/// **이름이 `BackLink` 가 아닌 이유** — `legal.dart` 의 `BackLink`(약관의
/// 「로그인으로 돌아가기」)와 다른 물건이다(B1 설계 14번).
///
/// **색이 brand600 인 이유.** 제목과 같은 brand900 으로 두면 제목처럼 읽혀서
/// 눌러서 이동하는 곳으로 안 보인다. 이 앱에서 파랑은 「누르면 간다」다.
class PageBackLink extends StatelessWidget {
  const PageBackLink({super.key, required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Align(
        alignment: Alignment.centerLeft,
        child: InkWell(
          key: const Key('page-back'),
          onTap: onTap,
          borderRadius: BorderRadius.circular(8),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
            child: Text(
              '← $label',
              style: const TextStyle(
                fontSize: 13,
                fontWeight: FontWeight.w600,
                color: AppColors.brand600,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 회색 블록 안의 안내 한 줄(「받은 숙제가 없습니다」). 웹 `px-4 py-6
/// text-center text-sm text-slate-500`.
class EmptyNote extends StatelessWidget {
  const EmptyNote({super.key, required this.text});

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
    child: Text(
      text,
      textAlign: TextAlign.center,
      style: const TextStyle(fontSize: 14, color: AppColors.slate500),
    ),
  );
}
