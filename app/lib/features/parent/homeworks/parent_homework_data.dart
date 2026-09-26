import 'package:dio/dio.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_response.dart';
import '../../../shared/widgets/lesson_day_filter.dart' show MonthFilter;

/// P-3 목록의 한 줄. 백엔드 `ParentHomeworkResponse` 가 정본이다.
///
/// **학생 모델을 재사용하지 마라**(CLAUDE.md 4-6). 학부모에게 열린 것은
/// 「냈는지 · 채점 결과 · 사진」뿐이다 — `description`(숙제 내용·재제출 상세)과
/// 영상은 이 응답에 **필드 자체가 없다.** 학생 모델로 받으면 그 칸이 null 로
/// 생겨서, 언젠가 누가 채워 보내는 순간 그대로 화면에 뜬다.
class ParentHomework {
  const ParentHomework({
    required this.homeworkId,
    required this.title,
    required this.classRoomName,
    required this.kind,
    required this.lessonDate,
    required this.result,
    required this.completionRate,
    required this.resolvedByResubmission,
    required this.dueAt,
    required this.status,
    required this.isLate,
    required this.photoCount,
  });

  final int homeworkId;
  final String title;
  final String classRoomName;
  final String kind;
  final String? lessonDate;

  /// null 이면 「미채점」이다. 0% 가 아니다 — 회색으로 보여라.
  final String? result;
  final int? completionRate;

  /// true 면 「완료 (재제출)」. 수업 때는 못 해왔지만 다시 냈다는 뜻이다.
  final bool resolvedByResubmission;
  final String? dueAt;
  final String status;
  final bool isLate;
  final int photoCount;

  bool get isGrid => kind == 'GRID';

  factory ParentHomework.fromJson(Map<String, dynamic> json) => ParentHomework(
    homeworkId: (json['homeworkId'] as num).toInt(),
    title: json['title'] as String,
    classRoomName: json['classRoomName'] as String,
    kind: json['kind'] as String,
    lessonDate: json['lessonDate'] as String?,
    result: json['result'] as String?,
    completionRate: (json['completionRate'] as num?)?.toInt(),
    // 여기부터 자바 원시형(boolean·int)만 기본값을 둔다(14-3).
    resolvedByResubmission: json['resolvedByResubmission'] as bool? ?? false,
    dueAt: json['dueAt'] as String?,
    status: json['status'] as String,
    isLate: json['isLate'] as bool? ?? false,
    photoCount: (json['photoCount'] as num?)?.toInt() ?? 0,
  );
}

class ParentHomeworkRepository {
  const ParentHomeworkRepository(this._dio);

  final Dio _dio;

  /// 첫 페이지(20건)만 받는다 — 웹과 같다. 연·월은 함께 보낸다.
  Future<List<ParentHomework>> list(int studentId, MonthFilter filter) =>
      unwrapCall(
        () => _dio.get<Map<String, dynamic>>(
          '/api/parent/children/$studentId/homeworks',
          queryParameters: {
            if (filter.year != null && filter.month != null) ...{
              'year': filter.year,
              'month': filter.month,
            },
          },
        ),
        (data) =>
            (((data as Map<String, dynamic>?)?['items'] as List<dynamic>?) ??
                    const [])
                .map((e) => ParentHomework.fromJson(e as Map<String, dynamic>))
                .toList(),
      );

  /// 제출 사진의 서명된 주소들. **사진만** 온다 — 영상 경로는 없다(4-6).
  Future<List<String>> photos(int studentId, int homeworkId) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/parent/children/$studentId/homeworks/$homeworkId/photos',
    ),
    (data) =>
        // 새 배열 필드는 `?? []` 로 받는다(7-3).
        (((data as Map<String, dynamic>?)?['photos'] as List<dynamic>?) ??
                const [])
            .map((e) => (e as Map<String, dynamic>)['url'] as String)
            .toList(),
  );
}

/// P-3. 매개변수는 (자녀, 달)이다 — 자녀가 바뀌어도 달이 바뀌어도 옛 목록을
/// 버리고 새로 부른다([ParamController]).
class ParentHomeworksController
    extends ParamController<(int, MonthFilter), List<ParentHomework>> {
  ParentHomeworksController({
    required this.repository,
    super.staleAfter,
    super.now,
  });

  /// 사진 보기(시트)가 직접 부른다 — 사진은 목록 상태가 아니다.
  final ParentHomeworkRepository repository;

  @override
  Future<List<ParentHomework>> fetch((int, MonthFilter) param) =>
      repository.list(param.$1, param.$2);
}
