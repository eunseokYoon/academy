import 'package:dio/dio.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_response.dart';
import '../../../shared/score/score_data.dart';

class ParentScoreRepository {
  const ParentScoreRepository(this._dio);

  final Dio _dio;

  /// 학생(S-7)과 같은 응답이다. 정기고사·등수·반 평균은 없다(7-1·7-2).
  Future<ScoreData> scores(int studentId) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/parent/children/$studentId/scores',
    ),
    (data) => ScoreData.fromJson(data! as Map<String, dynamic>),
  );

  Future<List<ExamSchedule>> exams(int studentId) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/parent/children/$studentId/exam-schedules',
    ),
    examSchedulesFromJson,
  );
}

/// P-4. 매개변수는 자녀다 — 자녀를 바꾸면 성적과 D-day 가 함께 바뀐다(반이 달라서).
class ParentScoreController
    extends ParamController<int, (ScoreData, List<ExamSchedule>)> {
  ParentScoreController({
    required this.repository,
    super.staleAfter,
    super.now,
  });

  /// 주간 레포트(P-6)도 성적을 이 저장소로 받는다.
  final ParentScoreRepository repository;

  @override
  Future<(ScoreData, List<ExamSchedule>)> fetch(int param) =>
      both(repository.scores(param), repository.exams(param));
}
