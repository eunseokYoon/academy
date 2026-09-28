import 'package:flutter/material.dart';
import 'package:path_drawing/path_drawing.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';

/// 별 하나. 웹 `StarRating.tsx` 의 `STAR_PATH` 그대로다(viewBox 24×24, 채운 별).
const String _kStarPath =
    'M12 2.5l2.95 5.98 6.6.96-4.78 4.66 1.13 6.58L12 17.77l-5.9 3.1 '
    '1.13-6.58L2.45 9.44l6.6-.96z';

final Path _starPath = parseSvgPathData(_kStarPath);

/// 0.5 단위 별점. 별 하나를 좌·우 절반으로 나눠 누른다. 웹 `StarRating.tsx`.
///
/// **크기가 둘이고 같이 움직이지 마라.** 입력은 56 이다 — 반쪽 터치 영역이
/// 28 로 WCAG 2.5.8 의 24 를 넘는다. 3.5 와 4.0 을 가르는 경계가 정확해야 해서
/// 옆 절반을 침범하는 여유 영역으로 대신할 수 없다. **입력 모드를 줄이지 마라.**
/// 표시는 20 이다(누를 곳이 없다).
///
/// 숫자를 옆에 같이 띄운다 — 없으면 자기가 뭘 골랐는지 확신하지 못한다.
/// 360px 의 카드 안에서는 별 다섯(280)과 숫자가 한 줄에 안 들어가서 숫자가
/// 다음 줄로 내려간다([Wrap]). 별을 줄여서 맞추지 마라.
class StarRating extends StatelessWidget {
  const StarRating({super.key, required this.value, this.onChanged});

  final double value;

  /// null 이면 표시 모드다.
  final ValueChanged<double>? onChanged;

  @override
  Widget build(BuildContext context) {
    final onChanged = this.onChanged;
    final readOnly = onChanged == null;
    final size = readOnly ? 20.0 : 56.0;
    final stars = Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var i = 1; i <= 5; i++)
          _Star(index: i, value: value, size: size, onSelect: onChanged),
      ],
    );
    final number = Text(
      value.toStringAsFixed(1),
      key: const Key('star-value'),
      style: TextStyle(
        fontSize: readOnly ? 12 : 15,
        fontWeight: FontWeight.w700,
        color: AppColors.brand900,
        fontFeatures: kTabularFigures,
      ),
    );
    final body = Wrap(
      crossAxisAlignment: WrapCrossAlignment.center,
      spacing: readOnly ? 6 : 8,
      children: [stars, number],
    );
    return readOnly
        ? Semantics(
            label: '${value.toStringAsFixed(1)}점',
            excludeSemantics: true,
            child: body,
          )
        : body;
  }
}

class _Star extends StatelessWidget {
  const _Star({
    required this.index,
    required this.value,
    required this.size,
    required this.onSelect,
  });

  final int index;
  final double value;
  final double size;
  final ValueChanged<double>? onSelect;

  @override
  Widget build(BuildContext context) {
    // 채울 비율 0 · 0.5 · 1. 바탕 별 위에 amber 별을 왼쪽부터 잘라 얹는다.
    final fill = (value - (index - 1)).clamp(0.0, 1.0);
    final onSelect = this.onSelect;
    return SizedBox.square(
      dimension: size,
      child: Stack(
        children: [
          const Positioned.fill(
            child: CustomPaint(painter: _StarPainter(AppColors.slate200)),
          ),
          Positioned.fill(
            child: ClipRect(
              child: Align(
                alignment: Alignment.centerLeft,
                widthFactor: fill,
                child: SizedBox.square(
                  dimension: size,
                  child: const CustomPaint(
                    painter: _StarPainter(AppColors.amber400),
                  ),
                ),
              ),
            ),
          ),
          if (onSelect != null)
            Row(
              children: [
                _half(index - 0.5, onSelect),
                _half(index.toDouble(), onSelect),
              ],
            ),
        ],
      ),
    );
  }

  Widget _half(double v, ValueChanged<double> onSelect) => Expanded(
    child: Semantics(
      button: true,
      label: '$v점',
      child: GestureDetector(
        key: ValueKey('star-$v'),
        behavior: HitTestBehavior.opaque,
        onTap: () => onSelect(v),
        child: const SizedBox.expand(),
      ),
    ),
  );
}

class _StarPainter extends CustomPainter {
  const _StarPainter(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.width / 24;
    canvas.save();
    canvas.scale(s, s);
    canvas.drawPath(_starPath, Paint()..color = color);
    canvas.restore();
  }

  @override
  bool shouldRepaint(_StarPainter old) => old.color != color;
}
