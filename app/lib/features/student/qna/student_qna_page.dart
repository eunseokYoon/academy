import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/router/auth_redirect.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/app_text_area.dart';
import '../../../shared/widgets/app_text_field.dart';
import '../../../shared/widgets/form_error.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/star_rating.dart';
import '../../../shared/widgets/sub_page.dart';
import '../../../shared/widgets/submit_button.dart';
import '../homeworks/submission_media.dart';
import 'qna_data.dart';
import 'qna_photos.dart';

/// S-9 질의응답 목록(하단 탭 「질문」). 웹 정본은 `StudentQnaPage.tsx` 다.
///
/// **상태 배지가 없다**(2026-08-16 확정). 답변 완료·미답변을 만들지 마라.
///
/// **공개 기본값은 비공개다.** 기본이 공개면 학생이 성적이나 개인 사정을
/// 무심코 같은 반 20명 앞에 쓰게 된다.
///
/// 수강 후기도 여기서 쓴다(웹과 같은 자리). 학생당 하나라 목록이 없고, 다른
/// 학생 후기는 보이지 않는다(「만들지 마라」).
class StudentQnaPage extends StatefulWidget {
  const StudentQnaPage({
    super.key,
    required this.controller,
    required this.uploader,
    required this.picker,
  });

  final QnaBoardController controller;
  final QnaUploader uploader;
  final MediaPicker picker;

  @override
  State<StudentQnaPage> createState() => _StudentQnaPageState();
}

enum _Open { none, question, review }

class _StudentQnaPageState extends State<StudentQnaPage>
    with ReappearReload<StudentQnaPage> {
  _Open _open = _Open.none;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onChanged);
    widget.controller.load();
  }

  @override
  void didUpdateWidget(covariant StudentQnaPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onChanged);
      widget.controller.addListener(_onChanged);
      widget.controller.load();
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

  @override
  void onReappear() => widget.controller.load();

  void _toggle(_Open which) =>
      setState(() => _open = _open == which ? _Open.none : which);

  Future<void> _done() async {
    setState(() => _open = _Open.none);
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
        PageTitle(
          title: '질의응답',
          action: data == null
              ? null
              : Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    _headButton(
                      key: const Key('qna-review-toggle'),
                      label: data.myReview == null ? '수강 후기' : '내 후기',
                      primary: false,
                      onPressed: () => _toggle(_Open.review),
                    ),
                    const SizedBox(width: 8),
                    _headButton(
                      key: const Key('qna-write-toggle'),
                      label: _open == _Open.question ? '닫기' : '질문하기',
                      primary: true,
                      onPressed: () => _toggle(_Open.question),
                    ),
                  ],
                ),
        ),
        if (data == null)
          c.status == HomeStatus.error
              ? HomeErrorView(message: c.error, onRetry: c.refresh)
              : const SizedBox(height: 160, child: FullScreenLoader())
        else ...[
          if (_open == _Open.review) ...[
            _ReviewForm(
              key: const Key('qna-review-form'),
              repository: c.repository,
              myReview: data.myReview,
              onDone: _done,
            ),
            const SizedBox(height: 16),
          ],
          if (_open == _Open.question) ...[
            _QuestionForm(
              key: const Key('qna-question-form'),
              repository: c.repository,
              classRooms: data.classRooms,
              uploader: widget.uploader,
              picker: widget.picker,
              onDone: _done,
            ),
            const SizedBox(height: 16),
          ],
          if (data.items.isEmpty)
            const AppCard(
              padding: EdgeInsets.all(24),
              child: Text(
                '아직 질문이 없습니다. 궁금한 걸 물어보세요.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 14, color: AppColors.slate500),
              ),
            )
          else
            for (final (i, item) in data.items.indexed) ...[
              if (i > 0) const SizedBox(height: 8),
              _row(item),
            ],
        ],
      ],
    );
  }

  Widget _headButton({
    required Key key,
    required String label,
    required bool primary,
    required VoidCallback onPressed,
  }) {
    final shape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(AppRadii.lg),
    );
    const text = TextStyle(fontSize: 14, fontWeight: FontWeight.w500);
    const size = Size(0, 34);
    const padding = EdgeInsets.symmetric(horizontal: 12);
    return primary
        ? FilledButton(
            key: key,
            onPressed: onPressed,
            style: FilledButton.styleFrom(
              backgroundColor: AppColors.brand900,
              foregroundColor: Colors.white,
              minimumSize: size,
              padding: padding,
              shape: shape,
              textStyle: text,
            ),
            child: Text(label),
          )
        : OutlinedButton(
            key: key,
            onPressed: onPressed,
            style: OutlinedButton.styleFrom(
              backgroundColor: AppColors.brand50,
              foregroundColor: AppColors.brand700,
              side: const BorderSide(color: AppColors.brand200),
              minimumSize: size,
              padding: padding,
              shape: shape,
              textStyle: text,
            ),
            child: Text(label),
          );
  }

  Widget _row(QnaSummary item) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(AppRadii.xl),
      child: InkWell(
        key: ValueKey('qna-${item.postId}'),
        borderRadius: BorderRadius.circular(AppRadii.xl),
        onTap: () => context.go('${AppRoutes.studentQna}/${item.postId}'),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(AppRadii.xl),
            border: Border.all(color: AppColors.brand100),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Text(
                      '${item.isPublic ? '' : '🔒 '}${item.title}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w500,
                        color: AppColors.brand900,
                      ),
                    ),
                  ),
                  if (item.answerCount > 0) ...[
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: AppColors.brand50,
                        borderRadius: BorderRadius.circular(999),
                      ),
                      child: Text(
                        '답글 ${item.answerCount}',
                        style: const TextStyle(
                          fontSize: 12,
                          color: AppColors.brand600,
                          fontFeatures: kTabularFigures,
                        ),
                      ),
                    ),
                  ],
                ],
              ),
              const SizedBox(height: 4),
              Text(
                '${item.authorName} · ${item.classRoomName}'
                '${item.hasPhoto ? ' · 사진' : ''}',
                style: const TextStyle(fontSize: 12, color: AppColors.slate500),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 흰 카드 + 옅은 남색 테두리. 웹 `rounded-xl bg-white p-4 ring-1 ring-brand-100`.
class _FormCard extends StatelessWidget {
  const _FormCard({required this.children});

  final List<Widget> children;

  // Material 이어야 한다 — 안의 체크박스 줄(ListTile)이 잉크를 가장 가까운
  // Material 에 그려서, 색 있는 Container 로 두면 가려진다(Flutter 가 막는다).
  @override
  Widget build(BuildContext context) => Material(
    color: Colors.white,
    shape: RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(AppRadii.xl),
      side: const BorderSide(color: AppColors.brand100),
    ),
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: children,
      ),
    ),
  );
}

class _QuestionForm extends StatefulWidget {
  const _QuestionForm({
    super.key,
    required this.repository,
    required this.classRooms,
    required this.uploader,
    required this.picker,
    required this.onDone,
  });

  final QnaRepository repository;
  final List<QnaClassRoom> classRooms;
  final QnaUploader uploader;
  final MediaPicker picker;
  final Future<void> Function() onDone;

  @override
  State<_QuestionForm> createState() => _QuestionFormState();
}

class _QuestionFormState extends State<_QuestionForm> {
  late int? _classRoomId = widget.classRooms.isEmpty
      ? null
      : widget.classRooms.first.classRoomId;
  final _title = TextEditingController();
  final _content = TextEditingController();

  /// 기본은 비공개다(클래스 주석).
  bool _isPublic = false;
  List<QnaUploadedPhoto> _photos = const [];
  bool _uploading = false;
  bool _pending = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _title.addListener(_onText);
  }

  @override
  void dispose() {
    _title.removeListener(_onText);
    _title.dispose();
    _content.dispose();
    super.dispose();
  }

  void _onText() => setState(() {});

  Future<void> _send() async {
    setState(() {
      _pending = true;
      _error = null;
    });
    try {
      await widget.repository.create(
        classRoomId: _classRoomId!,
        title: _title.text.trim(),
        content: _content.text.trim(),
        isPublic: _isPublic,
        s3Keys: [for (final p in _photos) p.s3Key],
      );
      if (mounted) await widget.onDone();
    } catch (e) {
      if (mounted) {
        setState(() {
          _pending = false;
          _error = e is ApiException ? e.message : '올리지 못했습니다. 다시 시도해 주세요.';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final rooms = widget.classRooms;
    final canSend =
        _classRoomId != null &&
        _title.text.trim().isNotEmpty &&
        _content.text.trim().isNotEmpty &&
        !_uploading;
    return _FormCard(
      children: [
        // 반이 하나뿐이면 고를 것이 없으므로 아예 안 보인다.
        if (rooms.length > 1) ...[
          DropdownButtonFormField<int>(
            key: const Key('qna-class'),
            initialValue: _classRoomId,
            isExpanded: true,
            style: const TextStyle(fontSize: 16, color: AppColors.slate900),
            items: [
              for (final r in rooms)
                DropdownMenuItem(value: r.classRoomId, child: Text(r.name)),
            ],
            onChanged: (v) => setState(() => _classRoomId = v),
          ),
          const SizedBox(height: 12),
        ],
        if (rooms.isEmpty)
          const Padding(
            padding: EdgeInsets.only(bottom: 12),
            child: Text(
              '재원 중인 반이 없어 질문을 올릴 수 없습니다.',
              style: TextStyle(fontSize: 13, color: AppColors.slate500),
            ),
          ),
        AppTextField(
          key: const Key('qna-title'),
          label: '제목',
          controller: _title,
          inputFormatters: [LengthLimitingTextInputFormatter(kQnaTitleMax)],
        ),
        const SizedBox(height: 12),
        AppTextArea(
          key: const Key('qna-content'),
          label: '내용',
          placeholder: '궁금한 내용을 적어 주세요.',
          minLines: 5,
          controller: _content,
          onChanged: (_) => setState(() {}),
        ),
        const SizedBox(height: 12),
        QnaPhotoPicker(
          picker: widget.picker,
          uploader: widget.uploader,
          photos: _photos,
          onChanged: (v) => setState(() => _photos = v),
          onBusyChanged: (v) => setState(() => _uploading = v),
        ),
        const SizedBox(height: 8),
        CheckboxListTile(
          key: const Key('qna-public'),
          value: _isPublic,
          onChanged: (v) => setState(() => _isPublic = v ?? false),
          controlAffinity: ListTileControlAffinity.leading,
          contentPadding: EdgeInsets.zero,
          dense: true,
          activeColor: AppColors.brand600,
          title: const Text(
            '같은 반 학생들에게도 공개',
            style: TextStyle(fontSize: 14, color: AppColors.brand700),
          ),
        ),
        const Text(
          '공개하지 않으면 선생님만 볼 수 있습니다.',
          style: TextStyle(fontSize: 12, color: AppColors.slate500),
        ),
        if (_error != null) ...[
          const SizedBox(height: 12),
          FormError(message: _error),
        ],
        const SizedBox(height: 12),
        SubmitButton(
          key: const Key('qna-submit'),
          label: '질문 올리기',
          pending: _pending,
          onPressed: canSend ? _send : null,
        ),
      ],
    );
  }
}

/// 수강 후기. [myReview] 가 있으면 그 값으로 채운 수정 모드이고 「삭제」가
/// 함께 뜬다 — 새 후기를 두 번 만드는 길은 없다(학생당 하나, 12번).
class _ReviewForm extends StatefulWidget {
  const _ReviewForm({
    super.key,
    required this.repository,
    required this.myReview,
    required this.onDone,
  });

  final QnaRepository repository;
  final MyReview? myReview;
  final Future<void> Function() onDone;

  @override
  State<_ReviewForm> createState() => _ReviewFormState();
}

class _ReviewFormState extends State<_ReviewForm> {
  late double _rating = widget.myReview?.rating ?? 0;
  late final _content = TextEditingController(text: widget.myReview?.content);
  bool _pending = false;
  String? _error;

  @override
  void dispose() {
    _content.dispose();
    super.dispose();
  }

  /// 서버도 같은 조건으로 400 을 내지만, 그 전에 화면에서 먼저 막고 이유를
  /// 보여준다(웹과 같은 문구).
  String? get _missing {
    final noRating = _rating < 0.5;
    final noContent = _content.text.trim().isEmpty;
    if (noRating && noContent) return '별점과 내용을 모두 입력해 주세요.';
    if (noRating) return '별점을 선택해 주세요.';
    if (noContent) return '내용을 적어 주세요.';
    return null;
  }

  Future<void> _run(Future<void> Function() call, String fallback) async {
    setState(() {
      _pending = true;
      _error = null;
    });
    try {
      await call();
      if (mounted) await widget.onDone();
    } catch (e) {
      if (mounted) {
        setState(() {
          _pending = false;
          _error = e is ApiException ? e.message : fallback;
        });
      }
    }
  }

  Future<void> _save() => _run(
    () => widget.repository.saveReview(
      exists: widget.myReview != null,
      rating: _rating,
      content: _content.text.trim(),
    ),
    '저장하지 못했습니다.',
  );

  Future<void> _delete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        content: const Text('삭제하면 되돌릴 수 없습니다. 삭제할까요?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('취소'),
          ),
          TextButton(
            key: const Key('confirm-delete'),
            onPressed: () => Navigator.of(context).pop(true),
            style: TextButton.styleFrom(foregroundColor: AppColors.red600),
            child: const Text('삭제'),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    await _run(widget.repository.deleteReview, '삭제하지 못했습니다.');
  }

  @override
  Widget build(BuildContext context) {
    final missing = _missing;
    return _FormCard(
      children: [
        StarRating(
          value: _rating,
          onChanged: (v) => setState(() => _rating = v),
        ),
        const SizedBox(height: 12),
        AppTextArea(
          key: const Key('review-content'),
          label: null,
          placeholder: '수업은 어떠셨나요?',
          minLines: 5,
          controller: _content,
          onChanged: (_) => setState(() {}),
        ),
        if (missing != null) ...[
          const SizedBox(height: 8),
          Text(
            missing,
            key: const Key('review-missing'),
            style: const TextStyle(fontSize: 12, color: AppColors.red600),
          ),
        ],
        if (_error != null) ...[
          const SizedBox(height: 8),
          FormError(message: _error),
        ],
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: SubmitButton(
                key: const Key('review-save'),
                label: '저장',
                pending: _pending,
                onPressed: missing == null ? _save : null,
              ),
            ),
            if (widget.myReview != null) ...[
              const SizedBox(width: 8),
              OutlinedButton(
                key: const Key('review-delete'),
                onPressed: _pending ? null : _delete,
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.red600,
                  side: const BorderSide(color: AppColors.red200),
                  minimumSize: const Size(0, 52),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(AppRadii.xl),
                  ),
                ),
                child: const Text('삭제'),
              ),
            ],
          ],
        ),
      ],
    );
  }
}
