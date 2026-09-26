import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:academy_app/shared/lib/homework_labels.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/router/app_router.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/widgets/app_badge.dart';
import '../../../shared/widgets/app_card.dart';
import '../../../shared/widgets/form_error.dart';
import '../../../shared/widgets/full_screen_loader.dart';
import '../../../shared/widgets/home_layout.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/sub_page.dart';
import '../../../shared/widgets/submit_button.dart';
import '../../student/home/student_home_controller.dart' show HomeStatus;
import 'student_homework_controllers.dart';
import 'student_homework_models.dart';
import 'submission_media.dart';
import 'submission_video_player.dart';

/// 영상 칸. 테스트가 바꿔 끼운다 — 실제 플레이어는 플랫폼 채널이라 위젯
/// 테스트에서 돌지 않는다.
typedef VideoViewBuilder = Widget Function(String url);

Widget _defaultVideoView(String url) => SubmissionVideoPlayer(url: url);

/// S-3 제출 + S-4 상세. 웹 정본은 `StudentHomeworkDetailPage.tsx` 다. 상태에
/// 따라 한 화면이 두 역할을 한다.
///
/// **제출 화면을 여는 근거는 [StudentHomeworkDetail.canSubmit] 하나다.** 서버도
/// 같은 기준으로 막으므로(4-4) 여기서 안 그리는 건 안내일 뿐이다.
///
/// **GRID 재제출은 한 번 내면 끝이다**(4-5). 내는 순간 서버가 ⭕를 붙이고
/// `resubmitRequired` 가 false 가 되어 화면이 통째로 안내문으로 바뀐다. 잘못
/// 냈으면 선생님이 그리드에서 🔺·❌로 되돌려 줘야 다시 낼 수 있다 — 잠금용
/// 상태 검사를 여기 따로 만들지 마라.
///
/// 사진은 고른 즉시 **한 장씩** 줄여 올린다. 한 장이 실패해도 나머지는 그대로
/// 진행하고, 실패한 것만 다시 시도할 수 있게 자리에 남긴다.
///
/// [onChanged] 는 올림·지움·제출이 성공할 때마다 부른다. 목록(개수·배지)과
/// 홈(미완료 숙제)이 낡았다는 신호다 — 앱이 두 컨트롤러에 [markStale] 을 건다.
class StudentHomeworkDetailPage extends StatefulWidget {
  const StudentHomeworkDetailPage({
    super.key,
    required this.homeworkId,
    required this.controller,
    required this.uploader,
    required this.picker,
    required this.onChanged,
    this.videoView = _defaultVideoView,
  });

  final int homeworkId;
  final StudentHomeworkDetailController controller;
  final SubmissionUploader uploader;
  final MediaPicker picker;
  final VoidCallback onChanged;
  final VideoViewBuilder videoView;

  @override
  State<StudentHomeworkDetailPage> createState() =>
      _StudentHomeworkDetailPageState();
}

/// 올리는 중인 사진 한 장. 실패하면 [error] 가 채워지고 자리에 남는다.
class _Uploading {
  _Uploading(this.key, this.photo);

  final int key;
  final PreparedPhoto photo;
  String? error;
}

class _StudentHomeworkDetailPageState extends State<StudentHomeworkDetailPage>
    with ReappearReload<StudentHomeworkDetailPage> {
  final List<_Uploading> _uploading = [];
  int _nextKey = 0;

  /// 고른 사진을 줄이는 중. 몇 초가 걸린다 — 반응이 없으면 버튼을 여러 번 누른다.
  bool _preparing = false;

  /// 영상 올리는 중이면 0~1, 아니면 null.
  double? _videoProgress;
  bool _submitting = false;
  String? _error;

  int get _id => widget.homeworkId;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onChanged);
    widget.controller.load(_id);
  }

  @override
  void onReappear() => widget.controller.load(_id);

  @override
  void didUpdateWidget(covariant StudentHomeworkDetailPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller ||
        oldWidget.homeworkId != widget.homeworkId) {
      oldWidget.controller.removeListener(_onChanged);
      widget.controller.addListener(_onChanged);
      widget.controller.load(_id);
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

  /// 저장이 성공했다. 이 화면은 새로 받고, 목록·홈은 다음에 보일 때 받는다.
  Future<void> _afterSave() async {
    widget.onChanged();
    await widget.controller.reload();
  }

  static String _messageOf(Object e, String fallback) => switch (e) {
    // 서버 문구 그대로다 — 감싸지 마라.
    ApiException(:final message) => message,
    UploadFailure(:final message) => message,
    FormatException(:final message) => message,
    _ => fallback,
  };

  // ── 사진 ──────────────────────────────────────────────

  Future<void> _addPhotos(StudentHomeworkDetail detail) async {
    final from = await _chooseSource('사진 추가');
    if (from == null || !mounted) return;
    final room = kMaxPhotos - detail.photos.length - _uploading.length;
    setState(() {
      _error = null;
      _preparing = true;
    });
    final List<PreparedPhoto> picked;
    try {
      picked = await widget.picker.pickPhotos(from, limit: room);
    } catch (e) {
      if (mounted) {
        setState(() {
          _preparing = false;
          _error = _messageOf(e, '사진을 불러오지 못했습니다.');
        });
      }
      return;
    }
    if (!mounted) return;
    final queued = [for (final p in picked) _Uploading(_nextKey++, p)];
    setState(() {
      _preparing = false;
      _uploading.addAll(queued);
    });
    final base = detail.photos.length;
    var uploaded = false;
    for (final (index, item) in queued.indexed) {
      uploaded =
          await _uploadOne(item, sortOrder: base + index + 1) || uploaded;
      if (!mounted) return;
    }
    if (uploaded) await _afterSave();
  }

  /// 성공하면 자리를 비우고 true, 실패하면 자리에 문구를 남기고 false.
  Future<bool> _uploadOne(_Uploading item, {required int sortOrder}) async {
    try {
      await widget.uploader.uploadPhoto(_id, item.photo, sortOrder: sortOrder);
      if (mounted) setState(() => _uploading.remove(item));
      return true;
    } catch (e) {
      if (mounted) {
        setState(() => item.error = _messageOf(e, '사진을 올리지 못했습니다.'));
      }
      return false;
    }
  }

  Future<void> _retry(_Uploading item, int photoCount) async {
    setState(() => item.error = null);
    if (await _uploadOne(item, sortOrder: photoCount + 1) && mounted) {
      await _afterSave();
    }
  }

  Future<void> _deletePhoto(SubmissionPhoto photo) async {
    setState(() => _error = null);
    try {
      await widget.uploader.repository.deletePhoto(_id, photo.photoId);
      if (mounted) await _afterSave();
    } catch (e) {
      if (mounted) setState(() => _error = _messageOf(e, '사진을 지우지 못했습니다.'));
    }
  }

  // ── 영상 ──────────────────────────────────────────────

  Future<void> _pickVideo() async {
    final from = await _chooseSource('영상 추가');
    if (from == null || !mounted) return;
    setState(() => _error = null);
    final PickedVideo? video;
    try {
      video = await widget.picker.pickVideo(from);
    } catch (e) {
      if (mounted) setState(() => _error = _messageOf(e, '영상을 불러오지 못했습니다.'));
      return;
    }
    if (video == null || !mounted) return;
    // 올리기 전에 거른다 — 100MB 를 다 올린 뒤 거절당하면 데이터만 쓴다.
    final problem = videoProblem(video);
    if (problem != null) {
      setState(() => _error = problem);
      return;
    }
    setState(() => _videoProgress = 0);
    try {
      await widget.uploader.uploadVideo(
        _id,
        video,
        onProgress: (p) {
          if (mounted) setState(() => _videoProgress = p);
        },
      );
      if (!mounted) return;
      setState(() => _videoProgress = null);
      await _afterSave();
    } catch (e) {
      if (mounted) {
        setState(() {
          _videoProgress = null;
          _error = _messageOf(e, '영상을 올리지 못했습니다.');
        });
      }
    }
  }

  Future<void> _deleteVideo() async {
    setState(() => _error = null);
    try {
      await widget.uploader.repository.deleteVideo(_id);
      if (mounted) await _afterSave();
    } catch (e) {
      if (mounted) setState(() => _error = _messageOf(e, '영상을 지우지 못했습니다.'));
    }
  }

  // ── 제출 ──────────────────────────────────────────────

  Future<void> _submit() async {
    setState(() {
      _error = null;
      _submitting = true;
    });
    try {
      await widget.uploader.repository.submit(_id);
      if (mounted) await _afterSave();
    } catch (e) {
      if (mounted) setState(() => _error = _messageOf(e, '제출하지 못했습니다.'));
    } finally {
      if (mounted) setState(() => _submitting = false);
    }
  }

  /// 카메라·앨범 중 하나. 버튼 하나가 둘 다 연다(2026-09-26 확정).
  Future<MediaFrom?> _chooseSource(String title) {
    return showModalBottomSheet<MediaFrom>(
      context: context,
      useRootNavigator: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              key: const Key('source-camera'),
              title: const Text('카메라로 찍기'),
              onTap: () => Navigator.of(context).pop(MediaFrom.camera),
            ),
            ListTile(
              key: const Key('source-gallery'),
              title: const Text('앨범에서 고르기'),
              onTap: () => Navigator.of(context).pop(MediaFrom.gallery),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    // 컨트롤러가 앞서 본 다른 숙제를 들고 있어도 여기 오지 않는다 —
    // initState·didUpdateWidget 의 load(id) 가 첫 빌드 전에 옛 데이터를 버린다.
    final detail = c.data;
    return SubPageScroll(
      role: '학생',
      onRefresh: c.refresh,
      children: [
        PageBackLink(
          label: '숙제 목록',
          onTap: () => context.go(AppRoutes.studentHomeworks),
        ),
        if (detail == null)
          c.status == HomeStatus.error
              ? HomeErrorView(message: c.error, onRetry: c.refresh)
              : const SizedBox(height: 160, child: FullScreenLoader())
        else
          ..._body(detail),
      ],
    );
  }

  List<Widget> _body(StudentHomeworkDetail d) {
    // 실패해 자리에 남은 사진도 막는다(웹 `uploading.length > 0`) — 빠진 채로
    // 내면 학생은 다 냈다고 믿는다. 다시 시도해서 비워야 낼 수 있다.
    final busy = _uploading.isNotEmpty || _preparing || _videoProgress != null;
    final photoCount = d.photos.length;
    final hasVideo = d.video != null;
    return [
      _headerCard(d),
      const SizedBox(height: 16),
      if (!d.canSubmit)
        AppCard(
          key: const Key('locked-note'),
          padding: const EdgeInsets.all(16),
          child: Text(
            d.isGrid && d.status == 'SUBMITTED'
                ? '제출했습니다. 더 이상 수정할 수 없습니다.'
                : '다시 제출할 숙제가 아닙니다.',
            textAlign: TextAlign.center,
            style: const TextStyle(fontSize: 14, color: AppColors.slate500),
          ),
        )
      else ...[
        _photoCard(d),
        const SizedBox(height: 16),
        _videoCard(d),
        const SizedBox(height: 16),
        if (photoCount == 0 && !hasVideo && !busy) ...[
          const Text(
            '사진이나 영상을 하나 이상 올려야 제출할 수 있습니다.',
            key: Key('need-media'),
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 12, color: AppColors.slate400),
          ),
          const SizedBox(height: 16),
        ],
        if (_error != null) ...[
          FormError(message: _error),
          const SizedBox(height: 16),
        ],
        SubmitButton(
          key: const Key('submit'),
          label: d.status == 'SUBMITTED' ? '다시 제출하기' : '제출하기',
          pending: _submitting,
          // 올리는 중에는 못 낸다 — 반쯤 올라간 채로 제출되면 선생님은 빈
          // 칸을 본다.
          onPressed: (photoCount == 0 && !hasVideo) || busy ? null : _submit,
        ),
      ],
    ];
  }

  Widget _headerCard(StudentHomeworkDetail d) {
    final dueAt = d.dueAt;
    final description = d.description;
    return AppCard(
      key: const Key('homework-header'),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Text(
                  d.title,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.w600,
                    color: AppColors.brand900,
                  ),
                ),
              ),
              // 다시 낼 게 없는 GRID 는 이 축이 영원히 NOT_SUBMITTED 다(4-2) —
              // 그때는 배지를 안 띄운다. 「미제출」이 크게 보이면 아래 안내와
              // 모순된다.
              if (d.canSubmit) ...[
                const SizedBox(width: 8),
                AppBadge(
                  key: const Key('submission-badge'),
                  tone: d.status == 'NOT_SUBMITTED'
                      ? BadgeTone.warn
                      : BadgeTone.ok,
                  label: submissionLabel(d.status),
                ),
              ],
            ],
          ),
          const SizedBox(height: 2),
          Text(
            d.lessonDate == null
                ? d.classRoomName
                : '${d.classRoomName} · ${d.lessonDate} 수업',
            style: const TextStyle(
              fontSize: 14,
              color: AppColors.slate500,
              fontFeatures: kTabularFigures,
            ),
          ),
          // GRID 는 재제출을 열기 전까지 마감이 없다.
          if (dueAt != null) ...[
            const SizedBox(height: 2),
            Text(
              '${d.isGrid ? '다시 제출 마감' : '마감'} ${formatDueAt(dueAt)}',
              style: const TextStyle(
                fontSize: 12,
                color: AppColors.slate500,
                fontFeatures: kTabularFigures,
              ),
            ),
          ],
          if (description != null && description.isNotEmpty) ...[
            const SizedBox(height: 12),
            // 줄바꿈은 선생님이 쓴 그대로 살린다.
            Text(
              description,
              style: const TextStyle(fontSize: 14, color: AppColors.slate700),
            ),
          ],
          if (d.isLate) ...[
            const SizedBox(height: 8),
            const Text(
              '마감 후에 제출했습니다.',
              style: TextStyle(fontSize: 12, color: AppColors.amber700),
            ),
          ],
        ],
      ),
    );
  }

  Widget _photoCard(StudentHomeworkDetail d) {
    final photoCount = d.photos.length;
    final full = photoCount + _uploading.length >= kMaxPhotos;
    return AppCard(
      key: const Key('photo-card'),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _cardHead(
            label: '사진 $photoCount/$kMaxPhotos',
            action: _preparing ? '준비 중…' : '사진 추가',
            buttonKey: const Key('add-photo'),
            onPressed: full || _preparing ? null : () => _addPhotos(d),
          ),
          if (d.photos.isNotEmpty || _uploading.isNotEmpty) ...[
            const SizedBox(height: 12),
            GridView.count(
              crossAxisCount: 3,
              mainAxisSpacing: 8,
              crossAxisSpacing: 8,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              children: [
                for (final photo in d.photos) _photoTile(photo),
                // 줄이고 올리는 데 몇 초가 걸린다. 아무 반응이 없으면 학생이
                // 버튼을 여러 번 누른다.
                for (final item in _uploading) _uploadingTile(item, photoCount),
              ],
            ),
          ],
        ],
      ),
    );
  }

  Widget _photoTile(SubmissionPhoto photo) => ClipRRect(
    key: ValueKey('photo-${photo.photoId}'),
    borderRadius: BorderRadius.circular(AppRadii.lg),
    child: Stack(
      fit: StackFit.expand,
      children: [
        ColoredBox(
          color: AppColors.slate100,
          child: Image.network(
            photo.url,
            fit: BoxFit.cover,
            errorBuilder: (context, error, stackTrace) =>
                const SizedBox.shrink(),
          ),
        ),
        Positioned(
          top: 4,
          right: 4,
          child: Material(
            color: AppColors.brand900.withValues(alpha: 0.7),
            shape: const StadiumBorder(),
            child: InkWell(
              key: ValueKey('delete-photo-${photo.photoId}'),
              customBorder: const StadiumBorder(),
              onTap: () => _deletePhoto(photo),
              child: const Padding(
                padding: EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                child: Text(
                  '✕',
                  semanticsLabel: '사진 삭제',
                  style: TextStyle(fontSize: 12, color: Colors.white),
                ),
              ),
            ),
          ),
        ),
      ],
    ),
  );

  Widget _uploadingTile(_Uploading item, int photoCount) {
    final error = item.error;
    return Container(
      key: ValueKey('uploading-${item.key}'),
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(AppRadii.lg),
        border: Border.all(color: AppColors.slate300),
      ),
      child: Center(
        child: error == null
            ? const Text(
                '올리는 중…',
                style: TextStyle(fontSize: 12, color: AppColors.slate400),
              )
            : Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Text(
                    '실패',
                    style: TextStyle(fontSize: 10, color: AppColors.red700),
                  ),
                  TextButton(
                    key: ValueKey('retry-${item.key}'),
                    onPressed: () => _retry(item, photoCount),
                    child: const Text('다시 시도', style: TextStyle(fontSize: 12)),
                  ),
                ],
              ),
      ),
    );
  }

  Widget _videoCard(StudentHomeworkDetail d) {
    final video = d.video;
    final progress = _videoProgress;
    return AppCard(
      key: const Key('video-card'),
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _cardHead(
            label: '영상 (1개까지)',
            action: video == null ? '영상 추가' : '영상 바꾸기',
            buttonKey: const Key('add-video'),
            onPressed: progress != null ? null : _pickVideo,
          ),
          const SizedBox(height: 12),
          if (progress != null)
            Column(
              key: const Key('video-uploading'),
              children: [
                LinearProgressIndicator(
                  value: progress,
                  color: AppColors.brand600,
                  backgroundColor: AppColors.slate100,
                ),
                const SizedBox(height: 8),
                Text(
                  '영상을 올리는 중입니다(${(progress * 100).round()}%). '
                  '사진보다 오래 걸립니다…',
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    fontSize: 12,
                    color: AppColors.slate400,
                    fontFeatures: kTabularFigures,
                  ),
                ),
              ],
            )
          else if (video != null) ...[
            KeyedSubtree(
              key: const Key('video-view'),
              child: widget.videoView(video.url),
            ),
            TextButton(
              key: const Key('delete-video'),
              onPressed: _deleteVideo,
              child: const Text(
                '영상 삭제',
                style: TextStyle(
                  fontSize: 12,
                  color: AppColors.slate500,
                  decoration: TextDecoration.underline,
                  decorationColor: AppColors.slate500,
                ),
              ),
            ),
          ] else
            const Text(
              '100MB까지 올릴 수 있습니다. 폰으로 1분 안쪽 분량입니다.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 12, color: AppColors.slate400),
            ),
        ],
      ),
    );
  }

  /// 카드 머리 — 왼쪽 글자, 오른쪽 테두리 버튼. 웹 `rounded-lg border
  /// border-slate-300 px-3 py-1.5 text-sm`.
  Widget _cardHead({
    required String label,
    required String action,
    required Key buttonKey,
    required VoidCallback? onPressed,
  }) {
    return Row(
      children: [
        Expanded(
          child: Text(
            label,
            style: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w500,
              color: AppColors.slate700,
              fontFeatures: kTabularFigures,
            ),
          ),
        ),
        OutlinedButton(
          key: buttonKey,
          onPressed: onPressed,
          style: OutlinedButton.styleFrom(
            foregroundColor: AppColors.slate700,
            side: const BorderSide(color: AppColors.slate300),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            minimumSize: const Size(0, 36),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(AppRadii.lg),
            ),
            textStyle: const TextStyle(fontSize: 14),
          ),
          child: Text(action),
        ),
      ],
    );
  }
}
