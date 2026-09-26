import 'package:flutter/material.dart';

import '../../../core/api/api_exception.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/icons/app_icon.dart';
import '../../../shared/icons/icon_paths.dart';
import '../homeworks/media_source_sheet.dart';
import '../homeworks/submission_media.dart';
import 'qna_data.dart';

/// 첨부 사진 고르기. 웹 `shared/qna/PhotoPicker.tsx`.
///
/// 고르는 즉시 줄여서 S3 에 올리고 `s3Key` 만 부모에게 준다. 글을 저장하기 전에
/// 올라가므로 쓰다 만 사진이 S3 에 남을 수 있다 — 200명 규모에서 문제가 되는
/// 양이 아니고 웹도 같다. **`qna/` 에 수명주기를 걸지 마라**(보관 기간 미정).
///
/// 목록은 부모가 들고 있다([photos]) — 제출 뒤 부모가 비우면 미리보기도 같이
/// 비어야 해서다. 웹은 두 배열이 따로 놀아 인덱스가 어긋났다.
///
/// [onBusyChanged] 로 올리는 중임을 알린다. 올리는 중에 글이 나가면 사진이
/// 빠진다 — 부모가 버튼을 끈다.
class QnaPhotoPicker extends StatefulWidget {
  const QnaPhotoPicker({
    super.key,
    required this.picker,
    required this.uploader,
    required this.photos,
    required this.onChanged,
    required this.onBusyChanged,
  });

  final MediaPicker picker;
  final QnaUploader uploader;
  final List<QnaUploadedPhoto> photos;
  final ValueChanged<List<QnaUploadedPhoto>> onChanged;
  final ValueChanged<bool> onBusyChanged;

  @override
  State<QnaPhotoPicker> createState() => _QnaPhotoPickerState();
}

class _QnaPhotoPickerState extends State<QnaPhotoPicker> {
  bool _busy = false;
  String? _error;

  void _setBusy(bool v) {
    setState(() => _busy = v);
    widget.onBusyChanged(v);
  }

  Future<void> _add() async {
    final from = await chooseMediaSource(context);
    if (from == null || !mounted) return;
    setState(() => _error = null);
    _setBusy(true);
    var current = widget.photos;
    try {
      final picked = await widget.picker.pickPhotos(
        from,
        limit: kQnaMaxPhotos - current.length,
      );
      for (final p in picked) {
        final uploaded = await widget.uploader.upload(p);
        if (!mounted) return;
        // 한 장씩 바로 넘긴다 — 셋째에서 실패해도 앞의 둘은 남는다.
        current = [...current, uploaded];
        widget.onChanged(current);
      }
    } catch (e) {
      if (mounted) {
        setState(
          () => _error = switch (e) {
            ApiException(:final message) => message,
            UploadFailure(:final message) => message,
            FormatException(:final message) => message,
            _ => '사진을 올리지 못했습니다.',
          },
        );
      }
    } finally {
      if (mounted) _setBusy(false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final photos = widget.photos;
    final full = photos.length >= kQnaMaxPhotos;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final (i, p) in photos.indexed)
              SizedBox.square(
                key: ValueKey('qna-preview-$i'),
                dimension: 80,
                child: Stack(
                  clipBehavior: Clip.none,
                  children: [
                    Positioned.fill(
                      child: ClipRRect(
                        borderRadius: BorderRadius.circular(AppRadii.lg),
                        child: Image.memory(p.bytes, fit: BoxFit.cover),
                      ),
                    ),
                    Positioned(
                      top: -6,
                      right: -6,
                      child: Material(
                        color: AppColors.brand900,
                        shape: const CircleBorder(),
                        child: InkWell(
                          key: ValueKey('qna-remove-$i'),
                          customBorder: const CircleBorder(),
                          onTap: _busy
                              ? null
                              : () => widget.onChanged([
                                  for (final (j, q) in photos.indexed)
                                    if (j != i) q,
                                ]),
                          child: const SizedBox.square(
                            dimension: 24,
                            child: Center(
                              child: Text(
                                '✕',
                                semanticsLabel: '사진 빼기',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: Colors.white,
                                ),
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            if (!full)
              SizedBox.square(
                dimension: 80,
                child: OutlinedButton(
                  key: const Key('qna-add-photo'),
                  onPressed: _busy ? null : _add,
                  style: OutlinedButton.styleFrom(
                    padding: EdgeInsets.zero,
                    foregroundColor: AppColors.brand600,
                    side: const BorderSide(color: AppColors.brand200),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(AppRadii.lg),
                    ),
                  ),
                  child: Text(
                    _busy ? '올리는 중' : '사진 추가',
                    style: const TextStyle(fontSize: 12),
                  ),
                ),
              ),
          ],
        ),
        const SizedBox(height: 6),
        const Text(
          '사진은 최대 $kQnaMaxPhotos장입니다.',
          style: TextStyle(fontSize: 12, color: AppColors.slate500),
        ),
        if (_error != null) ...[
          const SizedBox(height: 4),
          Text(
            _error!,
            key: const Key('qna-photo-error'),
            style: const TextStyle(fontSize: 12, color: AppColors.red600),
          ),
        ],
      ],
    );
  }
}

/// 첨부 사진 가로 줄. 누르면 전체 화면으로 키운다. 웹 `PhotoStrip.tsx`.
///
/// presigned URL 이 만료돼 깨지면 빈 자리로 둔다 — 흔한 일이 아니다.
class QnaPhotoStrip extends StatelessWidget {
  const QnaPhotoStrip({super.key, required this.photos});

  final List<QnaPhoto> photos;

  @override
  Widget build(BuildContext context) {
    if (photos.isEmpty) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 12),
      child: SizedBox(
        height: 96,
        child: ListView.separated(
          scrollDirection: Axis.horizontal,
          itemCount: photos.length,
          separatorBuilder: (_, _) => const SizedBox(width: 8),
          itemBuilder: (context, i) {
            final photo = photos[i];
            return InkWell(
              key: ValueKey('qna-photo-${photo.photoId}'),
              onTap: () => _zoom(context, photo.url),
              borderRadius: BorderRadius.circular(AppRadii.lg),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(AppRadii.lg),
                child: ColoredBox(
                  color: AppColors.slate100,
                  child: SizedBox.square(
                    dimension: 96,
                    child: Image.network(
                      photo.url,
                      fit: BoxFit.cover,
                      errorBuilder: (_, _, _) => const SizedBox.shrink(),
                    ),
                  ),
                ),
              ),
            );
          },
        ),
      ),
    );
  }

  static Future<void> _zoom(BuildContext context, String url) {
    return showDialog<void>(
      context: context,
      useRootNavigator: true,
      barrierColor: Colors.black.withValues(alpha: 0.85),
      builder: (context) => Stack(
        children: [
          Positioned.fill(
            child: GestureDetector(
              onTap: () => Navigator.of(context).pop(),
              child: InteractiveViewer(
                child: Center(
                  child: Image.network(
                    url,
                    fit: BoxFit.contain,
                    errorBuilder: (_, _, _) => const SizedBox.shrink(),
                  ),
                ),
              ),
            ),
          ),
          Positioned(
            top: MediaQuery.paddingOf(context).top + 12,
            right: 12,
            child: IconButton(
              key: const Key('qna-zoom-close'),
              tooltip: '닫기',
              onPressed: () => Navigator.of(context).pop(),
              style: IconButton.styleFrom(
                backgroundColor: Colors.black.withValues(alpha: 0.5),
                foregroundColor: Colors.white,
              ),
              // Material 아이콘으로 바꾸지 마라(14-8) — 웹 `Icon name="close"`.
              icon: const AppIcon(
                AppIconName.close,
                size: 20,
                color: Colors.white,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
