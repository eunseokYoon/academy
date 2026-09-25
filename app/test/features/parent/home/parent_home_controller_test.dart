import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/features/parent/home/parent_home_controller.dart';
import 'package:academy_app/features/parent/home/parent_home_models.dart';
import 'package:academy_app/features/parent/home/parent_home_repository.dart';
import 'package:academy_app/features/student/home/student_home_controller.dart'
    show HomeStatus;

/// 실제 저장소를 대신한다. 호출된 studentId 를 순서대로 기록한다.
class _FakeRepo implements ParentHomeRepository {
  _FakeRepo(this._result);
  Future<ParentHome> Function(int) _result;
  final calls = <int>[];
  int? get lastId => calls.isEmpty ? null : calls.last;
  void thenReturn(Future<ParentHome> Function(int) r) => _result = r;
  @override
  Future<ParentHome> fetch(int studentId) {
    calls.add(studentId);
    return _result(studentId);
  }
}

ParentHome _home(String name) => ParentHome(
  studentName: name,
  nextExam: null,
  nextLessonDate: null,
  nextLessonTime: null,
  nextLessonDDay: null,
  notices: const HomeNotices(totalCount: 0, recent: []),
  pendingHomeworkCount: 0,
  nextClinic: null,
  thisMonthAttendance: const AttendanceSummary(
    present: 0,
    late: 0,
    absent: 0,
    sick: 0,
    excused: 0,
    makeup: 0,
  ),
);

void main() {
  late DateTime clock;
  DateTime now() => clock;
  setUp(() => clock = DateTime(2026, 9, 19, 12));

  ParentHomeController build(_FakeRepo r) =>
      ParentHomeController(repository: r, now: now);

  test('첫 load 는 부르고 ready 가 된다', () async {
    final repo = _FakeRepo((id) async => _home('김하늘'));
    final c = build(repo);
    await c.load(1);
    expect(repo.calls, [1]);
    expect(c.status, HomeStatus.ready);
    expect(c.data!.studentName, '김하늘');
    expect(c.studentId, 1);
  });

  test('같은 자녀는 60초 이내 재진입에 부르지 않는다', () async {
    final repo = _FakeRepo((id) async => _home('김하늘'));
    final c = build(repo);
    await c.load(1);
    clock = clock.add(const Duration(seconds: 59));
    await c.load(1);
    expect(repo.calls, [1]);
  });

  test('같은 자녀도 60초를 넘기면 다시 부른다', () async {
    // 학생 컨트롤러의 같은 규칙 — 브리프의 기본 7개 테스트에는 빠져
    // 있었지만 `_staleAfter` 를 쓰지 않게 바뀌어도 잡아낼 테스트가 없으면
    // 안 된다.
    final repo = _FakeRepo((id) async => _home('김하늘'));
    final c = build(repo);
    await c.load(1);
    clock = clock.add(const Duration(seconds: 61));
    await c.load(1);
    expect(repo.calls, [1, 1]);
  });

  test('자녀가 바뀌면 60초를 무시하고 다시 부른다', () async {
    // 아이를 바꿨는데 이전 아이 데이터가 남아 있으면 잘못된 정보다.
    final repo = _FakeRepo((id) async => _home(id == 1 ? '김하늘' : '김바다'));
    final c = build(repo);
    await c.load(1);
    clock = clock.add(const Duration(seconds: 1));
    await c.load(2);
    expect(repo.calls, [1, 2]);
    expect(c.data!.studentName, '김바다');
    expect(c.studentId, 2);
  });

  test('자녀가 바뀌면 이전 데이터를 즉시 버린다', () async {
    // 새 데이터가 오기 전에 옛 이름이 떠 있으면 안 된다.
    final repo = _FakeRepo((id) async => _home(id == 1 ? '김하늘' : '김바다'));
    final c = build(repo);
    await c.load(1);
    final states = <String?>[];
    c.addListener(() => states.add(c.data?.studentName));
    await c.load(2);
    // 첫 알림 시점에 이미 옛 데이터가 없어야 한다.
    expect(states.first, isNull);
  });

  test('refresh 는 60초를 무시한다', () async {
    final repo = _FakeRepo((id) async => _home('김하늘'));
    final c = build(repo);
    await c.load(1);
    clock = clock.add(const Duration(seconds: 1));
    await c.refresh();
    expect(repo.calls, [1, 1]);
  });

  test('자녀를 고르기 전 refresh 는 아무것도 하지 않는다', () async {
    final repo = _FakeRepo((id) async => _home('김하늘'));
    final c = build(repo);
    await c.refresh();
    expect(repo.calls, isEmpty);
  });

  test('새로고침 실패는 기존 데이터를 유지한다', () async {
    final repo = _FakeRepo((id) async => _home('김하늘'));
    final c = build(repo);
    await c.load(1);
    repo.thenReturn(
      (id) async => throw const ApiException(message: '연결 실패', code: 'X'),
    );
    await c.refresh();
    expect(c.status, HomeStatus.ready);
    expect(c.data!.studentName, '김하늘');
    expect(c.error, '연결 실패');
  });

  test('같은 자녀의 진행 중인 요청에는 합류한다', () async {
    final completer = Completer<ParentHome>();
    final repo = _FakeRepo((_) => completer.future);
    final c = build(repo);
    final a = c.load(1);
    final b = c.load(1);
    expect(repo.calls, [1]);
    completer.complete(_home('김하늘'));
    await Future.wait([a, b]);
    expect(repo.calls, [1]);
  });

  test('자녀가 다르면 합류하지 않고 새로 부른다', () async {
    // 둘째를 골랐는데 첫째를 기다리면 영영 첫째가 뜬다.
    final first = Completer<ParentHome>();
    final second = Completer<ParentHome>();
    final repo = _FakeRepo((id) => id == 1 ? first.future : second.future);
    final c = build(repo);
    final a = c.load(1);
    final b = c.load(2);
    expect(repo.calls, [1, 2]);
    expect(repo.lastId, 2);
    second.complete(_home('김바다'));
    await b;
    expect(c.data!.studentName, '김바다');
    first.complete(_home('김하늘'));
    await a;
    // 늦게 도착한 첫째 응답이 둘째 화면을 덮으면 안 된다.
    expect(c.data!.studentName, '김바다');
  });

  test('늦게 도착한 옛 자녀의 실패는 화면에 뜨지 않는다', () async {
    final first = Completer<ParentHome>();
    final second = Completer<ParentHome>();
    final repo = _FakeRepo((id) => id == 1 ? first.future : second.future);
    final c = build(repo);
    final a = c.load(1);
    final b = c.load(2);
    second.complete(_home('김바다'));
    await b;
    first.completeError(const ApiException(message: '서버 오류', code: 'X'));
    await a;
    expect(c.status, HomeStatus.ready);
    expect(c.error, isNull);
    expect(c.data!.studentName, '김바다');
  });

  test('A→B→A: 같은 자녀로 되돌아온 옛 요청이 최신 데이터를 덮지 않는다', () async {
    // load(1) 나감(A1) → load(2) 나감(B, A1 진행 중) → load(1) 나감(A2,
    // 같은 자녀로 되돌아옴 — studentId 만 보면 A1 과 A2 를 구분할 수
    // 없다). 도착 순서는 B, A2, A1 — 가장 늦게 끝나는 A1 이 A2 의 최신
    // 데이터를 덮으면 안 된다.
    final a1 = Completer<ParentHome>();
    final a2 = Completer<ParentHome>();
    final b = Completer<ParentHome>();
    var childOneCalls = 0;
    final repo = _FakeRepo((id) {
      if (id == 2) return b.future;
      childOneCalls++;
      return childOneCalls == 1 ? a1.future : a2.future;
    });
    final c = build(repo);
    final loadA1 = c.load(1);
    final loadB = c.load(2);
    final loadA2 = c.load(1);
    expect(repo.calls, [1, 2, 1]);

    b.complete(_home('김바다'));
    await loadB;

    a2.complete(_home('A2'));
    await loadA2;
    expect(c.data!.studentName, 'A2');

    a1.complete(_home('A1-stale'));
    await loadA1;
    // 옛 요청(A1)이 최신 데이터(A2)를 덮으면 안 된다.
    expect(c.data!.studentName, 'A2');
    expect(c.status, HomeStatus.ready);
  });

  test('A→B→A: 늦게 실패한 옛 요청이 최신 데이터 위에 에러를 띄우지 않는다', () async {
    final a1 = Completer<ParentHome>();
    final a2 = Completer<ParentHome>();
    final b = Completer<ParentHome>();
    var childOneCalls = 0;
    final repo = _FakeRepo((id) {
      if (id == 2) return b.future;
      childOneCalls++;
      return childOneCalls == 1 ? a1.future : a2.future;
    });
    final c = build(repo);
    final loadA1 = c.load(1);
    final loadB = c.load(2);
    final loadA2 = c.load(1);

    b.complete(_home('김바다'));
    await loadB;

    a2.complete(_home('A2'));
    await loadA2;
    expect(c.data!.studentName, 'A2');

    // A1 이 A2 보다 늦게, 그것도 실패로 끝난다 — A2 화면에 에러가 뜨면
    // 안 된다.
    a1.completeError(const ApiException(message: 'A1 실패', code: 'X'));
    await loadA1;
    expect(c.status, HomeStatus.ready);
    expect(c.error, isNull);
    expect(c.data!.studentName, 'A2');
  });

  test('자녀를 바꾸면 60초 규칙을 건너뛴다', () async {
    final repo = _FakeRepo((id) async => _home(id == 1 ? '김하늘' : '김바다'));
    final c = build(repo);
    await c.load(1);
    clock = clock.add(const Duration(seconds: 5));
    await c.load(2); // 5초밖에 안 지났지만 다른 아이다
    expect(repo.calls, [1, 2]);
    expect(c.data!.studentName, '김바다');
    await c.load(2); // 같은 아이는 60초 규칙이 산다
    expect(repo.calls, [1, 2]);
  });

  test('dispose 된 뒤 응답이 와도 예외를 던지지 않고 상태를 쓰지 않는다', () async {
    // 화면이 응답을 기다리는 중에 닫히면(느린 망에서 뒤로 가기) 응답은
    // dispose 뒤에 도착한다. notifyListeners 를 그대로 부르면 Flutter 가
    // "used after being disposed"로 던진다.
    final completer = Completer<ParentHome>();
    final repo = _FakeRepo((_) => completer.future);
    final c = build(repo);
    final future = c.load(1); // 응답 전
    c.dispose();
    completer.complete(_home('김하늘'));
    await expectLater(future, completes); // 예외를 던지지 않는다
    expect(c.data, isNull); // 상태를 쓰지 않았다
    expect(c.status, HomeStatus.loading); // ready 로 전이하지 않았다
  });

  test('리스너가 동기적으로 load 를 다시 불러도 요청은 한 번만 나간다', () async {
    // _run() 은 async 라 호출 시점에 첫 notifyListeners 까지 동기로
    // 실행된다. 그 동기 구간에서 리스너가 곧바로 load() 를 다시 부르는
    // 경우 — _inFlight/_inFlightStudentId 가 이미 무장돼 있어야 재진입이
    // 가드를 통과하지 않는다.
    //
    // 재진입은 한 번만 한다(`reentered` 플래그). 가드가 없는 버그
    // 상태에서는 무장 시점이 늦어, 재진입한 load() 의 _run() 이 자신의
    // notifyListeners 를 또 부르고 리스너가 다시 load() 를 부른다 —
    // 조건에 제한이 없으면 매 단계가 여전히 status == loading 이라 재귀가
    // 끝없이 깊어진다. 한 번만 재진입해도 가드가 없으면 이미 요청이 두 번
    // 나가는 것으로 충분히 드러난다.
    final completer = Completer<ParentHome>();
    final repo = _FakeRepo((_) => completer.future);
    final c = build(repo);
    var reentered = false;
    c.addListener(() {
      if (!reentered && c.status == HomeStatus.loading) {
        reentered = true;
        c.load(1); // 재진입
      }
    });
    final first = c.load(1);
    expect(repo.calls, [1]);
    completer.complete(_home('김하늘'));
    await first;
    expect(repo.calls, [1]);
  });

  // ── 로그아웃(reset) ─────────────────────────────────────────
  // 컨트롤러는 앱 수명 동안 산다. 같은 폰으로 다음 학부모가 로그인했을 때
  // 앞 사람 자녀의 홈이 남으면 안 된다.

  test('reset 은 데이터·선택·60초 기록을 전부 비운다', () async {
    final repo = _FakeRepo((id) async => _home('김하늘'));
    final c = build(repo);
    await c.load(1);
    c.reset();
    expect(c.data, isNull);
    expect(c.error, isNull);
    expect(c.status, HomeStatus.idle);
    expect(c.studentId, isNull);
    // 60초 안이어도 다시 부른다 — 앞 사람의 기록으로 건너뛰면 안 된다.
    await c.load(1);
    expect(repo.calls, [1, 1]);
  });

  test('reset 뒤에 도착한 앞 사람의 응답은 버린다', () async {
    final slow = Completer<ParentHome>();
    final repo = _FakeRepo((_) => slow.future);
    final c = build(repo);
    final pending = c.load(1);
    c.reset();
    slow.complete(_home('김하늘'));
    await pending;
    expect(c.data, isNull);
    expect(c.status, HomeStatus.idle);
  });

  test('reset 뒤에 도착한 앞 사람의 실패도 버린다', () async {
    final slow = Completer<ParentHome>();
    final repo = _FakeRepo((_) => slow.future);
    final c = build(repo);
    final pending = c.load(1);
    c.reset();
    slow.completeError(
      const ApiException(code: 'FORBIDDEN', message: '접근 권한이 없습니다.'),
    );
    await pending;
    expect(c.error, isNull);
    expect(c.status, HomeStatus.idle);
  });
}
