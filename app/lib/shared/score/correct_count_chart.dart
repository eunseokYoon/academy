import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/chart_colors.dart';
import 'score_data.dart';

/// 웹 SVG 의 viewBox. 좌표는 전부 이 안에서 계산하고 그릴 때 한 번 늘린다.
const double _vw = 320;
const double _vh = 150;
const _padTop = 14.0;
const _padRight = 14.0;
const _padBottom = 28.0;
const _padLeft = 32.0;

/// 막대 하나. 좌표는 viewBox(320×150) 기준이다.
class ChartBar {
  const ChartBar(this.rect, this.color);

  final Rect rect;
  final Color color;
}

/// 가로축 라벨 하나.
class ChartLabel {
  const ChartLabel(this.x, this.text, {this.marked = false});

  final double x;
  final String text;
  final bool marked;
}

/// 그릴 것 전부. [layoutChart] 가 만들고 페인터는 그리기만 한다 — 규칙을
/// 테스트하려고 계산을 여기로 뺐다.
class ChartLayout {
  const ChartLayout({
    required this.yMax,
    required this.ticks,
    required this.bars,
    required this.labels,
  });

  /// 구획 전체에서 가장 큰 전체 문항 수. Y축이 이것 하나로 고정된다.
  final int yMax;

  /// 0 · 가운데 · yMax. 값과 viewBox 의 y.
  final List<(int, double)> ticks;
  final List<ChartBar> bars;
  final List<ChartLabel> labels;
}

/// 웹 `CorrectCountChart.tsx` 의 계산을 그대로 옮겼다. 그릴 것이 없으면 null.
///
/// - **전체 문항 수가 없는 주는 막대를 안 그린다.** 0 으로 채우면 「다 틀렸다」로 읽힌다.
///   시험을 안 본 주는 애초에 배열에 없다 — 빈 막대를 채워 넣지 마라.
/// - **Y축은 구획 전체에서 가장 큰 전체 문항 수 하나로 고정한다.** 주마다 다시 잡으면
///   막대 높이를 주끼리 비교할 수 없다.
/// - **items 순서를 그대로 그린다.** 서버가 오름차순으로 준다.
/// - [highlight] 주는 주황이다(주간 레포트의 「지금 보고 있는 주」).
ChartLayout? layoutChart(
  List<ScoreItem> items,
  ChartKind kind, {
  int? highlight,
}) {
  if (kind == ChartKind.none) return null;
  final split = kind == ChartKind.splitBar;
  // 미응시 줄은 막대가 없다(값이 비어 아래 조건에서도 빠지지만 뜻을 적어 둔다).
  final points = items
      .where((i) => !i.absent)
      .where(
        (i) => split
            ? i.internalTotal != null || i.externalTotal != null
            : i.correctCount != null && i.totalCount != null,
      )
      .toList();
  if (points.isEmpty) return null;

  final yMax = points
      .map(
        (i) => split
            ? math.max(i.internalTotal ?? 0, i.externalTotal ?? 0)
            : i.totalCount ?? 0,
      )
      .fold(1, math.max);

  const plotWidth = _vw - _padLeft - _padRight;
  const plotHeight = _vh - _padTop - _padBottom;
  final slot = plotWidth / points.length;
  // 막대 폭 상한이 둘이다 — 주가 둘뿐일 때 판처럼 넓어지지 않게(14·22), 그리고
  // 옆 주차와 붙지 않게. split 은 두 막대 + 사이 2px 가 slot 에 정확히 들어가야
  // 한다(웹 주석: `slot * 0.5` 면 10주차쯤부터 붙는다).
  final barWidth = split
      ? math.min((slot - 2) / 2, 14.0)
      : math.min(slot * 0.5, 22.0);
  double y(num v) => _padTop + plotHeight * (1 - v / yMax);
  double cx(int i) => _padLeft + slot * (i + 0.5);
  Rect bar(double left, num value) {
    final top = y(value);
    return Rect.fromLTWH(left, top, barWidth, math.max(1, y(0) - top));
  }

  final bars = <ChartBar>[];
  for (var i = 0; i < points.length; i++) {
    final p = points[i];
    if (split) {
      final ic = p.internalCorrect;
      final ec = p.externalCorrect;
      if (ic != null) {
        bars.add(ChartBar(bar(cx(i) - barWidth - 1, ic), ChartColors.internal));
      }
      if (ec != null) {
        bars.add(ChartBar(bar(cx(i) + 1, ec), ChartColors.external));
      }
    } else {
      bars.add(
        ChartBar(
          bar(cx(i) - barWidth / 2, p.correctCount ?? 0),
          p.key == highlight ? ChartColors.marked : ChartColors.external,
        ),
      );
    }
  }

  // 라벨이 겹치면 양 끝과 가운데만 적는다. 표시한 주는 늘 적는다 — 어느 주인지
  // 모르면 표시한 뜻이 없다.
  final labels = <ChartLabel>[
    for (var i = 0; i < points.length; i++)
      if (points[i].key == highlight ||
          points.length <= 6 ||
          i == 0 ||
          i == points.length - 1 ||
          i == points.length ~/ 2)
        ChartLabel(
          cx(i),
          points[i].weekLabel,
          marked: points[i].key == highlight,
        ),
  ];

  final ticks = [0, (yMax / 2).round(), yMax];
  return ChartLayout(
    yMax: yMax,
    ticks: [for (final t in ticks) (t, y(t))],
    bars: bars,
    labels: labels,
  );
}

/// 맞힌 개수 막대. S-7 · P-4 · P-6 공용.
///
/// **정답률이 아니라 개수다**(2026-09-10). 그래서 꺾은선이 아니다 — 주마다 전체
/// 문항 수가 달라서 개수를 선으로 이으면 기울기가 거짓말을 한다(7-5).
/// 반 평균선·등수 같은 상대 지표는 그리지 않는다.
class CorrectCountChart extends StatelessWidget {
  const CorrectCountChart({
    super.key,
    required this.items,
    required this.chartKind,
    this.highlight,
  });

  final List<ScoreItem> items;
  final ChartKind chartKind;

  /// 표시할 주의 [weekKey]. 성적 목록 화면은 넘기지 않는다.
  final int? highlight;

  @override
  Widget build(BuildContext context) {
    final layout = layoutChart(items, chartKind, highlight: highlight);
    if (layout == null) return const SizedBox.shrink();
    final split = chartKind == ChartKind.splitBar;
    return Column(
      key: const Key('score-chart'),
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          '${split ? '내부 · 외부 맞힌 개수' : '맞힌 개수'} · 전체 ${layout.yMax}문항',
          textAlign: TextAlign.right,
          style: const TextStyle(
            fontSize: 12,
            color: AppColors.slate500,
            fontFeatures: [FontFeature.tabularFigures()],
          ),
        ),
        const SizedBox(height: 8),
        Semantics(
          label: split ? '주차별 내부·외부 맞힌 개수' : '주차별 맞힌 개수',
          image: true,
          // 웹 `h-36 w-full min-w-[280px]`.
          child: LayoutBuilder(
            builder: (context, constraints) {
              final width = math.max(constraints.maxWidth, 280.0);
              final chart = CustomPaint(
                size: Size(width, 144),
                painter: _ChartPainter(layout),
              );
              return constraints.maxWidth >= 280
                  ? chart
                  : SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: chart,
                    );
            },
          ),
        ),
        // 2계열일 때만 범례가 필요하다. 색 둘을 글씨 없이 두면 무엇인지 모른다.
        if (split) ...[
          const SizedBox(height: 8),
          const Row(
            children: [
              _LegendDot(color: ChartColors.internal, label: '내부지문'),
              SizedBox(width: 12),
              _LegendDot(color: ChartColors.external, label: '외부지문'),
            ],
          ),
        ],
      ],
    );
  }
}

class _LegendDot extends StatelessWidget {
  const _LegendDot({required this.color, required this.label});

  final Color color;
  final String label;

  @override
  Widget build(BuildContext context) => Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      Container(
        width: 10,
        height: 10,
        decoration: BoxDecoration(
          color: color,
          borderRadius: BorderRadius.circular(2),
        ),
      ),
      const SizedBox(width: 6),
      Text(
        label,
        style: const TextStyle(fontSize: 11, color: AppColors.slate500),
      ),
    ],
  );
}

/// SVG 의 기본 `preserveAspectRatio`(xMidYMid meet)처럼 viewBox 를 가로세로 같은
/// 비율로 늘려 가운데 둔다. 글자가 찌그러지지 않는다.
class _ChartPainter extends CustomPainter {
  _ChartPainter(this.layout);

  final ChartLayout layout;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = math.min(size.width / _vw, size.height / _vh);
    canvas.save();
    canvas.translate(
      (size.width - _vw * scale) / 2,
      (size.height - _vh * scale) / 2,
    );
    canvas.scale(scale);

    final grid = Paint()
      ..color = ChartColors.grid
      ..strokeWidth = 1;
    for (final (value, y) in layout.ticks) {
      canvas.drawLine(Offset(_padLeft, y), Offset(_vw - _padRight, y), grid);
      _text(canvas, '$value', Offset(4, y + 4), ChartColors.tick);
    }
    for (final b in layout.bars) {
      canvas.drawRRect(
        RRect.fromRectAndRadius(b.rect, const Radius.circular(2)),
        Paint()..color = b.color,
      );
    }
    for (final l in layout.labels) {
      _text(
        canvas,
        l.text,
        Offset(l.x, _vh - 8),
        l.marked ? ChartColors.marked : ChartColors.label,
        bold: l.marked,
        center: true,
      );
    }
    canvas.restore();
  }

  /// [baseline] 은 SVG `<text>` 의 x·y — y 가 글자의 기준선이다.
  void _text(
    Canvas canvas,
    String text,
    Offset baseline,
    Color color, {
    bool bold = false,
    bool center = false,
  }) {
    final tp = TextPainter(
      text: TextSpan(
        text: text,
        style: TextStyle(
          fontSize: 9,
          color: color,
          fontWeight: bold ? FontWeight.w700 : FontWeight.w400,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    final ascent = tp.computeDistanceToActualBaseline(TextBaseline.alphabetic);
    tp.paint(
      canvas,
      Offset(
        center ? baseline.dx - tp.width / 2 : baseline.dx,
        baseline.dy - ascent,
      ),
    );
  }

  @override
  bool shouldRepaint(covariant _ChartPainter old) => old.layout != layout;
}
