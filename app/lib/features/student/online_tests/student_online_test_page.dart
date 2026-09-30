import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:academy_app/shared/lib/homework_labels.dart';
import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/router/auth_redirect.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';

import 'package:academy_app/shared/lib/url_opener.dart';

import '../../../shared/widgets/app_badge.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/form_error.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/sub_page.dart';
import '../../../shared/widgets/submit_button.dart';
import 'online_test_data.dart';

/// 답을 고를 때마다가 아니라 이 간격으로 모아 저장한다 — 25문항을 연타해도
/// 요청이 25번 나가지 않는다. 웹 `AUTOSAVE_DELAY_MS` 와 같다.
const Duration kAutosaveDelay = Duration(milliseconds: 1500);

/// S-10 응시 + 결과. 웹 정본은 `StudentOnlineTestTakePage.tsx`.
///
/// 종이 시험지를 풀고 **답만** 입력한다 — 문제지는 화면에 없다.
///
/// **임시 저장은 서버에 한다.** 25문항에 20~30분이 걸려서 앱이 죽으면 처음부터
/// 다시 해야 한다. 고른 뒤 [kAutosaveDelay] 가 지나면, 그리고 앱이 뒤로
/// 가거나 이 화면을 떠날 때 남은 답을 흘려 보낸다.
///
/// **마감은 기기 시계로 판정한다 — 이 화면만의 예외다**(2026-09-27 사용자
/// 결정, [remainingMinutesOf]). 웹 응시 화면과 같다. 제출·저장의 최종 판정은
/// 서버다.
///
/// [onSubmitted] 는 제출이 성공하면 부른다 — 목록의 상태와 성적(클리닉 칸
/// 자동 반영)이 낡았다.
class StudentOnlineTestPage extends StatefulWidget {
  const StudentOnlineTestPage({
    super.key,
    required this.testId,
    required this.controller,
    required this.openUrl,
    required this.onSubmitted,
    this.now = DateTime.now,
  });

  final int testId;
  final OnlineTestTakeController controller;
  final UrlOpener openUrl;
  final VoidCallback onSubmitted;

  /// 테스트가 시계를 고정한다.
  final DateTime Function() now;

  @override
  State<StudentOnlineTestPage> createState() => _StudentOnlineTestPageState();
}

class _StudentOnlineTestPageState extends State<StudentOnlineTestPage>
    with ReappearReload<StudentOnlineTestPage> {
  /// 화면이 들고 있는 답. **한 번만** 채운다 — 다시 받아도 덮지 않는다(고르는
  /// 중에 새로고침이 오면 방금 고른 답이 사라진다). 컨트롤러의 초안이 서버 응답보다
  /// 먼저다([OnlineTestTakeController.draftOf]).
  List<int?>? _answers;
  bool _dirty = false;
  Timer? _timer;
  DateTime? _savedAt;
  String? _saveError;

  OnlineTestResult? _result;
  bool _submitting = false;
  String? _submitError;
  late final AppLifecycleListener _lifecycle;

  int get _id => widget.testId;

  @override
  void initState() {
    super.initState();
    // 앱이 뒤로 가면 남은 답을 바로 저장한다(웹 visibilitychange).
    _lifecycle = AppLifecycleListener(onHide: _flush);
    widget.controller.addListener(_onChanged);
    widget.controller.load(_id);
  }

  @override
  void didUpdateWidget(covariant StudentOnlineTestPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller ||
        oldWidget.testId != widget.testId) {
      _flushFor(oldWidget.testId, oldWidget.controller);
      oldWidget.controller.removeListener(_onChanged);
      _answers = null;
      _result = null;
      _savedAt = null;
      _saveError = null;
      _submitError = null;
      widget.controller.addListener(_onChanged);
      widget.controller.load(_id);
    }
  }

  @override
  void dispose() {
    // 떠날 때 남은 답을 저장한다. 결과는 기다리지 않는다.
    _flushFor(_id, widget.controller);
    _lifecycle.dispose();
    widget.controller.removeListener(_onChanged);
    super.dispose();
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  /// 한 번 채운다. 빌드에서 부른다 — 컨트롤러가 이미 데이터를 들고 있으면(60초
  /// 안에 다시 들어옴) 알림이 오지 않아서다. 그때의 [OnlineTestTakeController.data]
  /// 는 처음 받은 답이라 떠나기 전에 고른 답이 없다 — 초안을 먼저 본다.
  void _seedAnswers() {
    final view = widget.controller.data;
    if (_answers == null &&
        view != null &&
        view.take.testId == _id &&
        view.result == null) {
      final draft = widget.controller.draftOf(_id);
      _answers = [...draft ?? view.take.chosenChoices];
      // 떠날 때의 저장이 실패했을 수 있다. 서버 답과 다르면 아직 안 나간 답으로 둔다 —
      // 제출·자동 저장이 먼저 흘려 보낸다.
      if (draft != null && !listEquals(draft, view.take.chosenChoices)) {
        _dirty = true;
      }
    }
  }

  @override
  void onReappear() => widget.controller.load(_id);

  void _pick(int index, int choice) {
    final answers = _answers;
    if (answers == null) return;
    setState(() {
      // 같은 번호를 다시 누르면 선택 해제다 — 틀린 답 확정보다 미체크가 낫다.
      answers[index] = answers[index] == choice ? null : choice;
      _dirty = true;
      widget.controller.keepDraft(_id, answers);
      _saveError = null;
    });
    _timer?.cancel();
    _timer = Timer(kAutosaveDelay, _flush);
  }

  /// 남은 답이 있으면 저장한다. 실패하면 다시 「남은 답」으로 돌려 둔다.
  Future<void> _flush() {
    _timer?.cancel();
    final answers = _answers;
    final c = widget.controller;
    if (!_dirty || answers == null || _result != null) return c.saving;
    _dirty = false;
    final snapshot = [...answers];
    return c.saving = c.saving.then((_) async {
      try {
        await widget.controller.repository.saveAnswers(_id, snapshot);
        if (mounted) setState(() => _savedAt = DateTime.now());
      } catch (e) {
        _dirty = true;
        if (mounted) {
          setState(
            () => _saveError = e is ApiException
                ? e.message
                : '답을 저장하지 못했습니다. 연결을 확인해 주세요.',
          );
        }
      }
    });
  }

  /// 화면을 떠날 때. `setState` 없이 저장만 흘려 보낸다.
  void _flushFor(int testId, OnlineTestTakeController controller) {
    _timer?.cancel();
    final answers = _answers;
    if (!_dirty || answers == null || _result != null) return;
    _dirty = false;
    final snapshot = [...answers];
    controller.saving = controller.saving
        .then((_) => controller.repository.saveAnswers(testId, snapshot))
        .catchError((_) {});
  }

  Future<void> _submit() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        content: const Text('제출하면 답을 바꿀 수 없습니다. 제출하시겠습니까?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('취소'),
          ),
          TextButton(
            key: const Key('confirm-submit'),
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('제출'),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    setState(() {
      _submitting = true;
      _submitError = null;
    });
    // 서버는 **마지막으로 저장된 답**으로 채점한다. 방금 고른 답이 아직 안
    // 나갔으면 그 문항이 빠진다 — 먼저 흘려 보내고 끝까지 기다린다.
    await _flush();
    if (!mounted) return;
    if (_dirty) {
      setState(() {
        _submitting = false;
        _submitError = _saveError ?? '답을 저장하지 못해 제출하지 않았습니다.';
      });
      return;
    }
    try {
      final result = await widget.controller.repository.submit(_id);
      widget.controller.clearDraft(_id);
      if (!mounted) return;
      widget.controller.markStale();
      widget.onSubmitted();
      setState(() {
        _result = result;
        _submitting = false;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _submitting = false;
          _submitError = e is ApiException ? e.message : '제출하지 못했습니다.';
        });
      }
    }
  }

  void _toList() => context.go(AppRoutes.studentOnlineTests);

  @override
  Widget build(BuildContext context) {
    _seedAnswers();
    final c = widget.controller;
    final view = c.data;
    final result = _result ?? view?.result;
    return SubPageScroll(
      role: '학생',
      onRefresh: c.refresh,
      children: [
        PageBackLink(label: '테스트 목록', onTap: _toList),
        if (view == null)
          c.status == HomeStatus.error
              ? HomeErrorView(message: c.error, onRetry: c.refresh)
              : const SizedBox(height: 160, child: FullScreenLoader())
        else if (result != null)
          ..._resultView(result)
        else
          ..._takeView(view.take),
      ],
    );
  }

  // ── 응시 ──────────────────────────────────────────────

  List<Widget> _takeView(OnlineTestTake test) {
    final answers = _answers ?? test.chosenChoices;
    final now = widget.now();
    final closesAt = test.closesAt;
    final remaining = remainingMinutesOf(closesAt, now);
    final closed = closesAt != null && DateTime.parse(closesAt).isBefore(now);
    final answered = answers.where((a) => a != null).length;
    final empty = test.questionCount - answered;
    final savedAt = _savedAt;
    return [
      AppCard(
        key: const Key('test-header'),
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              test.title,
              style: const TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w600,
                color: AppColors.brand900,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              '${test.classRoomName} · ${test.questionCount}문항',
              style: const TextStyle(
                fontSize: 12,
                color: AppColors.slate500,
                fontFeatures: kTabularFigures,
              ),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 4,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                AppBadge(
                  key: const Key('answered-count'),
                  label: '$answered / ${test.questionCount} 입력',
                ),
                if (remaining != null)
                  Text(
                    remainingLabel(remaining),
                    key: const Key('test-remaining'),
                    style: TextStyle(
                      fontSize: 12,
                      color: closed ? AppColors.amber700 : AppColors.slate500,
                      fontFeatures: kTabularFigures,
                    ),
                  ),
                if (savedAt != null)
                  Text(
                    '${_hms(savedAt)} 저장됨',
                    key: const Key('saved-at'),
                    style: const TextStyle(
                      fontSize: 12,
                      color: AppColors.emerald700,
                      fontFeatures: kTabularFigures,
                    ),
                  ),
              ],
            ),
          ],
        ),
      ),
      const SizedBox(height: 16),
      if (closed)
        const AppCard(
          key: Key('test-closed'),
          padding: EdgeInsets.all(16),
          child: Text(
            '마감 시간이 지나 제출할 수 없습니다.',
            style: TextStyle(fontSize: 14, color: AppColors.amber700),
          ),
        )
      else ...[
        for (var i = 0; i < test.questionCount; i++) ...[
          if (i > 0) const SizedBox(height: 8),
          _question(i, test.choiceCount, answers[i]),
        ],
        const SizedBox(height: 16),
        if (_saveError != null) ...[
          FormError(message: _saveError),
          const SizedBox(height: 12),
        ],
        if (_submitError != null) ...[
          FormError(message: _submitError),
          const SizedBox(height: 12),
        ],
        if (empty > 0) ...[
          Text(
            '$empty문항이 비어 있습니다. 미체크는 오답으로 처리됩니다.',
            key: const Key('empty-warning'),
            style: const TextStyle(fontSize: 12, color: AppColors.amber700),
          ),
          const SizedBox(height: 8),
        ],
        SubmitButton(
          key: const Key('test-submit'),
          label: '제출하고 채점받기',
          pending: _submitting,
          onPressed: _submit,
        ),
      ],
    ];
  }

  static String _hms(DateTime t) =>
      '${t.hour.toString().padLeft(2, '0')}:'
      '${t.minute.toString().padLeft(2, '0')}:'
      '${t.second.toString().padLeft(2, '0')}';

  Widget _question(int index, int choiceCount, int? picked) {
    return AppCard(
      key: ValueKey('q-$index'),
      padding: const EdgeInsets.all(12),
      child: Row(
        children: [
          SizedBox(
            width: 28,
            child: Text(
              '${index + 1}',
              style: const TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w500,
                color: AppColors.slate500,
                fontFeatures: kTabularFigures,
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                for (var choice = 1; choice <= choiceCount; choice++)
                  _choice(index, choice, picked == choice),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _choice(int index, int choice, bool selected) {
    return Semantics(
      button: true,
      selected: selected,
      label: '${index + 1}번 $choice',
      excludeSemantics: true,
      child: Material(
        color: selected ? AppColors.brand900 : Colors.white,
        shape: CircleBorder(
          side: BorderSide(
            color: selected ? AppColors.brand900 : AppColors.slate300,
          ),
        ),
        child: InkWell(
          key: ValueKey('q-$index-$choice'),
          customBorder: const CircleBorder(),
          onTap: () => _pick(index, choice),
          child: SizedBox.square(
            dimension: 40,
            child: Center(
              child: Text(
                '$choice',
                style: TextStyle(
                  fontSize: 14,
                  fontWeight: selected ? FontWeight.w600 : FontWeight.w400,
                  color: selected ? Colors.white : AppColors.slate600,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  // ── 결과 ──────────────────────────────────────────────

  List<Widget> _resultView(OnlineTestResult r) {
    const big = TextStyle(
      fontSize: 30,
      fontWeight: FontWeight.w700,
      color: AppColors.brand900,
      fontFeatures: kTabularFigures,
    );
    final urls = r.answerFileUrls;
    return [
      AppCard(
        key: const Key('result-header'),
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Text(
              r.title,
              textAlign: TextAlign.center,
              style: const TextStyle(fontSize: 14, color: AppColors.slate500),
            ),
            const SizedBox(height: 4),
            // 환산 점수를 보여주지 않는다(2026-09-10). 내부·외부가 있으면 그것이
            // 주인공이다 — 클리닉 테스트의 대체본이라 성적 칸에 들어가는 값이다.
            if (r.split) ...[
              Text(
                '내부 ${r.internalCorrect}/${r.internalQuestionCount}',
                style: big,
              ),
              const SizedBox(height: 4),
              Text(
                '외부 ${r.externalCorrect}/'
                '${r.questionCount - r.internalQuestionCount!}',
                style: big,
              ),
            ] else
              Text('${r.correctCount} / ${r.questionCount}', style: big),
          ],
        ),
      ),
      // 해설지는 여러 장일 수 있다(2026-09-29). 한 장이면 예전 문구 그대로다.
      for (var i = 0; i < urls.length; i++) ...[
        SizedBox(height: i == 0 ? 16 : 8),
        OutlinedButton(
          key: Key(i == 0 ? 'answer-file' : 'answer-file-$i'),
          onPressed: () => widget.openUrl(Uri.parse(urls[i])),
          style: OutlinedButton.styleFrom(
            backgroundColor: Colors.white,
            foregroundColor: AppColors.brand900,
            side: const BorderSide(color: AppColors.slate300),
            minimumSize: const Size.fromHeight(48),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(AppRadii.xl),
            ),
            textStyle: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w500,
            ),
          ),
          child: Text(urls.length > 1 ? '해설지 ${i + 1} 보기' : '해설지 보기'),
        ),
      ],
      const SizedBox(height: 16),
      AppCard(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              '문항별 정오',
              style: TextStyle(
                fontSize: 14,
                fontWeight: FontWeight.w600,
                color: AppColors.brand900,
              ),
            ),
            const SizedBox(height: 8),
            for (final q in r.results) ...[
              if (q.questionNo > 1) const SizedBox(height: 4),
              _resultRow(q),
            ],
          ],
        ),
      ),
    ];
  }

  Widget _resultRow(QuestionResult q) {
    return Container(
      key: ValueKey('result-${q.questionNo}'),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: AppColors.slate50,
        borderRadius: BorderRadius.circular(AppRadii.lg),
      ),
      child: Row(
        children: [
          Text(
            '${q.questionNo}번',
            style: const TextStyle(fontSize: 14, color: AppColors.slate500),
          ),
          const Spacer(),
          Text(
            q.isCorrect
                ? '정답 ${q.correct}'
                : '${q.chosen ?? '미체크'} → 정답 ${q.correct}',
            style: TextStyle(
              fontSize: 14,
              // 오답은 숙제 ❌ 와 같은 빨강이다.
              color: q.isCorrect ? AppColors.emerald700 : AppColors.red600,
              fontFeatures: kTabularFigures,
            ),
          ),
        ],
      ),
    );
  }
}
