import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/router/auth_redirect.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/widgets/app_text_area.dart';
import '../../../shared/widgets/form_error.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/sub_page.dart';
import '../../../shared/widgets/submit_button.dart';
import '../homeworks/submission_media.dart';
import 'qna_data.dart';
import 'qna_photos.dart';

/// S-9 상세. 질문 하나와 그 아래 답글들이다. 웹 `StudentQnaDetailPage.tsx`.
///
/// **답글에 차례가 없다.** 학생도 선생님도 아무 때나 여러 번 쓴다 —
/// 「선생님 차례」를 계산해 입력을 잠그지 마라(확정). 대댓글도 없다.
///
/// 본문은 사용자 입력이라 글자 그대로 그린다(줄바꿈만 살린다).
///
/// [onChanged] 는 답글·삭제가 성공하면 부른다 — 목록의 답글 수가 낡았다.
class StudentQnaDetailPage extends StatefulWidget {
  const StudentQnaDetailPage({
    super.key,
    required this.postId,
    required this.controller,
    required this.uploader,
    required this.picker,
    required this.onChanged,
  });

  final int postId;
  final QnaDetailController controller;
  final QnaUploader uploader;
  final MediaPicker picker;
  final VoidCallback onChanged;

  @override
  State<StudentQnaDetailPage> createState() => _StudentQnaDetailPageState();
}

class _StudentQnaDetailPageState extends State<StudentQnaDetailPage>
    with ReappearReload<StudentQnaDetailPage> {
  final _content = TextEditingController();
  List<QnaUploadedPhoto> _photos = const [];
  bool _uploading = false;
  bool _pending = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onChanged);
    widget.controller.load(widget.postId);
  }

  @override
  void didUpdateWidget(covariant StudentQnaDetailPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller ||
        oldWidget.postId != widget.postId) {
      oldWidget.controller.removeListener(_onChanged);
      widget.controller.addListener(_onChanged);
      widget.controller.load(widget.postId);
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onChanged);
    _content.dispose();
    super.dispose();
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  @override
  void onReappear() => widget.controller.load(widget.postId);

  void _toList() => context.go(AppRoutes.studentQna);

  Future<void> _answer() async {
    setState(() {
      _pending = true;
      _error = null;
    });
    try {
      await widget.controller.repository.answer(
        widget.postId,
        content: _content.text.trim(),
        s3Keys: [for (final p in _photos) p.s3Key],
      );
      if (!mounted) return;
      _content.clear();
      setState(() {
        _photos = const [];
        _pending = false;
      });
      widget.onChanged();
      await widget.controller.reload();
    } catch (e) {
      if (mounted) {
        setState(() {
          _pending = false;
          _error = e is ApiException ? e.message : '답글을 올리지 못했습니다.';
        });
      }
    }
  }

  Future<void> _delete() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        content: const Text('질문을 지우면 답글도 함께 사라집니다. 지울까요?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('취소'),
          ),
          TextButton(
            key: const Key('confirm-delete'),
            onPressed: () => Navigator.of(context).pop(true),
            style: TextButton.styleFrom(foregroundColor: AppColors.red600),
            child: const Text('지우기'),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    setState(() => _error = null);
    try {
      await widget.controller.repository.delete(widget.postId);
      if (!mounted) return;
      widget.onChanged();
      _toList();
    } catch (e) {
      if (mounted) {
        setState(
          () => _error = e is ApiException ? e.message : '질문을 지우지 못했습니다.',
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final detail = c.data;
    return SubPageScroll(
      role: '학생',
      onRefresh: c.refresh,
      children: [
        PageBackLink(label: '질문 목록', onTap: _toList),
        if (detail == null)
          c.status == HomeStatus.error
              ? HomeErrorView(message: c.error, onRetry: c.refresh)
              : const SizedBox(height: 160, child: FullScreenLoader())
        else ...[
          _question(detail),
          for (final a in detail.answers) ...[
            const SizedBox(height: 8),
            _answerCard(a),
          ],
          const SizedBox(height: 16),
          _answerForm(),
        ],
      ],
    );
  }

  static BoxDecoration _box(Color color) => BoxDecoration(
    color: color,
    borderRadius: BorderRadius.circular(AppRadii.xl),
    border: Border.all(color: AppColors.brand100),
  );

  Widget _question(QnaDetail d) {
    return Container(
      key: const Key('qna-question'),
      padding: const EdgeInsets.all(16),
      decoration: _box(Colors.white),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '${d.isPublic ? '' : '🔒 '}${d.title}',
            style: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w700,
              color: AppColors.brand900,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            '${d.authorName} · ${d.classRoomName}',
            style: const TextStyle(fontSize: 12, color: AppColors.slate500),
          ),
          const SizedBox(height: 12),
          Text(
            d.content,
            style: const TextStyle(fontSize: 14, color: AppColors.slate700),
          ),
          QnaPhotoStrip(photos: d.photos),
          if (d.editable) ...[
            const SizedBox(height: 8),
            TextButton(
              key: const Key('qna-delete'),
              onPressed: _delete,
              style: TextButton.styleFrom(
                foregroundColor: AppColors.red600,
                padding: EdgeInsets.zero,
                minimumSize: const Size(0, 32),
                textStyle: const TextStyle(fontSize: 12),
              ),
              child: const Text('질문 삭제'),
            ),
          ],
        ],
      ),
    );
  }

  Widget _answerCard(QnaAnswer a) {
    return Container(
      key: ValueKey('qna-answer-${a.answerId}'),
      padding: const EdgeInsets.all(16),
      width: double.infinity,
      // 선생님 답글은 옅은 남색이다 — 누가 답했는지 이름을 읽지 않아도 보인다.
      decoration: _box(a.byTeacher ? AppColors.brand50 : Colors.white),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            a.authorName,
            style: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: AppColors.brand700,
            ),
          ),
          const SizedBox(height: 4),
          Text(
            a.content,
            style: const TextStyle(fontSize: 14, color: AppColors.slate700),
          ),
          QnaPhotoStrip(photos: a.photos),
        ],
      ),
    );
  }

  Widget _answerForm() {
    final canSend = _content.text.trim().isNotEmpty && !_uploading;
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: _box(Colors.white),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          AppTextArea(
            key: const Key('answer-content'),
            label: null,
            placeholder: '답글을 남겨 보세요.',
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
          if (_error != null) ...[
            const SizedBox(height: 12),
            FormError(message: _error),
          ],
          const SizedBox(height: 12),
          SubmitButton(
            key: const Key('answer-submit'),
            label: '답글 쓰기',
            pending: _pending,
            onPressed: canSend ? _answer : null,
          ),
        ],
      ),
    );
  }
}
