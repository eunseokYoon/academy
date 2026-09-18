import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';

/// 웹 `TextField.tsx` 와 같은 3단 구조 — 라벨 위, 입력, 힌트 아래.
///
/// **placeholder 만 쓰지 마라.** 라벨이 입력칸 위에 따로 있어야 입력을 시작한
/// 뒤에도 그 칸이 무엇인지 알 수 있다. A단계의 세 화면이 placeholder 만 썼다.
///
/// 입력 글자는 **16 이상**이다. 웹 주석의 이유가 그대로 적용된다 —
/// 그보다 작으면 iOS 에서 입력할 때 화면이 확대된다.
///
/// 포커스 링은 웹의 `focus:ring-4 ring-brand-600/15` 다.
/// `InputDecoration` 의 `focusedBorder` 는 두께만 있고 **바깥 확산이 없어서**
/// [DecoratedBox] 의 `BoxShadow` 를 겹쳐 얹었다(`spreadRadius: 4`,
/// `blurRadius: 0`). 실기기에서 렌더를 확인한 구조다 — 바꾸지 마라.
class AppTextField extends StatefulWidget {
  const AppTextField({
    super.key,
    required this.label,
    required this.controller,
    this.hint,
    this.placeholder,
    this.obscureText = false,
    this.keyboardType,
    this.inputFormatters,
    this.textCapitalization = TextCapitalization.none,
    this.onSubmitted,
  });

  final String label;
  final TextEditingController controller;

  /// 입력칸 아래 회색 설명. 없으면 그 자리를 만들지 않는다.
  final String? hint;

  /// 입력칸 안 회색 예시.
  final String? placeholder;

  final bool obscureText;
  final TextInputType? keyboardType;
  final List<TextInputFormatter>? inputFormatters;
  final TextCapitalization textCapitalization;
  final ValueChanged<String>? onSubmitted;

  @override
  State<AppTextField> createState() => _AppTextFieldState();
}

class _AppTextFieldState extends State<AppTextField> {
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

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          widget.label,
          style: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w500,
            color: AppColors.slate700,
          ),
        ),
        const SizedBox(height: 6),
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
            obscureText: widget.obscureText,
            keyboardType: widget.keyboardType,
            inputFormatters: widget.inputFormatters,
            textCapitalization: widget.textCapitalization,
            onSubmitted: widget.onSubmitted,
            style: const TextStyle(fontSize: 16, color: AppColors.slate900),
            decoration: InputDecoration(
              filled: true,
              fillColor: Colors.white,
              hintText: widget.placeholder,
              hintStyle: const TextStyle(color: AppColors.slate400),
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
        if (widget.hint != null) ...[
          const SizedBox(height: 4),
          Text(
            widget.hint!,
            style: const TextStyle(fontSize: 12, color: AppColors.slate500),
          ),
        ],
      ],
    );
  }
}
