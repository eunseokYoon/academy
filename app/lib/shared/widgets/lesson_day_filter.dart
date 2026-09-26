import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';

import 'package:academy_app/shared/lib/homework_labels.dart';

import 'app_card.dart';

/// 목록의 달 필터. S-2 와 P-3 가 같이 쓴다. `month` 가 null 이면 「전체 월」이고 연도도 안 보낸다 —
/// 서버는 연·월이 다 있어야 범위를 건다.
class MonthFilter {
  const MonthFilter(this.year, this.month);

  static const all = MonthFilter(null, null);

  final int? year;
  final int? month;

  @override
  bool operator ==(Object other) =>
      other is MonthFilter && other.year == year && other.month == month;

  @override
  int get hashCode => Object.hash(year, month);
}

/// 수업일 칩의 선택. **셋을 구분해야 한다** — 전체 / 「수업일 없음」 묶음 /
/// 특정 날짜. 웹은 `undefined`·`null`·문자열로 가른다. `String?` 하나로 줄이면
/// 「전체」와 「수업일 없음」이 같은 값이 된다.
class DayChoice {
  const DayChoice._(this.all, this.date);

  /// 전체. 아무 칩도 고르지 않은 상태다.
  static const everything = DayChoice._(true, null);

  /// 그 묶음 하나. [date] 가 null 이면 「수업일 없음」 묶음이다.
  const DayChoice.of(String? date) : this._(false, date);

  final bool all;
  final String? date;

  bool matches(String? lessonDate) => all || lessonDate == date;

  @override
  bool operator ==(Object other) =>
      other is DayChoice && other.all == all && other.date == date;

  @override
  int get hashCode => Object.hash(all, date);
}

/// 숙제 목록 위의 달·수업일 필터. 웹 `LessonDayFilter.tsx`. S-2 와 P-3 가 같이 쓴다.
///
/// **달은 서버가 거른다**(year·month 파라미터). 받아 온 뒤 앱에서 거르면 한
/// 페이지(20건) 안에서만 걸러져 지난 달 숙제가 조용히 사라진다.
///
/// **수업일 칩은 반대로 받아 온 것 안에서 고른다.** 칩은 실제로 그려질
/// [groups] 에서 뽑는다 — 다른 배열에서 뽑으면 고른 칩이 빈 화면을 가리킨다.
/// 수업일이 하나뿐이면 고를 게 없어 칩 줄을 안 그린다.
///
/// 연도는 작년·올해 둘이다(웹과 같다). **표시용이라 기기 시계를 써도 된다**
/// — D-day 계산이 아니다(14-2). [thisYear] 는 테스트만 넘긴다.
class LessonDayFilter<T> extends StatelessWidget {
  const LessonDayFilter({
    super.key,
    required this.year,
    required this.month,
    required this.selectedDay,
    required this.groups,
    required this.onYearChange,
    required this.onMonthChange,
    required this.onDayChange,
    this.thisYear,
  });

  final int year;

  /// null 이면 「전체 월」이다. 그때는 달 범위를 안 보내고 서버가 전부 준다.
  final int? month;
  final DayChoice selectedDay;
  final List<LessonDayGroup<T>> groups;
  final ValueChanged<int> onYearChange;
  final ValueChanged<int?> onMonthChange;
  final ValueChanged<DayChoice> onDayChange;
  final int? thisYear;

  @override
  Widget build(BuildContext context) {
    final now = thisYear ?? DateTime.now().year;
    return AppCard(
      // 웹 `p-3 space-y-2.5`.
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: _Select<int>(
                  key: const Key('filter-year'),
                  value: year,
                  items: [
                    for (final y in [now - 1, now]) (y, '$y년'),
                  ],
                  onChanged: (v) => onYearChange(v!),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _Select<int?>(
                  key: const Key('filter-month'),
                  value: month,
                  items: [
                    (null, '전체 월'),
                    for (var m = 1; m <= 12; m++) (m, '$m월'),
                  ],
                  onChanged: onMonthChange,
                ),
              ),
            ],
          ),
          if (groups.length > 1) ...[
            const SizedBox(height: 10),
            SingleChildScrollView(
              key: const Key('filter-days'),
              scrollDirection: Axis.horizontal,
              child: Row(
                children: [
                  _DayChip(
                    label: '전체',
                    selected: selectedDay.all,
                    onTap: () => onDayChange(DayChoice.everything),
                  ),
                  for (final group in groups) ...[
                    const SizedBox(width: 6),
                    _DayChip(
                      label: group.lessonDate == null
                          ? '수업일 없음'
                          : lessonDayShort(group.lessonDate!),
                      selected: selectedDay == DayChoice.of(group.lessonDate),
                      onTap: () => onDayChange(DayChoice.of(group.lessonDate)),
                    ),
                  ],
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// 웹 `<select className="rounded-lg border border-slate-300 px-2 py-2">`.
class _Select<V> extends StatelessWidget {
  const _Select({
    super.key,
    required this.value,
    required this.items,
    required this.onChanged,
  });

  final V value;
  final List<(V, String)> items;
  final ValueChanged<V?> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(AppRadii.lg),
        border: Border.all(color: AppColors.slate300),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<V>(
          value: value,
          isExpanded: true,
          style: const TextStyle(fontSize: 14, color: AppColors.slate900),
          items: [
            for (final (v, label) in items)
              DropdownMenuItem<V>(value: v, child: Text(label)),
          ],
          onChanged: onChanged,
        ),
      ),
    );
  }
}

/// 웹 `DayChip` — 고른 칩은 brand600 바탕 흰 글씨, 나머지는 slate100.
class _DayChip extends StatelessWidget {
  const _DayChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? AppColors.brand600 : AppColors.slate100,
      borderRadius: BorderRadius.circular(999),
      child: InkWell(
        key: Key('day-chip-$label'),
        onTap: onTap,
        borderRadius: BorderRadius.circular(999),
        child: Padding(
          // 웹 `px-3 py-1.5`.
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
          child: Text(
            label,
            style: TextStyle(
              fontSize: 13,
              fontWeight: FontWeight.w600,
              color: selected ? Colors.white : AppColors.slate600,
              fontFeatures: kTabularFigures,
            ),
          ),
        ),
      ),
    );
  }
}
