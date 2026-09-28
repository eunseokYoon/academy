import 'package:dio/dio.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_response.dart';
import '../../../shared/score/score_data.dart';

/// 백엔드 `StudentMeResponse`.
class StudentMe {
  const StudentMe({
    required this.name,
    required this.classRooms,
    required this.phone,
    required this.parentLinked,
  });

  /// `students.name` 이다(3-1).
  final String name;
  final List<String> classRooms;

  /// 본인 번호. 미가입이면 null 일 수 있다.
  final String? phone;

  /// false 면 선생님께 문의하라는 안내를 띄운다 — 학생이 알아야 조치가 된다.
  final bool parentLinked;

  factory StudentMe.fromJson(Map<String, dynamic> json) => StudentMe(
    name: json['name'] as String,
    classRooms: ((json['classRooms'] as List<dynamic>?) ?? const [])
        .map((e) => (e as Map<String, dynamic>)['name'] as String)
        .toList(),
    phone: json['phone'] as String?,
    // 자바 boolean 이다(14-3). 모르면 「연결됨」으로 둔다 — 옛 응답에 없다고
    // 모든 학생에게 경고를 띄우지 않는다.
    parentLinked: json['parentLinked'] as bool? ?? true,
  );
}

/// S-7 한 화면 분량.
class StudentScorePageData {
  const StudentScorePageData({
    required this.me,
    required this.scores,
    required this.exams,
  });

  final StudentMe me;
  final ScoreData scores;
  final List<ExamSchedule> exams;
}

class StudentScoreRepository {
  const StudentScoreRepository(this._dio);

  final Dio _dio;

  Future<StudentMe> me() => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/student/me'),
    (data) => StudentMe.fromJson(data! as Map<String, dynamic>),
  );

  /// 학부모(P-4)와 같은 응답이다. 정기고사는 오지 않는다.
  Future<ScoreData> scores() => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/student/scores'),
    (data) => ScoreData.fromJson(data! as Map<String, dynamic>),
  );

  Future<List<ExamSchedule>> exams() => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/student/exam-schedules'),
    examSchedulesFromJson,
  );
}

/// S-7. 매개변수가 없는 화면이라 빈 레코드를 쓴다.
class StudentScoreController extends ParamController<(), StudentScorePageData> {
  StudentScoreController({
    required this._repository,
    super.staleAfter,
    super.now,
  });

  final StudentScoreRepository _repository;

  @override
  Future<void> load([() param = ()]) => super.load(param);

  @override
  Future<StudentScorePageData> fetch(() param) async {
    final (me, scores, exams) = await all3(
      _repository.me(),
      _repository.scores(),
      _repository.exams(),
    );
    return StudentScorePageData(me: me, scores: scores, exams: exams);
  }
}
