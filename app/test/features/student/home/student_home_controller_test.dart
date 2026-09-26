import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/features/student/home/student_home_controller.dart';
import 'package:academy_app/features/student/home/student_home_models.dart';
import 'package:academy_app/features/student/home/student_home_repository.dart';

/// 실제 저장소를 대신한다. 호출 횟수를 센다.
class _FakeRepo implements StudentHomeRepository {
  _FakeRepo(this._result);

  Future<StudentHome> Function() _result;
  int calls = 0;

  void thenReturn(Future<StudentHome> Function() r) => _result = r;

  @override
  Future<StudentHome> fetch() {
    calls++;
    return _result();
  }
}

StudentHome _home(String name) => StudentHome(
  studentName: name,
  nextLesson: null,
  nextExam: null,
  nextClinic: null,
  currentHomeworks: const [],
  lastLesson: null,
  notices: const HomeNotices(totalCount: 0, recent: []),
);

void main() {
  late DateTime clock;
  DateTime now() => clock;

  setUp(() => clock = DateTime(2026, 9, 19, 12, 0, 0));

  StudentHomeController build(_FakeRepo repo) =>
      StudentHomeController(repository: repo, now: now);

  test('첫 load 는 부르고 ready 가 된다', () async {
    final repo = _FakeRepo(() async => _home('김하늘'));
    final c = build(repo);
    expect(c.status, HomeStatus.idle);
    await c.load();
    expect(repo.calls, 1);
    expect(c.status, HomeStatus.ready);
    expect(c.data!.studentName, '김하늘');
    expect(c.error, isNull);
  });

  test('60초 이내 재진입은 부르지 않는다', () async {
    // 홈에 돌아올 때 스피너가 번쩍이면 안 된다.
    final repo = _FakeRepo(() async => _home('김하늘'));
    final c = build(repo);
    await c.load();
    clock = clock.add(const Duration(seconds: 59));
    await c.load();
    expect(repo.calls, 1);
  });

  test('60초를 넘기면 다시 부른다', () async {
    final repo = _FakeRepo(() async => _home('김하늘'));
    final c = build(repo);
    await c.load();
    clock = clock.add(const Duration(seconds: 61));
    await c.load();
    expect(repo.calls, 2);
  });

  test('refresh 는 60초를 무시한다', () async {
    // 당겨서 새로고침은 사용자가 명시적으로 요구한 것이다.
    final repo = _FakeRepo(() async => _home('김하늘'));
    final c = build(repo);
    await c.load();
    clock = clock.add(const Duration(seconds: 1));
    await c.refresh();
    expect(repo.calls, 2);
  });

  test('첫 로드 실패는 error 상태다', () async {
    final repo = _FakeRepo(
      () async => throw const ApiException(message: '불러올 수 없습니다.', code: 'X'),
    );
    final c = build(repo);
    await c.load();
    expect(c.status, HomeStatus.error);
    // 서버 문구를 그대로 쓴다. 감싸거나 접두어를 붙이지 마라.
    expect(c.error, '불러올 수 없습니다.');
    expect(c.data, isNull);
  });

  test('새로고침 실패는 기존 데이터를 유지한다', () async {
    // 당겨서 새로고침하다 실패했는데 화면이 비면 손해다.
    final repo = _FakeRepo(() async => _home('김하늘'));
    final c = build(repo);
    await c.load();
    repo.thenReturn(
      () async => throw const ApiException(message: '연결할 수 없습니다.', code: 'X'),
    );
    await c.refresh();
    expect(c.status, HomeStatus.ready);
    expect(c.data!.studentName, '김하늘');
    expect(c.error, '연결할 수 없습니다.');
  });

  test('성공하면 이전 오류가 지워진다', () async {
    final repo = _FakeRepo(
      () async => throw const ApiException(message: '실패', code: 'X'),
    );
    final c = build(repo);
    await c.load();
    expect(c.error, '실패');
    repo.thenReturn(() async => _home('김하늘'));
    await c.refresh();
    expect(c.error, isNull);
    expect(c.status, HomeStatus.ready);
  });

  test('상태가 바뀌면 리스너에게 알린다', () async {
    final repo = _FakeRepo(() async => _home('김하늘'));
    final c = build(repo);
    var notes = 0;
    c.addListener(() => notes++);
    await c.load();
    expect(notes, greaterThanOrEqualTo(2)); // loading 과 ready
  });

  test('진행 중인 요청이 있으면 두 번 나가지 않는다', () async {
    // 첫 로딩 중에 탭을 옮겼다 돌아오거나 당겨서 새로고침하면 두 응답이 경쟁한다.
    final completer = Completer<StudentHome>();
    final repo = _FakeRepo(() => completer.future);
    final c = build(repo);
    final first = c.load();
    final second = c.load(); // 아직 응답 전
    expect(repo.calls, 1);
    completer.complete(_home('김하늘'));
    await Future.wait([first, second]);
    expect(repo.calls, 1);
    expect(c.data!.studentName, '김하늘');
  });

  test('refresh 도 진행 중인 load 에 합류한다', () async {
    final completer = Completer<StudentHome>();
    final repo = _FakeRepo(() => completer.future);
    final c = build(repo);
    final first = c.load();
    final second = c.refresh();
    expect(repo.calls, 1);
    completer.complete(_home('김하늘'));
    await Future.wait([first, second]);
    expect(repo.calls, 1);
  });

  test('실패한 뒤에도 다음 호출이 다시 나간다', () async {
    // _inFlight 를 안 비우면 화면이 영원히 옛 상태에 묶인다.
    final repo = _FakeRepo(
      () async => throw const ApiException(message: '서버 오류', code: 'X'),
    );
    final c = build(repo);
    await c.load();
    expect(c.status, HomeStatus.error);
    repo.thenReturn(() async => _home('김하늘'));
    await c.refresh();
    expect(repo.calls, 2);
    expect(c.status, HomeStatus.ready);
  });

  test('dispose 된 뒤 응답이 와도 예외를 던지지 않고 상태를 쓰지 않는다', () async {
    // 화면이 응답을 기다리는 중에 닫히면(느린 망에서 뒤로 가기) 응답은
    // dispose 뒤에 도착한다. notifyListeners 를 그대로 부르면 Flutter 가
    // "used after being disposed"로 던진다 — 아무도 안 듣는 컨트롤러에
    // 상태를 쓰는 것도 의미가 없다.
    final completer = Completer<StudentHome>();
    final repo = _FakeRepo(() => completer.future);
    final c = build(repo);
    final future = c.load(); // 응답 전
    c.dispose();
    completer.complete(_home('김하늘'));
    await expectLater(future, completes); // 예외를 던지지 않는다
    expect(c.data, isNull); // 상태를 쓰지 않았다
    expect(c.status, HomeStatus.loading); // ready 로 전이하지 않았다
  });

  test('리스너가 동기적으로 load 를 다시 불러도 요청은 한 번만 나간다', () async {
    // _run() 은 async 라 호출 시점에 첫 notifyListeners 까지 동기로
    // 실행된다. 그 동기 구간에서 리스너가 곧바로 load() 를 다시 부르는
    // 경우 — _inFlight 가 이미 무장돼 있어야 재진입이 가드를 통과하지
    // 않는다.
    //
    // 재진입은 한 번만 한다(`reentered` 플래그). 가드가 없는 버그
    // 상태에서는 _inFlight 가 무장되는 시점이 늦어, 재진입한 load() 의
    // _run() 이 자신의 notifyListeners 를 또 부르고 리스너가 다시
    // load() 를 부른다 — 조건에 제한이 없으면 매 단계가 여전히
    // status == loading 이라 재귀가 끝없이 깊어진다. 한 번만 재진입해도
    // 가드가 없으면 이미 요청이 두 번 나가는 것으로 충분히 드러난다.
    final completer = Completer<StudentHome>();
    final repo = _FakeRepo(() => completer.future);
    final c = build(repo);
    var reentered = false;
    c.addListener(() {
      if (!reentered && c.status == HomeStatus.loading) {
        reentered = true;
        c.load(); // 재진입
      }
    });
    final first = c.load();
    expect(repo.calls, 1);
    completer.complete(_home('김하늘'));
    await first;
    expect(repo.calls, 1);
  });

  // ── 로그아웃(세션 dispose) ─────────────────────────────────
  // 로그아웃하면 AcademyApp 이 세션째 dispose 한다. 버려진 컨트롤러가
  // 요청을 내보내면 앞 사람의 토큰 없는 요청이 나가고, 응답을 쓸 곳도 없다.

  test('dispose 뒤 load·refresh 는 요청을 보내지 않는다', () async {
    final repo = _FakeRepo(() async => _home('김하늘'));
    final c = build(repo);
    c.dispose();
    await c.load();
    await c.refresh();
    expect(repo.calls, 0);
  });
}
