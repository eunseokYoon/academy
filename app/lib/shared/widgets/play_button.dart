import 'package:flutter/material.dart';
import 'package:path_drawing/path_drawing.dart';

/// 영상 위의 둥근 재생 버튼. 웹 `LastLessonCard.tsx` 의 `h-14 w-14 rounded-full
/// bg-black/45 ring-white/40` 과 그 안의 삼각형이다.
///
/// 원래 `last_lesson_card.dart` 안의 private 였다. 숙제 영상(S-4)이 같은 것을
/// 필요로 해서 여기로 옮겼다 — **두 벌을 두지 마라.**
class PlayButton extends StatelessWidget {
  const PlayButton({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 56,
      height: 56,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: Colors.black.withValues(alpha: 0.45),
        border: Border.all(color: Colors.white.withValues(alpha: 0.4)),
      ),
      child: Center(
        // 광학적으로 가운데 오게 살짝 오른쪽으로 민다.
        child: Transform.translate(
          offset: const Offset(2, 0),
          child: const CustomPaint(
            size: Size.square(24),
            painter: _PlayTrianglePainter(),
          ),
        ),
      ),
    );
  }
}

/// 재생 삼각형. 웹의 `<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>`
/// 를 그대로 옮긴다.
///
/// `AppIcon` 은 선으로 그리는 아이콘이라 채운 삼각형이 없다 — 여기서만 예외로
/// `CustomPainter` 를 둔다. `Icons.play_arrow` 로 대체하지 마라(CLAUDE.md 14-8).
class _PlayTrianglePainter extends CustomPainter {
  const _PlayTrianglePainter();

  @override
  void paint(Canvas canvas, Size size) {
    // 웹 viewBox 가 0 0 24 24 다. 그 좌표계로 그린 뒤 요청된 크기로 맞춘다.
    final s = size.width / 24;
    canvas.save();
    canvas.scale(s);
    canvas.drawPath(
      parseSvgPathData('M8 5v14l11-7z'),
      Paint()
        ..style = PaintingStyle.fill
        ..color = Colors.white,
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _PlayTrianglePainter oldDelegate) => false;
}
