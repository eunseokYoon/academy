import 'package:flutter/material.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../shared/widgets/app_badge.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/app_text_area.dart';
import '../../../shared/widgets/form_error.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/section.dart';
import '../../../shared/widgets/sub_page.dart';
import '../../../shared/widgets/submit_button.dart';
import 'student_schedule_data.dart';

/// 사유 상한. 서버 `@Size(max = 500)` 과 같다.
const int _kReasonMax = 500;

/// S-9 스케줄 관리. 웹 정본은 `StudentClinicPage.tsx` 다. 레일 라벨이 「스케줄」이고
/// 경로가 `/student/clinics` 다.
///
/// **학생은 클리닉을 신청하지 못한다**(2026-09-01 확정) — 배정받은 것만 본다.
/// 신청 목록을 되살리지 마라. 변경(시각·다른 클리닉)은 남아 있다.
///
/// **취소 버튼을 만들지 마라**(10-2). 못 가면 다른 시각으로 옮긴다.
///
/// [onClinicChanged] 는 클리닉을 옮긴 뒤 부른다. 변경하면 공지가 한 건
/// 발행되고(10-1) 홈의 「다음 클리닉」과 출석 캘린더도 낡는다 — 앱이 그
/// 컨트롤러들에 `markStale` 을 건다.
class StudentSchedulePage extends StatefulWidget {
  const StudentSchedulePage({
    super.key,
    required this.controller,
    required this.onClinicChanged,
    this.today = kstToday,
  });

  final StudentScheduleController controller;
  final VoidCallback onClinicChanged;

  /// 테스트가 날짜를 고정한다.
  final String Function() today;

  @override
  State<StudentSchedulePage> createState() => _StudentSchedulePageState();
}

class _StudentSchedulePageState extends State<StudentSchedulePage>
    with ReappearReload<StudentSchedulePage> {
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onChanged);
    widget.controller.load(widget.today());
  }

  @override
  void didUpdateWidget(covariant StudentSchedulePage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onChanged);
      widget.controller.addListener(_onChanged);
      widget.controller.load(widget.today());
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onChanged);
    super.dispose();
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  /// 날이 바뀌었으면 매개변수가 달라져 새 범위로 부른다.
  @override
  void onReappear() => widget.controller.load(widget.today());

  Future<void> _openClinicChange(
    StudentScheduleData data,
    StudentClinic clinic,
  ) async {
    final done = await _showSheet(
      context,
      _ClinicChangeSheet(
        clinic: clinic,
        candidates: data.moveTargets(clinic),
        repository: widget.controller.repository,
      ),
    );
    if (done != true || !mounted) return;
    widget.onClinicChanged();
    await widget.controller.reload();
  }

  Future<void> _openLessonChange() async {
    final done = await _showSheet(
      context,
      _LessonChangeSheet(repository: widget.controller.repository),
    );
    if (done != true || !mounted) return;
    await widget.controller.reload();
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final data = c.data;
    return SubPageScroll(
      role: '학생',
      onRefresh: c.refresh,
      children: [
        const PageTitle(title: '스케줄 관리'),
        if (data == null)
          c.status == HomeStatus.error
              ? HomeErrorView(message: c.error, onRetry: c.refresh)
              : const SizedBox(height: 160, child: FullScreenLoader())
        else ...[
          _lessonChangeSection(data),
          const SizedBox(height: 20),
          _clinicSection(data),
        ],
      ],
    );
  }

  /// 수업일 변경. 클리닉과 별개이고 **예약이 아니다** — 승인되면 공지가 한 건
  /// 뜨는 것이 전부다.
  Widget _lessonChangeSection(StudentScheduleData data) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            const Expanded(
              child: SectionHead(tone: SectionTone.neutral, title: '수업일 변경'),
            ),
            _SmallFilledButton(
              key: const Key('lesson-change-open'),
              label: '수업 변경',
              onPressed: _openLessonChange,
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (data.requests.isEmpty)
          const AppCard(
            padding: EdgeInsets.all(16),
            child: Text(
              '변경 요청한 수업이 없습니다.',
              style: TextStyle(fontSize: 14, color: AppColors.slate500),
            ),
          )
        else
          for (final (i, r) in data.requests.indexed) ...[
            if (i > 0) const SizedBox(height: 8),
            _requestCard(r),
          ],
      ],
    );
  }

  Widget _requestCard(LessonChangeRequest r) {
    return AppCard(
      key: ValueKey('lesson-change-${r.requestId}'),
      padding: const EdgeInsets.all(12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      lessonSlotLabel(r.from),
                      style: const TextStyle(
                        fontSize: 14,
                        color: AppColors.slate500,
                        fontFeatures: kTabularFigures,
                      ),
                    ),
                    Text(
                      '→ ${lessonSlotLabel(r.to)}',
                      style: const TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                        color: AppColors.brand900,
                        fontFeatures: kTabularFigures,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              AppBadge(
                tone: switch (r.status) {
                  'APPROVED' => BadgeTone.ok,
                  'REJECTED' => BadgeTone.danger,
                  _ => BadgeTone.warn,
                },
                label: lessonChangeStatusLabel(r.status),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            '사유 · ${r.reason}',
            style: const TextStyle(fontSize: 12, color: AppColors.slate500),
          ),
        ],
      ),
    );
  }

  Widget _clinicSection(StudentScheduleData data) {
    final mine = data.mine;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionHead(title: '내 클리닉', count: mine.length),
        if (mine.isEmpty)
          const TintBlock(
            tone: SectionTone.neutral,
            children: [EmptyNote(text: '배정된 클리닉이 없습니다.')],
          )
        else
          for (final (i, clinic) in mine.indexed) ...[
            if (i > 0) const SizedBox(height: 8),
            _clinicCard(data, clinic),
          ],
      ],
    );
  }

  Widget _clinicCard(StudentScheduleData data, StudentClinic clinic) {
    return AppCard(
      key: ValueKey('clinic-${clinic.clinicId}'),
      padding: const EdgeInsets.all(14),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 도착 시각이 주인공이다. 학생이 기억해야 하는 건 「몇 시에
                    // 가는가」다 — 시간대는 그 아래 작게 둔다.
                    Text(
                      '${clinic.clinicDate.substring(5)} '
                      '${clinic.myReservation!.arrivalTime} 도착',
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        color: AppColors.brand900,
                        fontFeatures: kTabularFigures,
                      ),
                    ),
                    Text(
                      clinicSlotLabel(clinic),
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppColors.slate500,
                        fontFeatures: kTabularFigures,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              const AppBadge(tone: BadgeTone.ok, label: '배정됨'),
            ],
          ),
          const SizedBox(height: 8),
          // 취소 버튼은 없다(10-2). 못 가면 다른 시각으로 옮긴다.
          OutlinedButton(
            key: ValueKey('clinic-change-${clinic.clinicId}'),
            onPressed: () => _openClinicChange(data, clinic),
            style: _outlineStyle,
            child: const Text('시간 변경'),
          ),
        ],
      ),
    );
  }
}

final ButtonStyle _outlineStyle = OutlinedButton.styleFrom(
  foregroundColor: AppColors.slate700,
  side: const BorderSide(color: AppColors.slate300),
  minimumSize: const Size.fromHeight(40),
  shape: RoundedRectangleBorder(
    borderRadius: BorderRadius.circular(AppRadii.lg),
  ),
  textStyle: const TextStyle(fontSize: 14),
);

/// 구획 머리 옆의 작은 남색 버튼. 웹 `bg-brand-900 px-3 py-2 text-sm`.
class _SmallFilledButton extends StatelessWidget {
  const _SmallFilledButton({
    super.key,
    required this.label,
    required this.onPressed,
  });

  final String label;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) => FilledButton(
    onPressed: onPressed,
    style: FilledButton.styleFrom(
      backgroundColor: AppColors.brand900,
      foregroundColor: Colors.white,
      minimumSize: const Size(0, 36),
      padding: const EdgeInsets.symmetric(horizontal: 12),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppRadii.lg),
      ),
      textStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
    ),
    child: Text(label),
  );
}

/// 아래에서 올라오는 시트. 성공하면 `true` 로 닫힌다.
///
/// 루트 내비게이터에 띄운다 — 하단 탭 바 위에 떠야 한다. 키보드가 올라오면
/// 그만큼 밀어 올린다.
Future<bool?> _showSheet(BuildContext context, Widget child) {
  return showModalBottomSheet<bool>(
    context: context,
    useRootNavigator: true,
    isScrollControlled: true,
    backgroundColor: Colors.white,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (context) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(16, 20, 16, 16),
          child: child,
        ),
      ),
    ),
  );
}

class _SheetTitle extends StatelessWidget {
  const _SheetTitle(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: Text(
      text,
      style: const TextStyle(
        fontSize: 17,
        fontWeight: FontWeight.w700,
        color: AppColors.brand900,
      ),
    ),
  );
}

class _FieldLabel extends StatelessWidget {
  const _FieldLabel(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 6),
    child: Text(
      text,
      style: const TextStyle(
        fontSize: 14,
        fontWeight: FontWeight.w500,
        color: AppColors.slate700,
      ),
    ),
  );
}

class _SheetNote extends StatelessWidget {
  const _SheetNote(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(top: 8),
    child: Text(
      text,
      style: const TextStyle(fontSize: 12, color: AppColors.slate500),
    ),
  );
}

/// 드롭다운 한 칸. 글자 16(13-4).
Widget _dropdown<T>({
  required Key key,
  required T? value,
  required String hint,
  required List<(T, String)> items,
  required ValueChanged<T?>? onChanged,
}) {
  final border = OutlineInputBorder(
    borderRadius: BorderRadius.circular(AppRadii.xl),
    borderSide: const BorderSide(color: AppColors.slate300),
  );
  return DropdownButtonFormField<T>(
    key: key,
    initialValue: value,
    isExpanded: true,
    hint: Text(hint, style: const TextStyle(color: AppColors.slate400)),
    style: const TextStyle(fontSize: 16, color: AppColors.slate900),
    decoration: InputDecoration(
      filled: true,
      fillColor: onChanged == null ? AppColors.slate100 : Colors.white,
      contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      enabledBorder: border,
      disabledBorder: border,
      focusedBorder: border.copyWith(
        borderSide: const BorderSide(color: AppColors.brand600),
      ),
    ),
    items: [
      for (final (v, label) in items)
        DropdownMenuItem<T>(
          value: v,
          child: Text(label, overflow: TextOverflow.ellipsis),
        ),
    ],
    onChanged: onChanged,
  );
}

String _messageOf(Object e, String fallback) =>
    e is ApiException ? e.message : fallback;

/// 시간 변경 · 다른 클리닉으로 이동. **선생님 승인이 없다** — 저장하면 즉시
/// 바뀐다(10-1).
///
/// **사유가 필수다.** 승인 단계가 없어서 이 문장이 선생님에게 남는 유일한
/// 설명이다. 선택 입력으로 바꾸지 마라.
class _ClinicChangeSheet extends StatefulWidget {
  const _ClinicChangeSheet({
    required this.clinic,
    required this.candidates,
    required this.repository,
  });

  final StudentClinic clinic;
  final List<StudentClinic> candidates;
  final StudentScheduleRepository repository;

  @override
  State<_ClinicChangeSheet> createState() => _ClinicChangeSheetState();
}

class _ClinicChangeSheetState extends State<_ClinicChangeSheet> {
  /// null 이면 「그대로 (시간만 변경)」.
  int? _targetId;
  late String _arrival = widget.clinic.myReservation!.arrivalTime;
  final _reason = TextEditingController();
  bool _pending = false;
  String? _error;

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  StudentClinic get _target => _targetId == null
      ? widget.clinic
      : widget.candidates.firstWhere(
          (c) => c.clinicId == _targetId,
          orElse: () => widget.clinic,
        );

  /// 옮길 클리닉이 바뀌면 슬롯 목록도 바뀐다. 고른 시각이 새 목록에 없으면
  /// 첫 슬롯이다(웹과 같다) — 없는 시각을 보내면 서버가 거절한다.
  String? get _effectiveArrival {
    final slots = _target.slots;
    if (slots.isEmpty) return null;
    return slots.contains(_arrival) ? _arrival : slots.first;
  }

  Future<void> _save() async {
    final arrival = _effectiveArrival;
    if (arrival == null) return;
    setState(() {
      _pending = true;
      _error = null;
    });
    try {
      await widget.repository.changeClinic(
        widget.clinic.clinicId,
        targetClinicId: _targetId,
        arrivalTime: arrival,
        reason: _reason.text.trim(),
      );
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) {
        setState(() {
          _pending = false;
          _error = _messageOf(e, '변경하지 못했습니다.');
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final slots = _target.slots;
    final arrival = _effectiveArrival;
    final canSave = _reason.text.trim().isNotEmpty && arrival != null;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        const _SheetTitle('시간 변경'),
        Text(
          '현재 ${clinicSlotLabel(widget.clinic)} · '
          '${widget.clinic.myReservation!.arrivalTime} 도착',
          style: const TextStyle(
            fontSize: 14,
            color: AppColors.slate600,
            fontFeatures: kTabularFigures,
          ),
        ),
        const SizedBox(height: 12),
        const _FieldLabel('클리닉'),
        _dropdown<int?>(
          key: const Key('change-target'),
          value: _targetId,
          hint: '그대로 (시간만 변경)',
          items: [
            (null, '그대로 (시간만 변경)'),
            for (final c in widget.candidates) (c.clinicId, clinicSlotLabel(c)),
          ],
          onChanged: (v) => setState(() => _targetId = v),
        ),
        const SizedBox(height: 12),
        const _FieldLabel('도착 시간'),
        if (slots.isEmpty)
          const Text(
            '고를 수 있는 시간이 없습니다.',
            style: TextStyle(fontSize: 12, color: AppColors.slate400),
          )
        else
          // 슬롯이 대여섯 개라 드롭다운보다 버튼이 빠르다.
          Wrap(
            spacing: 4,
            runSpacing: 4,
            children: [
              for (final slot in slots)
                _SlotButton(
                  slot: slot,
                  selected: slot == arrival,
                  onTap: () => setState(() => _arrival = slot),
                ),
            ],
          ),
        const SizedBox(height: 12),
        AppTextArea(
          key: const Key('change-reason'),
          label: '사유 (필수)',
          controller: _reason,
          maxLength: _kReasonMax,
          onChanged: (_) => setState(() {}),
        ),
        if (_error != null) ...[
          const SizedBox(height: 8),
          FormError(message: _error),
        ],
        const SizedBox(height: 16),
        SubmitButton(
          key: const Key('change-submit'),
          label: '변경하기',
          pending: _pending,
          onPressed: canSave ? _save : null,
        ),
        const _SheetNote('바로 반영됩니다. 적으신 사유는 선생님과 학부모님께 그대로 전달됩니다.'),
      ],
    );
  }
}

class _SlotButton extends StatelessWidget {
  const _SlotButton({
    required this.slot,
    required this.selected,
    required this.onTap,
  });

  final String slot;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      selected: selected,
      button: true,
      child: Material(
        color: selected ? AppColors.brand900 : Colors.white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadii.lg),
          side: selected
              ? BorderSide.none
              : const BorderSide(color: AppColors.slate300),
        ),
        child: InkWell(
          key: ValueKey('slot-$slot'),
          onTap: onTap,
          customBorder: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppRadii.lg),
          ),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Text(
              slot,
              style: TextStyle(
                fontSize: 14,
                fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
                color: selected ? Colors.white : AppColors.slate700,
                fontFeatures: kTabularFigures,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 수업일 변경 요청. 못 가는 내 수업을 먼저 고르면 **그 주(월~일)** 의 다른 반
/// 수업이 후보로 뜬다 — 고를 때마다 서버에 다시 묻는다.
class _LessonChangeSheet extends StatefulWidget {
  const _LessonChangeSheet({required this.repository});

  final StudentScheduleRepository repository;

  @override
  State<_LessonChangeSheet> createState() => _LessonChangeSheetState();
}

class _LessonChangeSheetState extends State<_LessonChangeSheet> {
  List<LessonSlot>? _myLessons;
  List<LessonSlot>? _candidates;
  int? _fromId;
  int? _toId;

  /// 후보 요청의 세대. A→B 로 빠르게 바꾸면 A 의 늦은 응답이 B 의 후보를
  /// 덮는다(14-5 와 같은 결함).
  int _generation = 0;
  final _reason = TextEditingController();
  bool _pending = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadMyLessons();
  }

  @override
  void dispose() {
    _reason.dispose();
    super.dispose();
  }

  Future<void> _loadMyLessons() async {
    try {
      final lessons = await widget.repository.changeableLessons();
      if (mounted) setState(() => _myLessons = lessons);
    } catch (e) {
      if (mounted) {
        setState(() => _error = _messageOf(e, '수업 목록을 불러오지 못했습니다.'));
      }
    }
  }

  Future<void> _pickFrom(int? id) async {
    final gen = ++_generation;
    setState(() {
      _fromId = id;
      // 주가 바뀌면 후보가 통째로 달라진다. 이전 선택을 남기면 화면에 없는
      // 수업이 그대로 제출된다.
      _toId = null;
      _candidates = null;
      _error = null;
    });
    if (id == null) return;
    try {
      final list = await widget.repository.changeCandidates(id);
      if (mounted && gen == _generation) setState(() => _candidates = list);
    } catch (e) {
      if (mounted && gen == _generation) {
        setState(() => _error = _messageOf(e, '후보를 불러오지 못했습니다.'));
      }
    }
  }

  Future<void> _send() async {
    setState(() {
      _pending = true;
      _error = null;
    });
    try {
      await widget.repository.requestLessonChange(
        fromLessonId: _fromId!,
        toLessonId: _toId!,
        reason: _reason.text.trim(),
      );
      if (mounted) Navigator.of(context).pop(true);
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _pending = false;
          _error = e.code == 'DUPLICATE_RESOURCE'
              ? '이미 그 수업에 변경 요청을 보냈습니다.'
              : e.message;
        });
      }
    } catch (_) {
      if (mounted) {
        setState(() {
          _pending = false;
          _error = '요청을 보내지 못했습니다.';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final mine = _myLessons;
    final candidates = _candidates;
    final canSend =
        _fromId != null && _toId != null && _reason.text.trim().isNotEmpty;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        const _SheetTitle('수업일 변경 요청'),
        const _FieldLabel('못 가는 수업'),
        _dropdown<int>(
          key: const Key('lc-from'),
          value: _fromId,
          hint: mine == null ? '불러오는 중…' : '선택하세요',
          items: [
            for (final l in mine ?? const <LessonSlot>[])
              (l.lessonId, lessonSlotLabel(l)),
          ],
          onChanged: mine == null ? null : _pickFrom,
        ),
        if (mine != null && mine.isEmpty)
          const _SheetNote('앞으로 한 달 안에 예정된 수업이 없습니다.'),
        const SizedBox(height: 12),
        const _FieldLabel('대신 갈 수업'),
        _dropdown<int>(
          // 후보가 바뀔 때 칸을 새로 만든다 — 옛 선택이 남지 않게.
          key: ValueKey('lc-to-$_fromId-${candidates?.length}'),
          value: _toId,
          hint: _fromId == null ? '못 가는 수업을 먼저 고르세요' : '선택하세요',
          items: [
            for (final l in candidates ?? const <LessonSlot>[])
              (l.lessonId, lessonSlotLabel(l)),
          ],
          onChanged: candidates == null
              ? null
              : (v) => setState(() => _toId = v),
        ),
        if (_fromId != null && candidates != null && candidates.isEmpty)
          const _SheetNote('그 주에 갈 수 있는 다른 반 수업이 없습니다.'),
        const SizedBox(height: 12),
        // 사유가 곧 공지 본문이다. 선택지 목록이 미확정이라 자유 텍스트다.
        AppTextArea(
          key: const Key('lc-reason'),
          label: '변경 사유 (필수)',
          controller: _reason,
          maxLength: _kReasonMax,
          onChanged: (_) => setState(() {}),
        ),
        if (_error != null) ...[
          const SizedBox(height: 8),
          FormError(message: _error),
        ],
        const SizedBox(height: 16),
        SubmitButton(
          key: const Key('lc-submit'),
          label: '요청 보내기',
          pending: _pending,
          onPressed: canSend ? _send : null,
        ),
        const _SheetNote(
          '선생님이 승인하면 학생·학부모에게 변경 안내 공지가 올라갑니다. 반 배정이 '
          '바뀌는 것은 아니라서, 원래 반 출석은 선생님이 따로 처리합니다.',
        ),
      ],
    );
  }
}
