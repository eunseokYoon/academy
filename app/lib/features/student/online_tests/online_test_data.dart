import 'package:dio/dio.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import '../../../core/api/api_response.dart';
import '../../../shared/widgets/app_badge.dart';

/// 응시 상태. 웹 `TAKE_STATUS_LABELS`. 모르는 값은 서버 문자열 그대로다.
///
/// `NOT_STARTED` 는 서버에 행이 없는 상태다. `OFFLINE` 은 종이로 봤고 선생님이
/// 그 주차 클리닉 칸에 성적을 적은 경우다(2026-09-10) — **미응시가 아니다.**
String takeStatusLabel(String status) => switch (status) {
  'NOT_STARTED' => '미응시',
  'IN_PROGRESS' => '작성 중',
  'SUBMITTED' => '제출 완료',
  'OFFLINE' => '오프라인 응시',
  _ => status,
};

/// 더 할 것이 없는 테스트. **OFFLINE 도 여기다**(2026-09-27 확정) — 반 전체가
/// 종이로 본 주에 전원이 「응시할 테스트」(주황)로 뜨지 않게 하려고 서버가
/// 이 값을 만들었다. 웹 목록도 같이 고쳤다.
bool isTakeFinished(String status) =>
    status == 'SUBMITTED' || status == 'OFFLINE';

/// 목록 한 줄. 백엔드 `StudentOnlineTestListItemResponse`. **정답·해설지가 없다.**
class OnlineTestListItem {
  const OnlineTestListItem({
    required this.testId,
    required this.title,
    required this.classRoomName,
    required this.questionCount,
    required this.remainingMinutes,
    required this.status,
    required this.answeredCount,
  });

  final int testId;
  final String title;
  final String classRoomName;
  final int questionCount;

  /// 서버가 계산했다. 음수면 마감 경과, null 이면 마감이 없다.
  final int? remainingMinutes;
  final String status;
  final int answeredCount;

  bool get closed => (remainingMinutes ?? 0) < 0;

  factory OnlineTestListItem.fromJson(Map<String, dynamic> json) =>
      OnlineTestListItem(
        testId: (json['testId'] as num).toInt(),
        title: json['title'] as String,
        classRoomName: json['classRoomName'] as String,
        questionCount: (json['questionCount'] as num).toInt(),
        remainingMinutes: (json['remainingMinutes'] as num?)?.toInt(),
        status: json['status'] as String,
        // 자바 int 다(14-3).
        answeredCount: (json['answeredCount'] as num?)?.toInt() ?? 0,
      );
}

/// 목록 줄의 배지 색. 웹은 마감 지난 것을 빨강(danger)으로 그리지만, 앱은
/// 「빨강은 결석·위험 하나만」이라 경고(warn)에 둔다 — 숙제 화면과 같다.
BadgeTone takeStatusTone(OnlineTestListItem item) => switch (item.status) {
  'SUBMITTED' => BadgeTone.ok,
  'OFFLINE' => BadgeTone.neutral,
  _ => BadgeTone.warn,
};

/// 응시 화면. 백엔드 `OnlineTestTakeResponse`.
///
/// **정답·해설지 URL 이 없다** — 서버 DTO 에 필드 자체가 없다(개발자 도구로
/// 정답이 보이면 안 된다). 결과는 [OnlineTestResult] 로 따로 받는다. 이
/// 모델에 그 필드를 더하지 마라.
class OnlineTestTake {
  const OnlineTestTake({
    required this.testId,
    required this.title,
    required this.classRoomName,
    required this.questionCount,
    required this.choiceCount,
    required this.closesAt,
    required this.chosenChoices,
    required this.status,
  });

  final int testId;
  final String title;
  final String classRoomName;
  final int questionCount;
  final int choiceCount;

  /// ISO 8601. null 이면 마감이 없다.
  final String? closesAt;

  /// 서버에 임시 저장된 답. 안 푼 문항은 null. 길이는 [questionCount] 다.
  final List<int?> chosenChoices;
  final String status;

  factory OnlineTestTake.fromJson(Map<String, dynamic> json) {
    final count = (json['questionCount'] as num).toInt();
    final raw = (json['chosenChoices'] as List<dynamic>?) ?? const [];
    return OnlineTestTake(
      testId: (json['testId'] as num).toInt(),
      title: json['title'] as String,
      classRoomName: json['classRoomName'] as String,
      questionCount: count,
      choiceCount: (json['choiceCount'] as num).toInt(),
      closesAt: json['closesAt'] as String?,
      // 서버 저장 길이가 다르면 문항 수에 맞춘다 — 저장 요청은 길이가 정확히
      // questionCount 여야 400 이 안 난다.
      chosenChoices: [
        for (var i = 0; i < count; i++)
          i < raw.length ? (raw[i] as num?)?.toInt() : null,
      ],
      status: json['status'] as String,
    );
  }
}

/// 남은 분. **기기 시계로 센다 — 이 화면만의 예외다**(2026-09-27 사용자 결정,
/// CLAUDE.md 14-2 참고). 응시 응답에 서버가 센 값이 없어서 웹 응시 화면과
/// 같은 방식으로 맞췄다. 웹 `Math.round(ms / 60000)` 과 같다.
///
/// 마감 판정의 정본은 여전히 서버다 — 저장·제출이 마감 후면 서버가 400 을 낸다.
int? remainingMinutesOf(String? closesAt, DateTime now) {
  if (closesAt == null) return null;
  final ms = DateTime.parse(closesAt).difference(now).inMilliseconds;
  return (ms / 60000).round();
}

class QuestionResult {
  const QuestionResult({
    required this.questionNo,
    required this.chosen,
    required this.correct,
    required this.isCorrect,
  });

  final int questionNo;

  /// null 이면 미체크다. 오답이고 감점은 없다.
  final int? chosen;
  final int correct;
  final bool isCorrect;

  factory QuestionResult.fromJson(Map<String, dynamic> json) => QuestionResult(
    questionNo: (json['questionNo'] as num).toInt(),
    chosen: (json['chosen'] as num?)?.toInt(),
    correct: (json['correct'] as num).toInt(),
    isCorrect: json['isCorrect'] as bool? ?? false,
  );
}

/// **제출 후에만** 받는 응답. 정답과 해설지가 여기 처음 나온다.
///
/// `score`(100점 환산)는 **읽지 않는다**(2026-09-10) — 환산 점수를 화면에
/// 보이지 않기로 했다. 필드도 두지 않는다.
class OnlineTestResult {
  const OnlineTestResult({
    required this.title,
    required this.correctCount,
    required this.questionCount,
    required this.internalQuestionCount,
    required this.internalCorrect,
    required this.externalCorrect,
    required this.answerFileUrls,
    required this.results,
  });

  final String title;
  final int correctCount;
  final int questionCount;

  /// 앞 N문항이 내부지문. null 이면 나누지 않은 테스트다.
  final int? internalQuestionCount;

  /// [internalQuestionCount] 가 null 이면 null 이다. **0 으로 채우지 마라**(14-3).
  final int? internalCorrect;
  final int? externalCorrect;

  /// 해설지 전부(2026-09-29, 여러 장). 없으면 빈 목록이다.
  final List<String> answerFileUrls;
  final List<QuestionResult> results;

  /// 내부·외부로 나눠 보여줄 수 있나. 셋 중 하나라도 없으면 맞힌 개수 하나다.
  bool get split =>
      internalQuestionCount != null &&
      internalCorrect != null &&
      externalCorrect != null;

  factory OnlineTestResult.fromJson(Map<String, dynamic> json) =>
      OnlineTestResult(
        title: json['title'] as String,
        correctCount: (json['correctCount'] as num).toInt(),
        questionCount: (json['questionCount'] as num).toInt(),
        internalQuestionCount: (json['internalQuestionCount'] as num?)?.toInt(),
        internalCorrect: (json['internalCorrect'] as num?)?.toInt(),
        externalCorrect: (json['externalCorrect'] as num?)?.toInt(),
        // 옛 서버에는 answerFileUrls 가 없다. 그때는 한 장(answerFileUrl)으로 받는다(7-3).
        answerFileUrls: switch (json['answerFileUrls']) {
          final List<dynamic> list => list.cast<String>(),
          _ => [if (json['answerFileUrl'] case final String url) url],
        },
        results: [
          for (final e in (json['results'] as List<dynamic>?) ?? const [])
            QuestionResult.fromJson(e as Map<String, dynamic>),
        ],
      );
}

/// 응시 화면 한 벌. 이미 냈으면 결과가 함께 온다.
class OnlineTestView {
  const OnlineTestView({required this.take, required this.result});

  final OnlineTestTake take;
  final OnlineTestResult? result;
}

class OnlineTestRepository {
  const OnlineTestRepository(this._dio);

  final Dio _dio;

  Future<List<OnlineTestListItem>> list() => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/student/online-tests'),
    (data) => [
      for (final e in (data as List<dynamic>?) ?? const [])
        OnlineTestListItem.fromJson(e as Map<String, dynamic>),
    ],
  );

  Future<OnlineTestTake> take(int testId) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>('/api/student/online-tests/$testId'),
    (data) => OnlineTestTake.fromJson(data! as Map<String, dynamic>),
  );

  /// 임시 저장. **서버에 한다**(출석과 반대) — 25문항에 20~30분이 걸려서 앱이
  /// 죽으면 처음부터 다시 해야 한다. 길이는 문항 수와 같아야 한다.
  Future<void> saveAnswers(int testId, List<int?> chosenChoices) => unwrapCall(
    () => _dio.put<Map<String, dynamic>>(
      '/api/student/online-tests/$testId/answers',
      data: {'chosenChoices': chosenChoices},
    ),
    (_) {},
  );

  /// 본문 없음. 서버가 **마지막으로 임시 저장한 답**으로 채점한다 — 그래서
  /// 부르기 전에 남은 답을 먼저 저장해야 한다.
  Future<OnlineTestResult> submit(int testId) => unwrapCall(
    () => _dio.post<Map<String, dynamic>>(
      '/api/student/online-tests/$testId/submit',
    ),
    (data) => OnlineTestResult.fromJson(data! as Map<String, dynamic>),
  );

  Future<OnlineTestResult> result(int testId) => unwrapCall(
    () => _dio.get<Map<String, dynamic>>(
      '/api/student/online-tests/$testId/result',
    ),
    (data) => OnlineTestResult.fromJson(data! as Map<String, dynamic>),
  );
}

class OnlineTestListController
    extends ParamController<(), List<OnlineTestListItem>> {
  OnlineTestListController({
    required this._repository,
    super.staleAfter,
    super.now,
  });

  final OnlineTestRepository _repository;

  @override
  Future<void> load([() param = ()]) => super.load(param);

  @override
  Future<List<OnlineTestListItem>> fetch(() param) => _repository.list();
}

class OnlineTestTakeController extends ParamController<int, OnlineTestView> {
  OnlineTestTakeController({
    required this.repository,
    super.staleAfter,
    super.now,
  });

  /// 화면이 저장·제출에 직접 쓴다.
  final OnlineTestRepository repository;

  /// 화면이 마지막으로 들고 있던 답(테스트별). **떠났다 다시 들어오면 서버 응답보다
  /// 이것을 먼저 쓴다.** 이 컨트롤러는 세션 수명이라 [data] 가 처음 받은 답을 그대로
  /// 들고 있다 — 60초 안에 다시 들어오면 `load` 가 건너뛰고, 다시 받더라도 도착 전까지
  /// 옛 [data] 가 보인다. 그 옛 답으로 화면을 채우면 다음 자동 저장이 서버에 이미
  /// 저장된 답을 null 로 덮는다(2026-09-30 리뷰에서 발견). 제출하면 지운다.
  final Map<int, List<int?>> _drafts = {};

  List<int?>? draftOf(int testId) => _drafts[testId];

  void keepDraft(int testId, List<int?> answers) =>
      _drafts[testId] = [...answers];

  void clearDraft(int testId) => _drafts.remove(testId);

  /// 저장을 한 줄로 세운다 — 앞 저장이 늦게 끝나 뒤 저장을 덮지 않게. 화면이 아니라
  /// 여기 두는 이유는 떠날 때 흘려 보낸 저장과 다시 들어와 고른 답의 저장이 **다른
  /// 화면 State** 에서 나가기 때문이다.
  Future<void> saving = Future<void>.value();

  @override
  Future<OnlineTestView> fetch(int param) async {
    final take = await repository.take(param);
    // 목록에서 이미 낸 테스트를 다시 누르면 결과를 바로 보여준다.
    final result = take.status == 'SUBMITTED'
        ? await repository.result(param)
        : null;
    return OnlineTestView(take: take, result: result);
  }
}
