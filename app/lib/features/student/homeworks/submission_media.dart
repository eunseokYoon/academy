import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:flutter_image_compress/flutter_image_compress.dart';
import 'package:image_picker/image_picker.dart';

import '../../../core/api/s3_uploader.dart';
import 'student_homework_repository.dart';

/// 사진·영상을 어디서 가져오는가. 버튼 하나가 둘 다 연다(2026-09-26 확정) —
/// 웹의 파일 선택이 폰에서 주는 선택지와 같다.
enum MediaFrom { camera, gallery }

/// 올릴 준비가 끝난 사진 — 이미 줄였다.
class PreparedPhoto {
  const PreparedPhoto({required this.bytes, required this.contentType});

  final Uint8List bytes;

  /// `image/webp`(Android) 또는 `image/jpeg`(iOS). 서버 허용 목록
  /// (`SubmissionMediaKeys`)에 둘 다 있다.
  final String contentType;
}

/// 고른 영상 — **원본 그대로다.** 앱에서도 압축·트랜스코딩을 하지 않는다.
class PickedVideo {
  const PickedVideo({
    required this.path,
    required this.bytes,
    required this.contentType,
  });

  final String path;
  final int bytes;

  /// 확장자로 정한다. 모르는 형식이면 null 이다([videoProblem] 이 막는다).
  final String? contentType;
}

/// 서버와 같은 값. **늘리지 마라**(CLAUDE.md 6번) — 영상은 압축이 안 돼서
/// 이 상한이 저장 비용을 막는 유일한 장치다.
const int kMaxVideoBytes = 100 * 1024 * 1024;

/// 사진 장수 상한. 서버 `SubmissionService.MAX_PHOTOS` 와 같다.
const int kMaxPhotos = 10;

/// 확장자 → 형식. `video/quicktime` 을 빼지 마라 — 아이폰 제출이 통째로
/// 막힌다(6번). 기기가 알려주는 MIME 은 자주 비어 있어서 확장자로 본다.
String? videoContentTypeOf(String path) {
  final dot = path.lastIndexOf('.');
  if (dot < 0) return null;
  return switch (path.substring(dot + 1).toLowerCase()) {
    'mp4' || 'm4v' => 'video/mp4',
    'mov' => 'video/quicktime',
    'webm' => 'video/webm',
    _ => null,
  };
}

/// 올리기 **전에** 거른다. 문구는 웹 `upload.ts` 와 같다.
///
/// 100MB 짜리를 다 올린 뒤 서버가 거절하면 모바일 데이터만 쓰고 끝난다.
String? videoProblem(PickedVideo video) {
  if (video.contentType == null) return 'mp4 · mov · webm 형식만 올릴 수 있습니다.';
  if (video.bytes > kMaxVideoBytes) {
    final mb = (video.bytes / (1024 * 1024)).round();
    return '영상은 100MB까지입니다. 고른 영상은 ${mb}MB입니다. 더 짧게 찍어 주세요.';
  }
  return null;
}

/// 사진·영상을 고르는 곳. 화면 테스트가 가짜를 넣는다 — 실제 구현은
/// 플랫폼 채널이라 위젯 테스트에서 돌지 않는다.
abstract class MediaPicker {
  /// 고르고 **줄여서** 돌려준다. 취소하면 빈 목록이다. [limit] 은 남은 칸 수다.
  Future<List<PreparedPhoto>> pickPhotos(MediaFrom from, {required int limit});

  /// 취소하면 null.
  Future<PickedVideo?> pickVideo(MediaFrom from);
}

/// 사진 장변 상한. 웹 `resize.ts` 와 같다(CLAUDE.md 6번 — 안 하면 연 200GB).
const int kPhotoMaxSide = 1600;
const int _photoQuality = 80;

/// 기기의 카메라·앨범.
///
/// **리사이즈는 `flutter_image_compress` 한 번이다.** 그 라이브러리의
/// `minWidth`·`minHeight` 는 「이 크기 이상으로 남긴다」라서 둘 다 1600 을 주면
/// 4000×3000 이 2133×1600 이 된다(장변이 1600 이 아니다). 그래서 원본 크기를
/// 먼저 읽고, **짧은 변 목표값**을 두 칸에 같이 넣어 장변이 1600 이 되게 한다.
/// 회전(EXIF)은 가로·세로만 바꾸므로 장변·단변 값은 그대로다.
///
/// **형식이 플랫폼마다 다르다.** iOS 는 WebP 인코딩이 안 돼서 JPEG 다.
class DeviceMediaPicker implements MediaPicker {
  DeviceMediaPicker([ImagePicker? picker]) : _picker = picker ?? ImagePicker();

  final ImagePicker _picker;

  @override
  Future<List<PreparedPhoto>> pickPhotos(
    MediaFrom from, {
    required int limit,
  }) async {
    if (limit <= 0) return const [];
    final List<XFile> files;
    if (from == MediaFrom.camera) {
      final one = await _picker.pickImage(
        source: ImageSource.camera,
        requestFullMetadata: false,
      );
      files = one == null ? const [] : [one];
    } else if (limit == 1) {
      // pickMultiImage 의 limit 은 2 이상이어야 한다.
      final one = await _picker.pickImage(
        source: ImageSource.gallery,
        requestFullMetadata: false,
      );
      files = one == null ? const [] : [one];
    } else {
      files = await _picker.pickMultiImage(
        limit: limit,
        requestFullMetadata: false,
      );
    }
    final prepared = <PreparedPhoto>[];
    // 한도를 넘게 고를 수 있는 기기가 있다(limit 을 무시하는 앨범). 잘라낸다.
    for (final file in files.take(limit)) {
      prepared.add(await _shrink(file.path));
    }
    return prepared;
  }

  Future<PreparedPhoto> _shrink(String path) async {
    final (width, height) = await _sizeOf(path);
    final long = math.max(width, height);
    final short = math.min(width, height);
    // 이미 작으면 키우지 않는다 — 라이브러리가 1 미만 배율을 1로 막는다.
    final target = long <= kPhotoMaxSide
        ? short
        : (short * kPhotoMaxSide / long).round();
    final format = Platform.isIOS ? CompressFormat.jpeg : CompressFormat.webp;
    final bytes = await FlutterImageCompress.compressWithFile(
      path,
      minWidth: target,
      minHeight: target,
      quality: _photoQuality,
      format: format,
      keepExif: false,
    );
    if (bytes == null) throw const FormatException('이미지를 변환하지 못했습니다.');
    return PreparedPhoto(
      bytes: bytes,
      contentType: format == CompressFormat.jpeg ? 'image/jpeg' : 'image/webp',
    );
  }

  /// 전부 디코딩하지 않고 머리만 읽는다 — 폰 사진은 장당 수십 MB 로 풀린다.
  Future<(int, int)> _sizeOf(String path) async {
    final buffer = await ui.ImmutableBuffer.fromFilePath(path);
    final descriptor = await ui.ImageDescriptor.encoded(buffer);
    final size = (descriptor.width, descriptor.height);
    descriptor.dispose();
    buffer.dispose();
    return size;
  }

  @override
  Future<PickedVideo?> pickVideo(MediaFrom from) async {
    final file = await _picker.pickVideo(
      source: from == MediaFrom.camera
          ? ImageSource.camera
          : ImageSource.gallery,
    );
    if (file == null) return null;
    return PickedVideo(
      path: file.path,
      bytes: await file.length(),
      contentType: videoContentTypeOf(file.path),
    );
  }
}

/// 발급 → S3 직접 PUT → 서버에 등록. 세 단계다(웹 `upload.ts`).
///
/// S3 가 실패하면 서버 문구가 없으므로 웹과 같은 문구를 쓴다. 발급·등록의
/// 실패는 서버의 [ApiException] 이 그대로 올라간다.
class SubmissionUploader {
  SubmissionUploader({required this.repository, required this._s3});

  final StudentHomeworkRepository repository;
  final S3Uploader _s3;

  Future<void> uploadPhoto(
    int homeworkId,
    PreparedPhoto photo, {
    required int sortOrder,
  }) async {
    final target = await repository.photoUploadUrl(
      homeworkId,
      contentType: photo.contentType,
      bytes: photo.bytes.length,
    );
    try {
      await _s3.put(
        target.uploadUrl,
        body: photo.bytes,
        contentType: photo.contentType,
        length: photo.bytes.length,
      );
    } on S3UploadException {
      throw const UploadFailure('사진을 올리지 못했습니다. 다시 시도해 주세요.');
    }
    await repository.registerPhoto(
      homeworkId,
      s3Key: target.s3Key,
      sortOrder: sortOrder,
      bytes: photo.bytes.length,
    );
  }

  /// [videoProblem] 을 먼저 통과한 영상만 받는다.
  Future<void> uploadVideo(
    int homeworkId,
    PickedVideo video, {
    void Function(double progress)? onProgress,
  }) async {
    final contentType = video.contentType!;
    final target = await repository.videoUploadUrl(
      homeworkId,
      contentType: contentType,
      bytes: video.bytes,
    );
    try {
      await _s3.put(
        target.uploadUrl,
        // 100MB 를 메모리에 올리지 않고 흘려 보낸다.
        body: File(video.path).openRead(),
        contentType: contentType,
        length: video.bytes,
        onSendProgress: onProgress == null
            ? null
            : (sent, total) => onProgress(total > 0 ? sent / total : 0),
      );
    } on S3UploadException {
      throw const UploadFailure('영상을 올리지 못했습니다. 다시 시도해 주세요.');
    }
    await repository.registerVideo(
      homeworkId,
      s3Key: target.s3Key,
      bytes: video.bytes,
    );
  }
}

/// S3 단계의 실패. 서버 문구가 없는 유일한 실패라 앱이 문구를 든다.
class UploadFailure implements Exception {
  const UploadFailure(this.message);

  final String message;

  @override
  String toString() => 'UploadFailure: $message';
}
