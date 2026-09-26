import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/shared/lib/param_controller.dart';

/// 요청마다 Completer 하나. 테스트가 끝내는 순서를 정한다.
class _Probe extends ParamController<int, String> {
  _Probe({super.now});

  final List<(int, Completer<String>)> calls = [];

  @override
  Future<String> fetch(int param) {
    final c = Completer<String>();
    calls.add((param, c));
    return c.future;
  }
}

void main() {
  late DateTime clock;
  late _Probe p;

  setUp(() {
    clock = DateTime(2026, 9, 26, 10);
    p = _Probe(now: () => clock);
  });

  tearDown(() => p.dispose());

  test('같은 매개변수는 60초 안이면 다시 안 부르고, 넘으면 부른다', () async {
    final f = p.load(1);
    p.calls.single.$2.complete('a');
    await f;
    await p.load(1);
    expect(p.calls, hasLength(1));
    clock = clock.add(const Duration(seconds: 61));
    final g = p.load(1);
    expect(p.calls, hasLength(2));
    p.calls.last.$2.complete('b');
    await g;
    expect(p.data, 'b');
  });

  test('매개변수가 바뀌면 옛 데이터를 새 요청 전에 버린다', () async {
    final f = p.load(1);
    p.calls.single.$2.complete('one');
    await f;
    String? seen;
    p.addListener(() => seen ??= p.data ?? '<null>');
    p.load(2);
    // 첫 알림에 이미 옛 값이 없다 — 9월 머리 아래 10월 목록이 안 뜬다.
    expect(seen, '<null>');
    expect(p.status, HomeStatus.loading);
  });

  test('같은 매개변수의 진행 중 요청에는 합류하고, 다른 매개변수는 새로 쏜다', () {
    p.load(1);
    p.refresh();
    expect(p.calls, hasLength(1));
    p.load(2);
    expect(p.calls, hasLength(2));
  });

  test('A→B→A 에서 늦게 온 첫 A 응답이 마지막 A 를 덮지 않는다', () async {
    p.load(1);
    p.load(2);
    final last = p.load(1);
    expect(p.calls, hasLength(3));
    p.calls[2].$2.complete('new');
    await last;
    p.calls[0].$2.complete('old');
    p.calls[1].$2.complete('two');
    await Future<void>.delayed(Duration.zero);
    expect(p.data, 'new');
  });

  test('reload 는 진행 중 요청에 합류하지 않는다 — 저장 전 응답이 이기지 않는다', () async {
    final f = p.load(1);
    p.calls.single.$2.complete('a');
    await f;
    p.refresh(); // 저장 전에 나간 요청
    final r = p.reload(); // 저장 뒤
    expect(p.calls, hasLength(3));
    p.calls[2].$2.complete('after-save');
    await r;
    p.calls[1].$2.complete('before-save');
    await Future<void>.delayed(Duration.zero);
    expect(p.data, 'after-save');
  });

  test('markStale 뒤의 load 는 60초 안이어도 부른다', () async {
    final f = p.load(1);
    p.calls.single.$2.complete('a');
    await f;
    p.markStale();
    p.load(1);
    expect(p.calls, hasLength(2));
  });

  test('새로고침 실패는 데이터를 지키고, 첫 실패는 error 다', () async {
    final f = p.load(1);
    p.calls.single.$2.complete('a');
    await f;
    final r = p.refresh();
    p.calls.last.$2.completeError(
      const ApiException(code: 'X', message: '서버 문구'),
    );
    await r;
    expect(p.data, 'a');
    expect(p.status, HomeStatus.ready);
    expect(p.error, '서버 문구');

    final q = _Probe();
    final g = q.load(1);
    q.calls.single.$2.completeError(StateError('down'));
    await g;
    expect(q.status, HomeStatus.error);
    expect(q.error, '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
    q.dispose();
  });

  test('dispose 뒤에 온 응답은 쓰지도 알리지도 않는다', () async {
    final q = _Probe();
    final f = q.load(1);
    q.dispose();
    q.calls.single.$2.complete('late');
    await f; // "used after being disposed" 가 나면 여기서 던진다.
    expect(q.data, isNull);
    // dispose 뒤의 load 는 요청을 안 낸다.
    q.load(2);
    expect(q.calls, hasLength(1));
  });

  test('both·all3 는 처음 실패한 ApiException 을 그대로 던진다', () async {
    // 레코드 `.wait` 는 ParallelWaitError 로 감싸 ParamController 가 서버
    // 문구를 잃는다. 이 둘은 원래 오류를 넘겨야 한다.
    const e = ApiException(code: 'FORBIDDEN', message: '접근 권한이 없습니다.');
    await expectLater(
      both(Future.value(1), Future<int>.error(e)),
      throwsA(same(e)),
    );
    await expectLater(
      all3(Future<int>.error(e), Future.value(2), Future.value(3)),
      throwsA(same(e)),
    );
    expect(await both(Future.value(1), Future.value('a')), (1, 'a'));
    expect(await all3(Future.value(1), Future.value('a'), Future.value(true)), (
      1,
      'a',
      true,
    ));
  });
}
