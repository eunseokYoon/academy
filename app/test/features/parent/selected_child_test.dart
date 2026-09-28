import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/fake_adapter.dart';
import 'package:academy_app/core/storage/key_value_store.dart';
import 'package:academy_app/features/parent/selected_child.dart';

class _MemoryStore implements KeyValueStore {
  final map = <String, String>{};

  @override
  Future<String?> read(String key) async => map[key];

  @override
  Future<void> write(String key, String value) async => map[key] = value;

  @override
  Future<void> delete(String key) async => map.remove(key);
}

/// 응답을 붙잡아 두는 어댑터. [release] 할 때 돌려준다.
class _GateAdapter implements HttpClientAdapter {
  final _body = Completer<Map<String, dynamic>>();

  void release(Map<String, dynamic> body) => _body.complete(body);

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async => ResponseBody.fromString(
    jsonEncode(await _body.future),
    200,
    headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    },
  );

  @override
  void close({bool force = false}) {}
}

Map<String, dynamic> _children(List<Map<String, dynamic>> items) => {
  'success': true,
  'data': items,
  'error': null,
};

void main() {
  late Dio dio;
  late FakeAdapter adapter;
  late _MemoryStore store;

  setUp(() {
    dio = Dio(BaseOptions(baseUrl: 'http://test'));
    adapter = FakeAdapter(replies: const []);
    dio.httpClientAdapter = adapter;
    store = _MemoryStore();
  });

  SelectedChild build() => SelectedChild(dio: dio, store: store);

  test('자녀 목록을 불러오고 첫째를 고른다', () async {
    adapter = FakeAdapter(
      replies: [
        FakeReply(
          statusCode: 200,
          body: _children([
            {'studentId': 1, 'name': '김하늘'},
            {'studentId': 2, 'name': '김바다'},
          ]),
        ),
      ],
    );
    dio.httpClientAdapter = adapter;

    final s = build();
    await s.load();

    expect(adapter.received.single.path, '/api/parent/children');
    expect(s.children.length, 2);
    expect(s.selectedStudentId, 1);
    expect(s.loading, isFalse);
  });

  test('고른 값을 기기에 저장한다', () async {
    adapter = FakeAdapter(
      replies: [
        FakeReply(
          statusCode: 200,
          body: _children([
            {'studentId': 1, 'name': '김하늘'},
            {'studentId': 2, 'name': '김바다'},
          ]),
        ),
      ],
    );
    dio.httpClientAdapter = adapter;

    final s = build();
    await s.load();
    await s.select(2);

    expect(s.selectedStudentId, 2);
    expect(store.map.values, contains('2'));
  });

  test('저장된 선택을 콜드 스타트에 되살린다', () async {
    store.map['academy.selectedStudentId'] = '2';
    adapter = FakeAdapter(
      replies: [
        FakeReply(
          statusCode: 200,
          body: _children([
            {'studentId': 1, 'name': '김하늘'},
            {'studentId': 2, 'name': '김바다'},
          ]),
        ),
      ],
    );
    dio.httpClientAdapter = adapter;

    final s = build();
    await s.load();

    expect(s.selectedStudentId, 2);
  });

  test('저장된 값이 더 이상 내 자녀가 아니면 첫째로 되돌린다', () async {
    store.map['academy.selectedStudentId'] = '99';
    adapter = FakeAdapter(
      replies: [
        FakeReply(
          statusCode: 200,
          body: _children([
            {'studentId': 1, 'name': '김하늘'},
          ]),
        ),
      ],
    );
    dio.httpClientAdapter = adapter;

    final s = build();
    await s.load();

    expect(s.selectedStudentId, 1);
  });

  test('자녀가 하나면 목록 길이가 1이다', () async {
    adapter = FakeAdapter(
      replies: [
        FakeReply(
          statusCode: 200,
          body: _children([
            {'studentId': 1, 'name': '김하늘'},
          ]),
        ),
      ],
    );
    dio.httpClientAdapter = adapter;

    final s = build();
    await s.load();

    expect(s.children.length, 1);
  });

  test('선택이 바뀌면 리스너에게 알린다', () async {
    adapter = FakeAdapter(
      replies: [
        FakeReply(
          statusCode: 200,
          body: _children([
            {'studentId': 1, 'name': '김하늘'},
            {'studentId': 2, 'name': '김바다'},
          ]),
        ),
      ],
    );
    dio.httpClientAdapter = adapter;

    final s = build();
    await s.load();

    var notes = 0;
    s.addListener(() => notes++);
    await s.select(2);

    expect(notes, greaterThanOrEqualTo(1));
  });

  test('같은 자녀를 다시 고르면 알리지 않는다', () async {
    adapter = FakeAdapter(
      replies: [
        FakeReply(
          statusCode: 200,
          body: _children([
            {'studentId': 1, 'name': '김하늘'},
            {'studentId': 2, 'name': '김바다'},
          ]),
        ),
      ],
    );
    dio.httpClientAdapter = adapter;

    final s = build();
    await s.load();

    var notes = 0;
    s.addListener(() => notes++);
    await s.select(1);

    expect(notes, 0);
  });

  test('dispose 뒤 도착한 앞 사람 목록은 쓰지도 알리지도 않는다', () async {
    // 로그아웃하면 AcademyApp 이 세션째 dispose 한다. 목록이 그 뒤에
    // 도착하면 notifyListeners 가 "used after being disposed"로 던진다.
    final gate = _GateAdapter();
    dio.httpClientAdapter = gate;
    final sc = build();
    final pending = sc.load();
    sc.dispose();
    gate.release(
      _children([
        {'studentId': 1, 'name': '김하늘'},
      ]),
    );
    await expectLater(pending, completes);
    expect(sc.children, isEmpty);
    expect(sc.selectedStudentId, isNull);
  });

  test('dispose 뒤 load 는 요청을 보내지 않는다', () async {
    final sc = build();
    sc.dispose();
    await sc.load();
    expect(adapter.received, isEmpty);
  });

  test('실패하면 loadError 에 서버 문구가 남고, 다시 부르면 지워진다', () async {
    adapter = FakeAdapter(
      replies: [
        const FakeReply(
          statusCode: 500,
          body: {
            'success': false,
            'data': null,
            'error': {'code': 'INTERNAL', 'message': '서버 오류입니다.'},
          },
        ),
        FakeReply(
          statusCode: 200,
          body: _children([
            {'studentId': 1, 'name': '김하늘'},
          ]),
        ),
      ],
    );
    dio.httpClientAdapter = adapter;
    final sc = SelectedChild(dio: dio, store: store);
    sc.ensureLoaded();
    await pumpEventQueue();
    expect(sc.loadError, '서버 오류입니다.');
    expect(sc.loaded, isFalse);

    await sc.load();
    expect(sc.loadError, isNull);
    expect(sc.selectedStudentId, 1);
    sc.dispose();
  });

  test('ensureLoaded 는 받았거나 받는 중이면 다시 부르지 않는다', () async {
    final gate = _GateAdapter();
    dio.httpClientAdapter = gate;
    final sc = SelectedChild(dio: dio, store: store);
    var requests = 0;
    dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (o, h) {
          requests++;
          h.next(o);
        },
      ),
    );
    sc.ensureLoaded();
    sc.ensureLoaded();
    gate.release(
      _children([
        {'studentId': 1, 'name': '김하늘'},
      ]),
    );
    await pumpEventQueue();
    sc.ensureLoaded();
    await pumpEventQueue();
    expect(requests, 1);
    expect(sc.loaded, isTrue);
    sc.dispose();
  });

  group('selectIfMine — 알림의 studentId', () {
    void script() {
      adapter = FakeAdapter(
        replies: [
          FakeReply(
            statusCode: 200,
            body: _children([
              {'studentId': 1, 'name': '김하늘'},
              {'studentId': 2, 'name': '김바다'},
            ]),
          ),
        ],
      );
      dio.httpClientAdapter = adapter;
    }

    test('목록을 아직 안 받았으면 받고 나서 그 자녀를 고른다', () async {
      script();
      final s = build();
      await s.selectIfMine(2);
      expect(adapter.received.single.path, '/api/parent/children');
      expect(s.selectedStudentId, 2);
      expect(store.map.values, contains('2'));
    });

    test('내 자녀가 아니면 바꾸지도 저장하지도 않는다', () async {
      script();
      final s = build();
      await s.load();
      await s.selectIfMine(99);
      expect(s.selectedStudentId, 1);
      expect(store.map.values, isNot(contains('99')));
    });

    test('목록을 못 받으면 조용히 포기한다(화면 게이트가 오류를 띄운다)', () async {
      adapter = FakeAdapter(
        replies: const [
          FakeReply(
            statusCode: 500,
            body: {
              'success': false,
              'data': null,
              'error': {'code': 'INTERNAL', 'message': '서버 오류입니다.'},
            },
          ),
        ],
      );
      dio.httpClientAdapter = adapter;
      final s = build();
      await s.selectIfMine(2);
      expect(s.selectedStudentId, isNull);
      expect(s.loadError, '서버 오류입니다.');
    });
  });
}
