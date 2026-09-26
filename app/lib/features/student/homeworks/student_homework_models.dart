/// 학생 숙제(S-2 · S-3 · S-4)의 응답 모델. 백엔드 레코드가 정본이다 —
/// `StudentHomeworkListItemResponse` · `HomeworkNoteResponse` ·
/// `StudentHomeworkDetailResponse`.
///
/// **없는 값에 기본값을 채우지 마라**(CLAUDE.md 14-3). `?? 0`·`?? false` 는
/// 자바 원시형(`boolean`·`int`) 필드에만 쓴다. `result` 가 null 이면 「미채점」
/// 이고 0% 가 아니다. `remainingMinutes`·`dueAt` 이 null 이면 마감이 **없는**
/// 것이다 — 0 을 넣으면 「마감 임박」으로 읽힌다.
library;

/// 목록의 한 줄.
class StudentHomeworkItem {
  const StudentHomeworkItem({
    required this.homeworkId,
    required this.title,
    required this.description,
    required this.classRoomName,
    required this.kind,
    required this.lessonDate,
    required this.result,
    required this.completionRate,
    required this.resolvedByResubmission,
    required this.resubmitRequired,
    required this.dueAt,
    required this.status,
    required this.isLate,
    required this.photoCount,
    required this.hasVideo,
    required this.remainingMinutes,
  });

  final int homeworkId;
  final String title;

  /// 재제출 상세 내용(4-8). 학생만 받는다 — 학부모 모델에는 없다.
  final String? description;
  final String classRoomName;

  /// `GRID` · `ONLINE`.
  final String kind;

  /// GRID 열은 어느 수업 숙제인지 보여준다. ONLINE 은 수업이 없을 수 있다.
  final String? lessonDate;

  /// `DONE` · `PARTIAL` · `NOT_DONE` · null(미채점).
  final String? result;
  final int? completionRate;
  final bool resolvedByResubmission;

  /// GRID 의 제출 화면을 여는 **유일한 근거**다(4-3). 서버가 계산한다.
  final bool resubmitRequired;

  /// GRID 열은 재제출을 열기 전까지 마감이 없다.
  final String? dueAt;

  /// `NOT_SUBMITTED` · `SUBMITTED`. **GRID 에서 이것으로 판정하지 마라**(4-2).
  final String status;
  final bool isLate;
  final int photoCount;
  final bool hasVideo;

  /// 서버가 계산한다. 음수면 마감이 지난 것이고, 마감이 없으면 null 이다.
  final int? remainingMinutes;

  bool get isGrid => kind == 'GRID';

  /// 「지금 낼 수 있고, 내야 하는 것」. 웹 `isTodo` 그대로다.
  ///
  /// **축이 둘이라 한 줄로 못 쓴다**(4-7) — GRID 는 선생님이 재제출을 열어
  /// 줘야만 낼 수 있고(⭕를 받아도 status 가 NOT_SUBMITTED 로 남는다),
  /// ONLINE 은 아직 안 낸 것이 그대로 할 일이다. 한 축으로 줄이지 마라.
  bool get isTodo => isGrid ? resubmitRequired : status == 'NOT_SUBMITTED';

  factory StudentHomeworkItem.fromJson(Map<String, dynamic> json) =>
      StudentHomeworkItem(
        homeworkId: (json['homeworkId'] as num).toInt(),
        title: json['title'] as String,
        description: json['description'] as String?,
        classRoomName: json['classRoomName'] as String,
        kind: json['kind'] as String,
        lessonDate: json['lessonDate'] as String?,
        result: json['result'] as String?,
        completionRate: (json['completionRate'] as num?)?.toInt(),
        // 여기부터 자바 원시형(boolean·int)이다.
        resolvedByResubmission:
            json['resolvedByResubmission'] as bool? ?? false,
        resubmitRequired: json['resubmitRequired'] as bool? ?? false,
        dueAt: json['dueAt'] as String?,
        status: json['status'] as String,
        isLate: json['isLate'] as bool? ?? false,
        photoCount: (json['photoCount'] as num?)?.toInt() ?? 0,
        hasVideo: json['hasVideo'] as bool? ?? false,
        remainingMinutes: (json['remainingMinutes'] as num?)?.toInt(),
      );
}

/// 「이번 주에 낼 것」의 한 덩어리 — 선생님이 수업에 적은 수업 숙제 글(4-7).
/// 반마다 하나, 가장 최근 **공개된** 수업의 글이다.
class HomeworkNote {
  const HomeworkNote({
    required this.lessonId,
    required this.lessonDate,
    required this.classRoomName,
    required this.weekLabel,
    required this.homeworkNote,
  });

  final int lessonId;
  final String lessonDate;
  final String classRoomName;

  /// 「8월 2주」. **서버가 준다** — 날짜로 주차를 세지 마라(9-2).
  final String weekLabel;
  final String homeworkNote;

  factory HomeworkNote.fromJson(Map<String, dynamic> json) => HomeworkNote(
    lessonId: (json['lessonId'] as num).toInt(),
    lessonDate: json['lessonDate'] as String,
    classRoomName: json['classRoomName'] as String,
    weekLabel: json['weekLabel'] as String,
    homeworkNote: json['homeworkNote'] as String,
  );
}

/// 제출한 사진 한 장. `url` 은 서명된 GET 주소라 시간이 지나면 만료된다 —
/// 화면에 오래 들고 있지 말고 상세를 다시 부르면 새로 받는다.
class SubmissionPhoto {
  const SubmissionPhoto({
    required this.photoId,
    required this.url,
    required this.sortOrder,
  });

  final int photoId;
  final String url;
  final int? sortOrder;

  factory SubmissionPhoto.fromJson(Map<String, dynamic> json) =>
      SubmissionPhoto(
        photoId: (json['photoId'] as num).toInt(),
        url: json['url'] as String,
        sortOrder: (json['sortOrder'] as num?)?.toInt(),
      );
}

/// 제출한 영상. 최대 1개다.
class SubmissionVideo {
  const SubmissionVideo({required this.url, required this.bytes});

  final String url;

  /// 모르면 null 이다(`Integer`). 0 으로 채우지 마라.
  final int? bytes;

  factory SubmissionVideo.fromJson(Map<String, dynamic> json) =>
      SubmissionVideo(
        url: json['url'] as String,
        bytes: (json['bytes'] as num?)?.toInt(),
      );
}

/// S-3 제출 + S-4 상세.
class StudentHomeworkDetail {
  const StudentHomeworkDetail({
    required this.homeworkId,
    required this.title,
    required this.description,
    required this.kind,
    required this.lessonDate,
    required this.dueAt,
    required this.classRoomName,
    required this.status,
    required this.isLate,
    required this.photos,
    required this.video,
    required this.resubmitRequired,
  });

  final int homeworkId;
  final String title;
  final String? description;
  final String kind;
  final String? lessonDate;
  final String? dueAt;
  final String classRoomName;
  final String status;
  final bool isLate;
  final List<SubmissionPhoto> photos;
  final SubmissionVideo? video;
  final bool resubmitRequired;

  bool get isGrid => kind == 'GRID';

  /// 제출 화면(사진·영상 추가, 제출 버튼)을 여는 **유일한 근거**다. 서버도
  /// 같은 기준으로 `findEditableSubmission` 에서 막으므로(4-4) 여기서 안
  /// 그리는 건 안내일 뿐이다.
  bool get canSubmit => !isGrid || resubmitRequired;

  factory StudentHomeworkDetail.fromJson(Map<String, dynamic> json) {
    final homework = json['homework'] as Map<String, dynamic>;
    final submission = json['submission'] as Map<String, dynamic>;
    final video = submission['video'] as Map<String, dynamic>?;
    return StudentHomeworkDetail(
      homeworkId: (homework['id'] as num).toInt(),
      title: homework['title'] as String,
      description: homework['description'] as String?,
      kind: homework['kind'] as String,
      lessonDate: homework['lessonDate'] as String?,
      dueAt: homework['dueAt'] as String?,
      classRoomName: homework['classRoomName'] as String,
      status: submission['status'] as String,
      isLate: submission['isLate'] as bool? ?? false,
      // 새 배열 필드는 `?? []` 로 받는다(7-3).
      photos: ((submission['photos'] as List<dynamic>?) ?? const [])
          .map((e) => SubmissionPhoto.fromJson(e as Map<String, dynamic>))
          .toList(),
      video: video == null ? null : SubmissionVideo.fromJson(video),
      resubmitRequired: json['resubmitRequired'] as bool? ?? false,
    );
  }
}

/// 서명된 업로드 주소. S3 에 **직접** PUT 한다(6번 — 서버 경유 금지).
class UploadTarget {
  const UploadTarget({required this.uploadUrl, required this.s3Key});

  final String uploadUrl;
  final String s3Key;

  factory UploadTarget.fromJson(Map<String, dynamic> json) => UploadTarget(
    uploadUrl: json['uploadUrl'] as String,
    s3Key: json['s3Key'] as String,
  );
}
