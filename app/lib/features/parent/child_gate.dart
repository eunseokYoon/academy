import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';
import '../../shared/widgets/full_screen_loader.dart';
import '../../shared/widgets/home_layout.dart';
import '../../shared/widgets/section.dart';
import '../../shared/widgets/sub_page.dart';
import 'selected_child.dart';

/// 학부모 하위 화면이 자녀를 기다리는 동안 그릴 것. 자녀가 골라져 있으면 null 이다.
///
/// 순서가 뜻을 갖는다 — 목록을 못 받았으면 오류와 「다시 시도」, 받았는데
/// 0명이면 안내, 아직 받는 중이면 로더. 오류를 로더보다 먼저 보지 않으면
/// 망이 끊긴 학부모가 로더만 영영 본다.
///
/// 화면은 `initState` 에서 [SelectedChild.ensureLoaded] 를 부르고, 빌드에서
/// 이것이 null 일 때만 자기 목록을 그린다.
Widget? childGate(SelectedChild sc) {
  if (sc.selectedStudentId != null) return null;
  final error = sc.loadError;
  if (error != null) {
    return HomeErrorView(
      key: const Key('children-error'),
      message: error,
      // 실패는 loadError 로 다시 남는다. 여기서 받지 않으면 처리 안 된 오류다.
      onRetry: () => sc.load().catchError((Object _) {}),
    );
  }
  if (sc.loaded && sc.children.isEmpty) {
    return const TintBlock(
      tone: SectionTone.neutral,
      children: [EmptyNote(text: '연결된 자녀가 없습니다.')],
    );
  }
  return const SizedBox(height: 160, child: FullScreenLoader());
}

/// 하위 화면 제목 줄의 자녀 선택. 웹 `ChildSelect.tsx` 의 자리다.
///
/// **밝은 면 위에 그린다.** 웹은 흰 글씨(`text-white border-white/25`)인데, 그
/// 주석이 전제한 「남색 띠 안」은 `PageTitle` 이 밝은 면으로 내려오면서
/// (2026-08-18) 사라졌다 — 그대로 옮기면 흰 바탕에 흰 글씨라 안 보인다.
/// 홈 지면의 `ChildSelector` 와는 다른 물건이다.
///
/// 자녀가 하나면 아무것도 그리지 않는다.
class TitleChildSelect extends StatelessWidget {
  const TitleChildSelect({
    super.key,
    required this.children,
    required this.selectedStudentId,
    required this.onSelect,
  });

  final List<Child> children;
  final int? selectedStudentId;
  final ValueChanged<int> onSelect;

  @override
  Widget build(BuildContext context) {
    if (children.length <= 1) return const SizedBox.shrink();
    // 목록에 없는 값을 value 로 주면 DropdownButton 이 assert 로 죽는다.
    final selected = children.any((c) => c.studentId == selectedStudentId)
        ? selectedStudentId
        : null;
    return Container(
      key: const Key('title-child-select'),
      padding: const EdgeInsets.symmetric(horizontal: 8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(AppRadii.lg),
        border: Border.all(color: AppColors.slate300),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<int>(
          value: selected,
          isDense: true,
          style: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w500,
            color: AppColors.brand900,
          ),
          items: [
            for (final child in children)
              DropdownMenuItem<int>(
                value: child.studentId,
                child: Text(child.name),
              ),
          ],
          onChanged: (id) {
            if (id != null) onSelect(id);
          },
        ),
      ),
    );
  }
}
