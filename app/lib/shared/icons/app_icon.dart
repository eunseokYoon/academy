import 'package:flutter/material.dart';
import 'package:path_drawing/path_drawing.dart';

import 'icon_paths.dart';

/// 웹의 선 아이콘. 패스는 [kIconPaths] 가 정본이고 여기서는 그리기만 한다.
///
/// 웹 `Icon.tsx` 의 stroke 설정을 그대로 쓴다 — `fill="none"`,
/// `stroke-linecap="round"`, `stroke-linejoin="round"`, 기본 굵기 1.7.
/// **현재 탭은 굵기가 2.2로 바뀐다** — 색만으로 구분하면 색각 이상이 있거나
/// 화면이 밝은 야외에서 어디 있는지 알 수 없다.
class AppIcon extends StatelessWidget {
  const AppIcon(
    this.name, {
    super.key,
    this.size = 22,
    this.strokeWidth = 1.7,
    required this.color,
  });

  final AppIconName name;
  final double size;
  final double strokeWidth;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(
      size: Size.square(size),
      painter: IconPainter(name: name, color: color, strokeWidth: strokeWidth),
    );
  }
}

/// 테스트가 값을 읽어야 해서 공개다.
class IconPainter extends CustomPainter {
  const IconPainter({
    required this.name,
    required this.color,
    required this.strokeWidth,
  });

  final AppIconName name;
  final Color color;
  final double strokeWidth;

  @override
  void paint(Canvas canvas, Size size) {
    // 웹 viewBox 가 0 0 24 24 다. 그 좌표계로 그린 뒤 요청된 크기로 확대한다.
    final s = size.width / 24;
    canvas.save();
    canvas.scale(s);
    canvas.drawPath(
      parseSvgPathData(kIconPaths[name]!),
      Paint()
        ..style = PaintingStyle.stroke
        ..color = color
        // 패스는 웹의 24단위 좌표계로 그려진다. canvas.scale() 이 패스와 함께 굵기도 확대하므로
        // strokeWidth 는 viewBox 단위 그대로이고 크기를 바꿔도 웹과 같은 상대 굵기를 유지한다
        ..strokeWidth = strokeWidth
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round,
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(IconPainter old) =>
      old.name != name || old.color != color || old.strokeWidth != strokeWidth;
}
