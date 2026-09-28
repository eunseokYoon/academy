import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';

import 'package:academy_app/shared/lib/attendance.dart';

import 'app_card.dart';

const _weekdays = ['일', '월', '화', '수', '목', '금', '토'];

/// S-6 · P-2 공용 월별 캘린더. 웹 정본은
/// `frontend/src/shared/components/AttendanceCalendar.tsx` 다.
///
/// **칸 하나는 날짜지 수업이 아니다.** 칸 바탕은 비워 두고 그 안에 일정마다
/// 칩을 넣는다 — 수업도 클리닉도 같은 색·같은 글씨다. 바탕에 상태 색을 쓰면
/// 칸 하나에 일정이 둘일 때 어느 쪽 색인지 알 수 없다.
///
/// **클리닉 목록을 아래에 따로 그리지 마라.** 캘린더가 날짜와 출결을 이미 칩으로
/// 보여준다. 칩에 도착 시각이 없는 건 칸이 좁아서다 — 몇 시에 가는지는 홈의
/// 「다음 클리닉」이 답한다.
class AttendanceCalendar extends StatelessWidget {
  const AttendanceCalendar({
    super.key,
    required this.data,
    required this.onPrev,
    required this.onNext,
  });

  final AttendanceCalendarData data;
  final VoidCallback onPrev;
  final VoidCallback onNext;

  @override
  Widget build(BuildContext context) {
    final month = data.month;
    final lessons = {for (final d in month.days) d.date: d.status};
    // 같은 날 클리닉이 둘일 수 있어 날짜당 배열로 모은다.
    final clinics = <String, List<DayStatus>>{};
    for (final c in data.clinics) {
      (clinics[c.date] ??= []).add(c.status);
    }

    // 1일이 무슨 요일인지에 따라 앞을 비운다. Dart 의 weekday 는 월=1…일=7 이라
    // 7 로 나눈 나머지가 일=0 이다.
    final leading = DateTime(month.year, month.month).weekday % 7;
    final daysInMonth = DateTime(month.year, month.month + 1, 0).day;
    final cells = <int?>[
      for (var i = 0; i < leading; i++) null,
      for (var d = 1; d <= daysInMonth; d++) d,
    ];
    while (cells.length % 7 != 0) {
      cells.add(null);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _SummaryGrid(summary: month.summary),
        if (month.homeworkCompletionRate != null) ...[
          const SizedBox(height: 12),
          _HomeworkRate(rate: month.homeworkCompletionRate!),
        ],
        const SizedBox(height: 12),
        AppCard(
          // 웹 `card p-3`.
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Padding(
                padding: const EdgeInsets.only(left: 4, right: 4, bottom: 8),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    _MonthButton(
                      key: const Key('calendar-prev'),
                      label: '이전 달',
                      glyph: '‹',
                      onTap: onPrev,
                    ),
                    Text(
                      '${month.year}년 ${month.month}월',
                      key: const Key('calendar-month'),
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w700,
                        color: AppColors.brand900,
                        fontFeatures: kTabularFigures,
                      ),
                    ),
                    _MonthButton(
                      key: const Key('calendar-next'),
                      label: '다음 달',
                      glyph: '›',
                      onTap: onNext,
                    ),
                  ],
                ),
              ),
              // 일요일 빨강 · 토요일 파랑. 한국 달력 관습이라 없으면 어색하다.
              Row(
                children: [
                  for (var i = 0; i < 7; i++)
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 6),
                        child: Text(
                          _weekdays[i],
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w500,
                            color: i == 0
                                ? AppColors.red400
                                : i == 6
                                ? AppColors.brand400
                                : AppColors.slate400,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
              for (var row = 0; row < cells.length ~/ 7; row++)
                Padding(
                  // 웹 `gap-1`.
                  padding: EdgeInsets.only(top: row == 0 ? 0 : 4),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      for (var col = 0; col < 7; col++) ...[
                        if (col > 0) const SizedBox(width: 4),
                        Expanded(
                          child: _cell(
                            cells[row * 7 + col],
                            month,
                            lessons,
                            clinics,
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        const _Legend(),
      ],
    );
  }

  Widget _cell(
    int? day,
    AttendanceMonth month,
    Map<String, DayStatus> lessons,
    Map<String, List<DayStatus>> clinics,
  ) {
    if (day == null) return const SizedBox.shrink();
    final key = dateKey(month.year, month.month, day);
    return _DayCell(
      key: ValueKey('day-$key'),
      day: day,
      lesson: lessons[key],
      clinics: clinics[key] ?? const [],
    );
  }
}

class _MonthButton extends StatelessWidget {
  const _MonthButton({
    super.key,
    required this.label,
    required this.glyph,
    required this.onTap,
  });

  final String label;
  final String glyph;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      button: true,
      label: label,
      excludeSemantics: true,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppRadii.lg),
        child: SizedBox(
          width: 32,
          height: 32,
          child: Center(
            child: Text(
              glyph,
              style: const TextStyle(
                fontSize: 18,
                height: 1,
                color: AppColors.brand600,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 4칸 통계. 학부모가 이 화면에서 제일 먼저 보는 건 「몇 번 빠졌나」다.
///
/// **대체 등원은 출석 칸에 합친다**(5-1). 5칸으로 늘리면 360px 에서 뭉개진다.
class _SummaryGrid extends StatelessWidget {
  const _SummaryGrid({required this.summary});

  final AttendanceSummary summary;

  @override
  Widget build(BuildContext context) {
    final items = [
      ('출석', summary.attended, AppColors.emerald600),
      ('지각', summary.late, AppColors.amber600),
      ('결석', summary.absent, AppColors.red600),
      ('병·공결', summary.sick + summary.excused, AppColors.sky600),
    ];
    return Row(
      children: [
        for (var i = 0; i < items.length; i++) ...[
          if (i > 0) const SizedBox(width: 8),
          Expanded(
            child: AppCard(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 12),
              child: Column(
                children: [
                  // 고정폭 숫자를 쓰지 않는다 — 「11」이 「1 1」처럼 벌어진다(웹 주석).
                  Text(
                    '${items[i].$2}',
                    key: ValueKey('summary-${items[i].$1}'),
                    style: TextStyle(
                      fontSize: 24,
                      height: 1,
                      fontWeight: FontWeight.w800,
                      color: items[i].$3,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    items[i].$1,
                    style: const TextStyle(
                      fontSize: 11,
                      color: AppColors.slate500,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ],
    );
  }
}

class _HomeworkRate extends StatelessWidget {
  const _HomeworkRate({required this.rate});

  final int rate;

  @override
  Widget build(BuildContext context) {
    return AppCard(
      key: const Key('homework-rate'),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          const Text(
            '이번 달 숙제 완료율',
            style: TextStyle(fontSize: 14, color: AppColors.slate600),
          ),
          Text(
            '$rate%',
            style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w700,
              color: AppColors.brand700,
              fontFeatures: kTabularFigures,
            ),
          ),
        ],
      ),
    );
  }
}

class _DayCell extends StatelessWidget {
  const _DayCell({
    super.key,
    required this.day,
    required this.lesson,
    required this.clinics,
  });

  final int day;
  final DayStatus? lesson;
  final List<DayStatus> clinics;

  @override
  Widget build(BuildContext context) {
    final lesson = this.lesson;
    final empty = lesson == null && clinics.isEmpty;
    // 높이는 일정이 있는 칸에만 준다(웹 `min-h-[4.25rem]`). 모든 칸에 주면
    // 일정이 없는 주가 68px 씩 차지해 달력 위아래가 텅 빈 채로 늘어진다.
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: empty ? 0 : 68),
      child: Padding(
        padding: const EdgeInsets.all(4),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              '$day',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 12,
                fontWeight: empty ? FontWeight.w400 : FontWeight.w600,
                color: empty ? AppColors.slate300 : AppColors.slate700,
                fontFeatures: kTabularFigures,
              ),
            ),
            // 수업이 먼저, 클리닉이 아래다. 그 날 순서가 아니라 고정 순서라 눈이 익는다.
            if (lesson != null) ...[
              const SizedBox(height: 2),
              SessionChip(kind: '수업', status: lesson),
            ],
            for (final c in clinics) ...[
              const SizedBox(height: 2),
              SessionChip(kind: '클리닉', status: c),
            ],
          ],
        ),
      ),
    );
  }
}

/// 칸 안의 일정 하나. **확정 전에는 종류만 적는다** — 「수업 미확인」은 뭔가
/// 잘못된 것처럼 읽히는데 실제로는 아직 오지 않은 날일 뿐이다. 회색이 이미
/// 「확정 전」을 말하고 범례가 그 색을 풀어 준다. 읽어 주는 기계에는 상태를
/// 그대로 들려준다.
///
/// **띄어쓰기에서만 접는다**(웹 `break-keep`). 그냥 [Text] 로 두면 한글을 아무
/// 데서나 끊어 「수업 출」/「석」이 된다 — 그래서 낱말마다 줄바꿈 없는 조각으로
/// 놓고 [Wrap] 이 낱말 사이에서만 접는다.
class SessionChip extends StatelessWidget {
  const SessionChip({super.key, required this.kind, required this.status});

  /// 「수업」 · 「클리닉」
  final String kind;
  final DayStatus status;

  @override
  Widget build(BuildContext context) {
    final style = dayStatusStyle(status);
    final spoken = '$kind ${status.label}';
    final shown = status == DayStatus.pending ? kind : spoken;
    return Semantics(
      label: spoken,
      excludeSemantics: true,
      child: Container(
        key: const Key('session-chip'),
        padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 4),
        decoration: BoxDecoration(
          color: style.background,
          borderRadius: BorderRadius.circular(4),
          border: Border.all(color: style.ring),
        ),
        child: Wrap(
          alignment: WrapAlignment.center,
          // 띄어쓰기 한 칸(10px 글씨의 공백 폭). 조각 안에 공백을 넣으면 접혔을
          // 때 둘째 줄이 공백으로 시작해 가운데가 어긋난다.
          spacing: 3,
          children: [
            for (final word in shown.split(' '))
              Text(
                word,
                softWrap: false,
                style: TextStyle(
                  fontSize: 10,
                  height: 1.25,
                  color: style.foreground,
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// 칸 안에 상태가 글씨로 적혀 있으니 범례는 색만 짚어 준다.
class _Legend extends StatelessWidget {
  const _Legend();

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: Wrap(
        spacing: 12,
        runSpacing: 6,
        children: [
          for (final status in DayStatus.values)
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Container(
                  width: 12,
                  height: 12,
                  decoration: BoxDecoration(
                    color: dayStatusStyle(status).background,
                    borderRadius: BorderRadius.circular(4),
                    border: Border.all(color: dayStatusStyle(status).ring),
                  ),
                ),
                const SizedBox(width: 4),
                Text(
                  status.label,
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.slate500,
                  ),
                ),
              ],
            ),
        ],
      ),
    );
  }
}

/// 회색이 무슨 뜻인지. 칩에서 「미확인」 글자를 뺐으니 여기서 말해야 한다.
class PendingFootnote extends StatelessWidget {
  const PendingFootnote({super.key});

  @override
  Widget build(BuildContext context) => const Padding(
    padding: EdgeInsets.only(top: 16),
    child: Text(
      '회색 칩은 선생님이 아직 출석을 확정하지 않은 날입니다. 결석이 아닙니다.',
      style: TextStyle(fontSize: 12, color: AppColors.slate500),
    ),
  );
}
