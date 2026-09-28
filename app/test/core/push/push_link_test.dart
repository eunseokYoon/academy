import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/auth/models/user_role.dart';
import 'package:academy_app/core/push/push_link.dart';
import 'package:academy_app/core/router/auth_redirect.dart';

void main() {
  group('PushLink.fromData', () {
    test('screen·id·studentId 를 읽는다(FCM data 는 전부 문자열이다)', () {
      expect(
        PushLink.fromData({'screen': 'notice', 'id': '12', 'studentId': '3'}),
        const PushLink(screen: 'notice', id: 12, studentId: 3),
      );
    });

    test('없는 id 는 null 이다', () {
      expect(
        PushLink.fromData({'screen': 'homework'}),
        const PushLink(screen: 'homework'),
      );
    });

    test('screen 이 없으면 링크가 아니다', () {
      expect(PushLink.fromData({}), isNull);
      expect(PushLink.fromData({'screen': ''}), isNull);
      expect(PushLink.fromData({'id': '1'}), isNull);
    });
  });

  group('pushLocation', () {
    // 서버 PushTopic 의 studentScreen 전부.
    test('학생 목적지', () {
      String? go(String screen, [int? id]) =>
          pushLocation(PushLink(screen: screen, id: id), UserRole.student);

      expect(go('notice', 5), AppRoutes.studentNotices);
      expect(go('homework'), AppRoutes.studentHomeworks);
      expect(go('scores'), AppRoutes.studentScores);
      expect(go('attendance'), AppRoutes.studentAttendances);
      expect(go('qna', 7), '/student/qna/7');
      expect(go('qna'), AppRoutes.studentQna);
      expect(go('lesson', 9), '/student/lessons/9');
      expect(go('lesson'), AppRoutes.studentLessons);
    });

    // 서버 PushTopic 의 parentScreen 전부.
    test('학부모 목적지', () {
      String? go(String screen, [int? id]) => pushLocation(
        PushLink(screen: screen, id: id, studentId: 1),
        UserRole.parent,
      );

      expect(go('notice', 5), AppRoutes.parentNotices);
      expect(go('homework'), AppRoutes.parentHomeworks);
      expect(go('scores'), AppRoutes.parentScores);
      expect(go('schedule'), AppRoutes.parentSchedule);
      expect(go('report', 9), AppRoutes.parentLessons);
    });

    test('남의 역할 목적지로는 보내지 않는다', () {
      // 게시판에는 학부모 경로가 없다. 온라인 테스트·수업 상세도 학생 것이다.
      for (final screen in ['qna', 'lesson', 'attendance']) {
        expect(
          pushLocation(PushLink(screen: screen, id: 1), UserRole.parent),
          isNull,
          reason: screen,
        );
      }
      for (final screen in ['schedule', 'report']) {
        expect(
          pushLocation(PushLink(screen: screen), UserRole.student),
          isNull,
          reason: screen,
        );
      }
    });

    test('모르는 screen 은 이동하지 않는다(서버가 나중에 추가한 값)', () {
      const link = PushLink(screen: 'somethingNew');
      expect(pushLocation(link, UserRole.student), isNull);
      expect(pushLocation(link, UserRole.parent), isNull);
    });

    test('선생님은 어디로도 가지 않는다', () {
      expect(
        pushLocation(const PushLink(screen: 'notice'), UserRole.teacher),
        isNull,
      );
    });

    test('모든 목적지가 그 역할의 영역 안이다(라우터가 홈으로 튕기지 않는다)', () {
      const screens = [
        'notice', 'homework', 'scores', 'qna', 'attendance', //
        'schedule', 'lesson', 'report',
      ];
      for (final role in [UserRole.student, UserRole.parent]) {
        final home = role == UserRole.student
            ? AppRoutes.student
            : AppRoutes.parent;
        for (final s in screens) {
          final loc = pushLocation(PushLink(screen: s, id: 1), role);
          if (loc != null) {
            expect(loc.startsWith('$home/'), isTrue, reason: loc);
          }
        }
      }
    });
  });
}
