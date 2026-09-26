import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';

/// 여러 줄 입력. 웹 `TextAreaField.tsx` — 라벨 위, 입력 아래.
///
/// **[AppTextField] 에 `maxLines` 를 더하지 않고 따로 둔 이유** — 그쪽 매개변수
/// 목록은 의도적으로 고정이다(CLAUDE.md 13-3). 사유·질문·답글처럼 여러 줄을
/// 받는 칸은 이것을 쓴다. 모양(테두리·포커스 링·글자 16)은 그쪽과 같다.
///
/// [onChanged] 는 부모가 「비었나」로 버튼을 켜고 끄는 데 쓴다.
class AppTextArea extends StatefulWidget {
  const AppTextArea({
    super.key,
    required this.label,
    required this.controller,
    this.placeholder,
    this.minLines = 3,
    this.maxLength,
    this.onChanged,
  });

  /// null 이면 라벨 줄을 안 그린다(웹의 placeholder 만 있는 칸).
  final String? label;
  final TextEditingController controller;
  final String? placeholder;
  final int minLines;

  /// 서버 `@Size(max = …)` 와 같은 값. 넘기면 입력이 막힌다.
  final int? maxLength;
  final ValueChanged<String>? onChanged;

  @override
  State<AppTextArea> createState() => _AppTextAreaState();
}

class _AppTextAreaState extends State<AppTextArea> {
  final _focus = FocusNode();
  bool _focused = false;

  @override
  void initState() {
    super.initState();
    _focus.addListener(_onFocusChange);
  }

  void _onFocusChange() {
    if (!mounted) return;
    setState(() => _focused = _focus.hasFocus);
  }

  @override
  void dispose() {
    _focus.removeListener(_onFocusChange);
    _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final border = OutlineInputBorder(
      borderRadius: BorderRadius.circular(AppRadii.xl),
      borderSide: const BorderSide(color: AppColors.slate300),
    );
    final label = widget.label;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (label != null) ...[
          Text(
            label,
            style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w500,
              color: AppColors.slate700,
            ),
          ),
          const SizedBox(height: 6),
        ],
        DecoratedBox(
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppRadii.xl),
            boxShadow: _focused
                ? [
                    BoxShadow(
                      color: AppColors.brand600.withValues(alpha: 0.15),
                      blurRadius: 0,
                      spreadRadius: 4,
                    ),
                  ]
                : const [],
          ),
          child: TextField(
            controller: widget.controller,
            focusNode: _focus,
            minLines: widget.minLines,
            maxLines: null,
            maxLength: widget.maxLength,
            keyboardType: TextInputType.multiline,
            onChanged: widget.onChanged,
            // 16 미만이면 iOS 가 포커스 때 화면을 확대한다(13-4).
            style: const TextStyle(fontSize: 16, color: AppColors.slate900),
            decoration: InputDecoration(
              filled: true,
              fillColor: Colors.white,
              hintText: widget.placeholder,
              hintStyle: const TextStyle(color: AppColors.slate400),
              // 글자 수 표시는 안 그린다 — 상한은 막는 용도다.
              counterText: '',
              contentPadding: const EdgeInsets.symmetric(
                horizontal: 12,
                vertical: 10,
              ),
              enabledBorder: border,
              focusedBorder: border.copyWith(
                borderSide: const BorderSide(color: AppColors.brand600),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
