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

  test('reset 은 목록과 선택을 비우고, 그 뒤 도착한 앞 사람 목록은 버린다', () async {
    final gate = _GateAdapter();
    dio.httpClientAdapter = gate;
    final sc = build();
    final pending = sc.load();
    sc.reset();
    expect(sc.children, isEmpty);
    expect(sc.selectedStudentId, isNull);
    expect(sc.loading, isFalse);
    gate.release(
      _children([
        {'studentId': 1, 'name': '김하늘'},
      ]),
    );
    await pending;
    expect(sc.children, isEmpty);
    expect(sc.selectedStudentId, isNull);
  });
}
