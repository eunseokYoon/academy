import 'package:dio/dio.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../core/api/api_response.dart';

/// 목록의 한 줄. 백엔드 `NoticeSummaryResponse` 가 정본이다.
class NoticeListItem {
  const NoticeListItem({
    required this.noticeId,
    required this.title,
    required this.pinned,
    required this.hasAttachment,
    required this.publishedAt,
  });

  final int noticeId;
  final String title;
  final bool pinned;
  final bool hasAttachment;

  /// 여기 오는 공지는 전부 발행된 것이라 null 이 아니다(7).
  final String publishedAt;

  /// `MM/DD`. 서버 문자열을 잘라 쓴다(14-10) — 웹 목록과 같다.
  String get dateLabel => publishedAt.substring(5, 10).replaceFirst('-', '/');

  factory NoticeListItem.fromJson(Map<String, dynamic> json) => NoticeListItem(
    noticeId: (json['noticeId'] as num).toInt(),
    title: json['title'] as String,
    // 자바 boolean 이라 기본값이 허용된다(14-3).
    pinned: json['pinned'] as bool? ?? false,
    hasAttachment: json['hasAttachment'] as bool? ?? false,
    publishedAt: json['publishedAt'] as String,
  );
}

/// 첨부 하나. `s3Key` 는 내려오지 않는다 — 받기는 별도 경로가 권한을 다시 본다.
class NoticeAttachment {
  const NoticeAttachment({
    required this.attachmentId,
    required this.fileName,
    required this.bytes,
  });

  final int attachmentId;
  final String fileName;

  /// 옛 첨부는 크기가 없다. null 이면 크기를 안 적는다 — 0B 로 채우지 마라.
  final int? bytes;

  factory NoticeAttachment.fromJson(Map<String, dynamic> json) =>
      NoticeAttachment(
        attachmentId: (json['attachmentId'] as num).toInt(),
        fileName: json['fileName'] as String,
        bytes: (json['bytes'] as num?)?.toInt(),
      );
}

class NoticeDetail {
  const NoticeDetail({
    required this.noticeId,
    required this.title,
    required this.content,
    required this.publishedAt,
    required this.attachments,
  });

  final int noticeId;
  final String title;

  /// 사용자가 입력한 **일반 텍스트**다. 마크다운·HTML 로 그리지 마라.
  final String content;
  final String publishedAt;
  final List<NoticeAttachment> attachments;

  /// `yyyy.MM.dd`. 웹 상세와 같다.
  String get dateLabel => publishedAt.substring(0, 10).replaceAll('-', '.');

  factory NoticeDetail.fromJson(Map<String, dynamic> json) => NoticeDetail(
    noticeId: (json['noticeId'] as num).toInt(),
    title: json['title'] as String,
    content: json['content'] as String,
    publishedAt: json['publishedAt'] as String,
    // **`?? []` 를 지우지 마라**(7-3). 2026-08-24 에 웹이 옛 응답의
    // `attachments.length` 로 공지 화면 전체가 죽었다.
    attachments: ((json['attachments'] as List<dynamic>?) ?? const [])
        .map((e) => NoticeAttachment.fromJson(e as Map<String, dynamic>))
        .toList(),
  );
}

/// 웹 `attachmentUpload.ts` 의 `formatBytes`.
String formatBytes(int bytes) {
  if (bytes < 1024) return '${bytes}B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).round()}KB';
  return '${(bytes / (1024 * 1024)).toStringAsFixed(1)}MB';
}

/// 학생·학부모 공용 공지 API.
///
/// **`studentId` 는 학부모만 넘긴다**(자녀마다 반 범위 공지가 다르다). 학생은
/// 생략하고 서버가 토큰에서 찾는다. 「학생만 보기」·「학부모만 보기」 판정은
/// 서버가 **역할**로 한다(7-6) — 앱에서 거르지 마라.
class NoticeRepository {
  const NoticeRepository(this._dio);

  final Dio _dio;

  Map<String, dynamic>? _q(int? studentId) =>
      studentId == null ? null : {'studentId': studentId};

  /// 첫 페이지(20건)만 받는다 — 웹과 같다. 정렬은 서버가 정한다(고정 → 선생님이
  /// 쓴 것 → 자동 발행, 7-6). 앱에서 다시 정렬하지 마라.
  Future<List<NoticeListItem>> list(int? studentId) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/notices',
      queryParameters: _q(studentId),
    ),
    (data) =>
        (((data as Map<String, dynamic>?)?['items'] as List<dynamic>?) ??
                const [])
            .map((e) => NoticeListItem.fromJson(e as Map<String, dynamic>))
            .toList(),
  );

  Future<NoticeDetail> detail(int noticeId, int? studentId) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/notices/$noticeId',
      queryParameters: _q(studentId),
    ),
    (data) => NoticeDetail.fromJson(data! as Map<String, dynamic>),
  );

  /// 5분짜리 주소다. **미리 받아 두지 말고 누를 때 받는다** — 상세를 열어 두고
  /// 한참 뒤에 누르면 만료된 주소를 쓰게 된다.
  Future<String> downloadUrl(int noticeId, int attachmentId, int? studentId) =>
      unwrapCall(
        () => _dio.get<Map<String, dynamic>>(
          '/api/notices/$noticeId/attachments/$attachmentId/download-url',
          queryParameters: _q(studentId),
        ),
        (data) => (data! as Map<String, dynamic>)['downloadUrl'] as String,
      );
}

/// 공지 목록. 매개변수는 자녀 id 다 — **학생은 null**.
class NoticeListController extends ParamController<int?, List<NoticeListItem>> {
  NoticeListController({required this.repository, super.staleAfter, super.now});

  /// 상세 시트가 직접 부른다 — 상세는 목록 상태가 아니다.
  final NoticeRepository repository;

  @override
  Future<List<NoticeListItem>> fetch(int? param) => repository.list(param);
}
