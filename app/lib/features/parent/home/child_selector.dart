import 'package:flutter/material.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../selected_child.dart';

/// 학부모 홈 지면 안의 자녀 선택. 웹 `ParentHomePage.tsx` 의 `HeroField`
/// children 블록이 정본이다(하위 화면 제목 줄의 `ChildSelect.tsx` 는 다른
/// 모양이고 B2~B3 것이다).
///
/// **지면 안에 두는 이유**(웹 주석): 이걸 바꾸면 아래 화면 전체가 다른 아이
/// 것으로 바뀌기 때문이다 — 카드 하나의 설정이 아니다.
///
/// **자녀가 하나면 아무것도 그리지 않는다.** 고를 게 없는 선택지는 화면만
/// 어지럽힌다.
///
/// 남색 지면 위라 **흰 계열로 그린다** — 기본 회색 테두리를 두면 안 보인다.
/// 반대로 **펼친 목록은 흰 바탕에 `brand900`** 이다. 웹의 「옵션 목록은 OS 가
/// 그리므로 글자색을 되돌려 놔야 한다」에 대응한다 — 항목까지 흰 글씨면 흰
/// 목록 위에서 안 보인다.
class ChildSelector extends StatelessWidget {
  const ChildSelector({
    super.key,
    required this.children,
    required this.selectedStudentId,
    required this.onSelect,
  });

  final List<Child> children;
  final int? selectedStudentId;
  final ValueChanged<int> onSelect;

  /// 글자 크기. **16 미만으로 줄이지 마라** — iOS 가 포커스 시 뷰포트를
  /// 확대한다(CLAUDE.md 13-4). 웹도 `text-base`(16) 다.
  static const double fontSize = 16;

  @override
  Widget build(BuildContext context) {
    if (children.length <= 1) return const SizedBox.shrink();

    // 목록에 없는 값을 value 로 주면 DropdownButton 이 assert 로 죽는다.
    final selected = children.any((c) => c.studentId == selectedStudentId)
        ? selectedStudentId
        : null;

    return Container(
      key: const Key('child-selector'),
      // 웹 `mt-4 border-t border-white/10 pt-3.5`.
      margin: const EdgeInsets.only(top: 16),
      padding: const EdgeInsets.only(top: 14),
      decoration: BoxDecoration(
        border: Border(
          top: BorderSide(color: Colors.white.withValues(alpha: 0.10)),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            '자녀 선택',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              // 웹 `tracking-[0.1em]` × 11px.
              letterSpacing: 1.1,
              color: AppColors.brand200.withValues(alpha: 0.7),
            ),
          ),
          // 웹 `mt-1.5`.
          const SizedBox(height: 6),
          Container(
            // 웹 `rounded-xl border border-white/15 bg-white/10 px-3`.
            padding: const EdgeInsets.symmetric(horizontal: 12),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.10),
              borderRadius: BorderRadius.circular(AppRadii.xl),
              border: Border.all(color: Colors.white.withValues(alpha: 0.15)),
            ),
            child: DropdownButtonHideUnderline(
              child: DropdownButton<int>(
                key: const Key('child-dropdown'),
                value: selected,
                isExpanded: true,
                // 펼친 목록: 흰 바탕 + brand900.
                dropdownColor: Colors.white,
                borderRadius: BorderRadius.circular(AppRadii.xl),
                iconEnabledColor: Colors.white,
                style: const TextStyle(
                  fontSize: fontSize,
                  color: AppColors.brand900,
                ),
                // 닫힌 버튼 안의 글자만 흰색이다.
                selectedItemBuilder: (_) => [
                  for (final child in children)
                    Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        child.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          fontSize: fontSize,
                          color: Colors.white,
                        ),
                      ),
                    ),
                ],
                items: [
                  for (final child in children)
                    DropdownMenuItem<int>(
                      value: child.studentId,
                      child: Text(
                        child.name,
                        style: const TextStyle(
                          fontSize: fontSize,
                          color: AppColors.brand900,
                        ),
                      ),
                    ),
                ],
                onChanged: (id) {
                  if (id != null) onSelect(id);
                },
              ),
            ),
          ),
        ],
      ),
    );
  }
}
