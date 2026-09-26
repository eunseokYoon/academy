import 'package:flutter/material.dart';
import 'package:academy_app/shared/lib/param_controller.dart';
import 'package:academy_app/shared/lib/url_opener.dart';

import '../../core/api/api_exception.dart';
import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';
import '../widgets/form_error.dart';
import '../widgets/full_screen_loader.dart';
import '../widgets/home_layout.dart';
import '../widgets/section.dart';
import '../widgets/sub_page.dart';
import 'notice_data.dart';

export 'package:academy_app/shared/lib/url_opener.dart';

// 첨부는 시스템 브라우저로 연다([openExternally]). 5분짜리 S3 주소라 앱 안에
// 띄울 이유가 없고, 받은 파일은 브라우저의 다운로드가 맡는다.

const _connectionError = '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.';

/// 학생·학부모 공용 공지 목록의 본문. 웹 정본은
/// `frontend/src/shared/notice/NoticeBoard.tsx` 다. 화면(학생 S-8 · 학부모
/// P-7)은 자녀 id 와 틀만 준다.
///
/// 읽음 표시는 없다(범위 밖). 읽은 공지도 계속 같은 모양으로 남는다.
class NoticeBoard extends StatelessWidget {
  const NoticeBoard({
    super.key,
    required this.controller,
    required this.studentId,
    required this.openUrl,
  });

  final NoticeListController controller;

  /// 학부모가 보는 자녀. **학생은 null** — 서버가 토큰에서 찾는다.
  final int? studentId;
  final UrlOpener openUrl;

  @override
  Widget build(BuildContext context) {
    final c = controller;
    final items = c.data;
    if (items == null) {
      return c.status == HomeStatus.error
          ? HomeErrorView(message: c.error, onRetry: c.refresh)
          : const SizedBox(height: 160, child: FullScreenLoader());
    }
    if (items.isEmpty) {
      return const TintBlock(
        key: Key('notices-empty'),
        tone: SectionTone.neutral,
        children: [EmptyNote(text: '등록된 공지가 없습니다.')],
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SectionHead(
          tone: SectionTone.brand,
          title: '전체 공지',
          count: items.length,
        ),
        TintBlock(
          tone: SectionTone.brand,
          children: [
            for (final item in items)
              _NoticeListRow(
                item: item,
                onTap: () => showNoticeSheet(
                  context,
                  repository: c.repository,
                  noticeId: item.noticeId,
                  studentId: studentId,
                  openUrl: openUrl,
                ),
              ),
          ],
        ),
      ],
    );
  }
}

class _NoticeListRow extends StatelessWidget {
  const _NoticeListRow({required this.item, required this.onTap});

  final NoticeListItem item;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    // TintBlock 은 Material 이 아니다 — 감싸지 않으면 물결이 블록 뒤에 그려진다
    // (notice_card.dart 와 같다).
    return Material(
      key: ValueKey('notice-${item.noticeId}'),
      type: MaterialType.transparency,
      child: InkWell(
        onTap: onTap,
        highlightColor: AppColors.brand100.withValues(alpha: 0.6),
        child: Padding(
          // 웹 `px-3.5 py-3`.
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          child: Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        // 옅은 배지는 남색 면 위에서 배경과 붙는다. 채운 남색으로 뒤집는다.
                        if (item.pinned) ...[
                          const _Tag(
                            key: Key('notice-pinned'),
                            label: '고정',
                            background: AppColors.brand600,
                            foreground: Colors.white,
                          ),
                          const SizedBox(width: 6),
                        ],
                        Flexible(
                          child: Text(
                            item.title,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: const TextStyle(
                              fontSize: 14,
                              fontWeight: FontWeight.w600,
                              color: AppColors.brand950,
                            ),
                          ),
                        ),
                        // 목록에서부터 자료 유무를 알린다(게시판의 「· 사진」과 같은 언어).
                        if (item.hasAttachment) ...[
                          const SizedBox(width: 6),
                          const _Tag(
                            key: Key('notice-attachment'),
                            label: '📎 자료',
                            background: AppColors.slate100,
                            foreground: AppColors.slate600,
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 2),
                    Text(
                      item.dateLabel,
                      style: TextStyle(
                        fontSize: 11.5,
                        color: AppColors.brand600.withValues(alpha: 0.7),
                        fontFeatures: kTabularFigures,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              const Text(
                '›',
                style: TextStyle(fontSize: 16, color: AppColors.brand300),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Tag extends StatelessWidget {
  const _Tag({
    super.key,
    required this.label,
    required this.background,
    required this.foreground,
  });

  final String label;
  final Color background;
  final Color foreground;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
    decoration: BoxDecoration(
      color: background,
      borderRadius: BorderRadius.circular(4),
    ),
    child: Text(
      label,
      style: TextStyle(
        fontSize: 9.5,
        fontWeight: FontWeight.w700,
        color: foreground,
      ),
    ),
  );
}

/// 공지 상세. 웹의 모달 자리다 — 앱은 아래에서 올라오는 시트다.
Future<void> showNoticeSheet(
  BuildContext context, {
  required NoticeRepository repository,
  required int noticeId,
  required int? studentId,
  required UrlOpener openUrl,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useRootNavigator: true,
    backgroundColor: Colors.white,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (context) => ConstrainedBox(
      constraints: BoxConstraints(
        maxHeight: MediaQuery.sizeOf(context).height * 0.85,
      ),
      child: _NoticeSheet(
        repository: repository,
        noticeId: noticeId,
        studentId: studentId,
        openUrl: openUrl,
      ),
    ),
  );
}

class _NoticeSheet extends StatefulWidget {
  const _NoticeSheet({
    required this.repository,
    required this.noticeId,
    required this.studentId,
    required this.openUrl,
  });

  final NoticeRepository repository;
  final int noticeId;
  final int? studentId;
  final UrlOpener openUrl;

  @override
  State<_NoticeSheet> createState() => _NoticeSheetState();
}

class _NoticeSheetState extends State<_NoticeSheet> {
  late final Future<NoticeDetail> _detail = widget.repository.detail(
    widget.noticeId,
    widget.studentId,
  );

  /// 받는 중인 첨부. 받는 동안 다른 첨부 버튼도 잠근다(웹 `disabled={isPending}`).
  int? _downloading;
  String? _downloadError;

  Future<void> _download(NoticeAttachment a) async {
    setState(() {
      _downloading = a.attachmentId;
      _downloadError = null;
    });
    String? error;
    try {
      final url = await widget.repository.downloadUrl(
        widget.noticeId,
        a.attachmentId,
        widget.studentId,
      );
      final opened = await widget.openUrl(Uri.parse(url));
      if (!opened) error = '파일을 열 수 없습니다. 잠시 후 다시 시도해 주세요.';
    } on ApiException catch (e) {
      error = e.message;
    } catch (_) {
      error = _connectionError;
    }
    if (!mounted) return;
    setState(() {
      _downloading = null;
      _downloadError = error;
    });
  }

  @override
  Widget build(BuildContext context) {
    return SafeArea(
      child: SingleChildScrollView(
        key: const Key('notice-sheet'),
        padding: const EdgeInsets.all(16),
        child: FutureBuilder<NoticeDetail>(
          future: _detail,
          builder: (context, snap) {
            final detail = snap.data;
            return Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  detail?.title ?? '공지',
                  style: const TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w800,
                    color: AppColors.brand900,
                  ),
                ),
                const SizedBox(height: 12),
                if (snap.hasError)
                  FormError(
                    message: snap.error is ApiException
                        ? (snap.error! as ApiException).message
                        : _connectionError,
                  )
                else if (detail == null)
                  const SizedBox(height: 120, child: FullScreenLoader())
                else
                  ..._body(detail),
                const SizedBox(height: 16),
                FilledButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('닫기'),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  List<Widget> _body(NoticeDetail detail) => [
    Text(
      detail.dateLabel,
      style: const TextStyle(fontSize: 12, color: AppColors.slate500),
    ),
    const SizedBox(height: 12),
    // 일반 텍스트 + 줄바꿈만. 마크다운·HTML 로 그리지 마라.
    Text(
      detail.content,
      key: const Key('notice-content'),
      style: const TextStyle(
        fontSize: 14,
        height: 1.625,
        color: AppColors.slate900,
      ),
    ),
    if (detail.attachments.isNotEmpty) ...[
      const SizedBox(height: 12),
      const Divider(height: 1, color: AppColors.slate100),
      const SizedBox(height: 12),
      if (_downloadError != null) ...[
        FormError(message: _downloadError),
        const SizedBox(height: 6),
      ],
      for (final a in detail.attachments) ...[
        _AttachmentRow(
          attachment: a,
          enabled: _downloading == null,
          onTap: () => _download(a),
        ),
        const SizedBox(height: 6),
      ],
    ],
  ];
}

class _AttachmentRow extends StatelessWidget {
  const _AttachmentRow({
    required this.attachment,
    required this.enabled,
    required this.onTap,
  });

  final NoticeAttachment attachment;
  final bool enabled;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final bytes = attachment.bytes;
    return Opacity(
      opacity: enabled ? 1 : 0.6,
      child: Material(
        key: ValueKey('attachment-${attachment.attachmentId}'),
        color: AppColors.slate50,
        borderRadius: BorderRadius.circular(AppRadii.lg),
        child: InkWell(
          onTap: enabled ? onTap : null,
          borderRadius: BorderRadius.circular(AppRadii.lg),
          highlightColor: AppColors.slate100,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Row(
              children: [
                Expanded(
                  child: Text.rich(
                    TextSpan(
                      children: [
                        TextSpan(text: '📎 ${attachment.fileName}'),
                        if (bytes != null)
                          TextSpan(
                            text: ' (${formatBytes(bytes)})',
                            style: const TextStyle(
                              color: AppColors.slate400,
                              fontFeatures: kTabularFigures,
                            ),
                          ),
                      ],
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 13,
                      color: AppColors.slate700,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(AppRadii.lg),
                    border: Border.all(color: AppColors.brand200),
                  ),
                  child: const Text(
                    '받기',
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: AppColors.brand700,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
