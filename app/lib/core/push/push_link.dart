import '../auth/models/user_role.dart';
import '../router/auth_redirect.dart';

/// 알림을 눌렀을 때 열 곳. 서버 `PushPlanner` 가 FCM `data` 에 담는 셋이다
/// (`docs/10_api_reference.md` 「푸시 payload」).
///
/// **내용은 없다** — 화면을 열고 정상 API 로 다시 받는다. 그래야 알림이 DTO
/// 경계를 우회할 길이 없다. 여기에 제목·점수 같은 필드를 늘리지 마라.
class PushLink {
  const PushLink({required this.screen, this.id, this.studentId});

  /// 서버 `PushScreen.value()`. 이미 깔린 앱과의 계약이라 서버는 추가만 한다 —
  /// 모르는 값은 [pushLocation] 이 null 로 돌려 홈에 남는다.
  final String screen;

  /// 공지·질문·수업 id. 해당할 때만 온다.
  final int? id;

  /// 학부모 알림에만 온다. 그 자녀를 고른 채 화면을 연다.
  final int? studentId;

  /// `screen` 이 없으면 링크가 아니다(콘솔에서 보낸 시험 발송 등).
  static PushLink? fromData(Map<String, dynamic> data) {
    final screen = data['screen'];
    if (screen is! String || screen.isEmpty) return null;
    return PushLink(
      screen: screen,
      id: int.tryParse('${data['id'] ?? ''}'),
      studentId: int.tryParse('${data['studentId'] ?? ''}'),
    );
  }

  @override
  bool operator ==(Object other) =>
      other is PushLink &&
      other.screen == screen &&
      other.id == id &&
      other.studentId == studentId;

  @override
  int get hashCode => Object.hash(screen, id, studentId);

  @override
  String toString() => 'PushLink($screen, id: $id, studentId: $studentId)';
}

/// 역할과 링크로 갈 경로를 정한다. null 이면 이동하지 않는다.
///
/// 서버의 역할별 목적지(`PushTopic`)와 짝이다. **남의 역할 화면으로 보내지 않는다**
/// — 학부모에게 온 `qna` 는 없어야 하고(게시판에 학부모 경로가 없다), 오더라도
/// 여기서 멈춘다.
///
/// 공지는 목록으로 가고 상세 시트는 부르는 쪽이 띄운다(상세는 라우트가 아니라
/// 목록 위의 시트다). `report` 의 id(수업)는 쓰지 않는다 — 주간 레포트는 이번
/// 주가 기본이고 방금 공개된 수업은 이번 주에 있다.
String? pushLocation(PushLink link, UserRole role) {
  switch (role) {
    case UserRole.student:
      return switch (link.screen) {
        'notice' => AppRoutes.studentNotices,
        'homework' => AppRoutes.studentHomeworks,
        'scores' => AppRoutes.studentScores,
        'attendance' => AppRoutes.studentAttendances,
        'qna' =>
          link.id == null
              ? AppRoutes.studentQna
              : '${AppRoutes.studentQna}/${link.id}',
        'lesson' =>
          link.id == null
              ? AppRoutes.studentLessons
              : '${AppRoutes.studentLessons}/${link.id}',
        _ => null,
      };
    case UserRole.parent:
      return switch (link.screen) {
        'notice' => AppRoutes.parentNotices,
        'homework' => AppRoutes.parentHomeworks,
        'scores' => AppRoutes.parentScores,
        'schedule' => AppRoutes.parentSchedule,
        'report' => AppRoutes.parentLessons,
        _ => null,
      };
    case UserRole.teacher:
      // 선생님은 알림을 받지 않는다(15-4).
      return null;
  }
}
