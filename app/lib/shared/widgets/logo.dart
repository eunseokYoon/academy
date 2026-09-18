import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../branding.dart';

/// NJ 모노그램. 흰 N의 오른쪽 기둥을 주황 J가 대신하는 로고의 도형이다.
///
/// **N의 오른쪽 세로획을 따로 그리지 마라.** N이 J에 기대어 서는 것이 이 마크의
/// 전부다 — 획을 하나 더 그으면 그냥 N 옆에 J가 선 모양이 된다. 한 폴리라인이다.
///
/// 색을 그리는 쪽 색에 맡기지 않았다. 남색 앱바 위와 흰 파비콘 위 어디서든
/// 흰 N + 주황 J로 같아야 로고로 읽힌다.
///
/// 좌표는 웹 SVG의 24×24 좌표계 그대로다 — `M7 17.5V6.5L16 16.5` 와
/// `M17 6v9.2c0 1.9-1.6 3.2-3.5 2.8`.
class LogoMark extends StatelessWidget {
  const LogoMark({super.key, this.size = 24});

  final double size;

  @override
  Widget build(BuildContext context) {
    return CustomPaint(size: Size.square(size), painter: const _LogoPainter());
  }
}

class _LogoPainter extends CustomPainter {
  const _LogoPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final s = size.width / 24;

    Paint stroke(Color color) => Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3 * s
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;

    // N — 왼쪽 기둥에서 올라가 사선으로 내려온다. 오른쪽 기둥은 J가 맡는다.
    canvas.drawPath(
      Path()
        ..moveTo(7 * s, 17.5 * s)
        ..lineTo(7 * s, 6.5 * s)
        ..lineTo(16 * s, 16.5 * s),
      stroke(Colors.white),
    );

    // J — 기둥을 세우고 아래에서 왼쪽으로 갈고리를 건다.
    canvas.drawPath(
      Path()
        ..moveTo(17 * s, 6 * s)
        ..lineTo(17 * s, 15.2 * s)
        ..cubicTo(17 * s, 17.1 * s, 15.4 * s, 18.4 * s, 13.5 * s, 18.0 * s),
      stroke(AppColors.accent500),
    );
  }

  @override
  bool shouldRepaint(_LogoPainter oldDelegate) => false;
}

/// 마크를 흰 테두리 사각형 안에 넣은 배지.
///
/// **왜 테두리가 필요한가.** 마크는 획만 있는 도형이라(면이 없다) 남색 위에
/// 그대로 얹으면 흰 획 두 개가 배경에 떠 있는 꼴이 된다 — 로고가 아니라
/// 장식으로 읽힌다. 테두리가 마크의 가장자리를 만들어 주면 하나의 물건이 된다.
///
/// **안을 흰색으로 채우지 마라.** 채우면 남색 위에 흰 스티커를 붙인 꼴이고,
/// 흰 판 위에서 N이 안 보여 마크를 남색으로 뒤집어야 한다 — 색 잠금이 둘로 갈라진다.
///
/// **남색 배경 전용이다.** 흰 배경에 놓으면 테두리도 마크도 보이지 않는다.
///
/// 크기를 키우면 반경과 테두리 두께도 같이 올려라. 마크는 배지의 60~65%가
/// 적당하다 — 꽉 채우면 획이 테두리에 붙는다. 로그인 화면은 68/42를 쓴다.
class LogoBadge extends StatelessWidget {
  const LogoBadge({
    super.key,
    this.size = 30,
    this.markSize = 19,
    this.radius = 9,
    this.borderWidth = 1.5,
  });

  final double size;
  final double markSize;
  final double radius;
  final double borderWidth;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(radius),
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.85),
          width: borderWidth,
        ),
      ),
      child: Center(child: LogoMark(size: markSize)),
    );
  }
}

/// 워드마크. 로고와 같은 잠금이다 — 한글은 흰색, `LAB` 만 주황.
///
/// 이름을 통째로 흰색으로 두면 로고와 다른 물건으로 보인다.
/// 주황 두 글자가 앱바와 로고를 같은 것으로 묶는다.
class Wordmark extends StatelessWidget {
  const Wordmark({super.key, this.fontSize = 15});

  final double fontSize;

  @override
  Widget build(BuildContext context) {
    return Text.rich(
      const TextSpan(
        children: [
          TextSpan(text: academyNameHead),
          TextSpan(
            text: academyNameTail,
            style: TextStyle(color: AppColors.accent500),
          ),
        ],
      ),
      style: TextStyle(
        fontSize: fontSize,
        fontWeight: FontWeight.w800,
        letterSpacing: -0.02 * fontSize,
        color: Colors.white,
      ),
    );
  }
}
