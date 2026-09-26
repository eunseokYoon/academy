import 'dart:async';

import 'package:flutter/foundation.dart';

import '../../../core/api/api_exception.dart';
import 'student_home_models.dart';
import 'student_home_repository.dart';

enum HomeStatus { idle, loading, ready, error }

/// 학생 홈의 상태. **B2~B4 의 화면들이 이 모양을 복제한다.**
///
/// **60초 규칙**: 탭이 다시 전면에 올 때 [load] 를 불러도 마지막 성공이
/// 60초 이내면 건너뛴다. 상태 관리 라이브러리 없이 낡음을 한 화면 수명
/// 안에 가두는 장치다 — 홈에 돌아올 때 스피너가 번쩍이지 않는다.
///
/// [refresh] 는 60초를 무시한다. 당겨서 새로고침은 사용자가 명시적으로
/// 요구한 것이다.
///
/// **새로고침 실패는 기존 데이터를 유지한다.** 당겨서 새로고침하다 실패했는데
/// 화면이 비면 손해다. 이때 [status] 는 `ready` 로 남고 [error] 만 채워진다.
///
/// **서버 문구를 그대로 쓴다.** [ApiException.message] 를 감싸거나
/// 접두어를 붙이지 않는다.
///
/// **진행 중인 요청이 있으면 새로 부르지 않고 합류한다.** [load] 와 [refresh]
/// 가 겹치는 경로가 둘 있다 — 앱이 뜨자마자 나간 첫 요청의 응답 전에 탭을
/// 옮겼다 돌아오는 경우, 느린 망에서 첫 로딩 중에 당겨서 새로고침하는
/// 경우. 두 응답이 경쟁하면 나중에 도착한 쪽이 이기는데 그게 더 오래된
/// 요청일 수 있다 — 그래서 이미 나간 요청이 있으면 새로 부르지 않고
/// 그 요청을 그대로 기다린다. 이 가드는 요청을 실제로 내보내는 [_run] 을
/// 부르기 **전에** 무장한다 — [_run] 은 `async` 라 호출 시점에 첫
/// `await` 까지(여기선 첫 [notifyListeners] 호출까지) 동기로 실행되므로,
/// 그 동기 구간에서 리스너가 [load]/[refresh] 를 다시 부르면 가드가 아직
/// 없는 채로 재진입해 요청이 두 번 나갈 수 있다.
///
/// **dispose 이후에는 아무것도 쓰지 않는다.** 화면이 응답을 기다리는 중에
/// 닫히면(느린 망에서 뒤로 가기) 응답은 dispose 뒤에 도착한다. 그 시점에
/// [notifyListeners] 를 부르면 Flutter 가 "used after being disposed"로
/// 던지고(디버그·위젯 테스트에서만 — `assert` 안에 있어 release 에서는
/// 안 던진다. 그래도 개발 중·13개 화면의 테스트에서 매번 터지는 건 나쁘고,
/// 죽은 컨트롤러에 상태를 쓰는 것 자체가 의미가 없다), 어느 쪽이든 이
/// 컨트롤러는 [dispose] 이후로는 상태를 쓰지도 알리지도 않는다.
class StudentHomeController extends ChangeNotifier {
  StudentHomeController({
    required this._repository,
    this._staleAfter = const Duration(seconds: 60),
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  final StudentHomeRepository _repository;
  final Duration _staleAfter;
  final DateTime Function() _now;

  HomeStatus _status = HomeStatus.idle;
  StudentHome? _data;
  String? _error;
  DateTime? _loadedAt;
  Future<void>? _inFlight;
  bool _disposed = false;

  HomeStatus get status => _status;
  StudentHome? get data => _data;
  String? get error => _error;

  /// 탭 재진입용. 마지막 성공이 60초 이내면 아무것도 하지 않는다.
  Future<void> load() {
    final loadedAt = _loadedAt;
    if (loadedAt != null && _now().difference(loadedAt) < _staleAfter) {
      return Future<void>.value();
    }
    return _fetch();
  }

  /// 당겨서 새로고침. 60초 규칙을 무시하고 항상 새로 부른다.
  Future<void> refresh() => _fetch();

  /// 이미 나간 요청이 있으면 새로 부르지 않고 그것을 기다린다 — 같은 응답을
  /// 두 번 받을 이유가 없고, 겹쳐 쏘면 나중에 끝난 쪽이 이겨 더 오래된
  /// 응답으로 상태를 덮어쓸 수 있다.
  Future<void> _fetch() {
    // 로그아웃으로 버려진 세션의 컨트롤러다. 요청을 내보내지 않는다.
    if (_disposed) return Future<void>.value();
    final existing = _inFlight;
    if (existing != null) return existing;
    final completer = Completer<void>();
    // 가드를 [_run] 을 부르기 **전에** 무장한다. [_run] 은 async 라 호출한
    // 순간 첫 await(첫 notifyListeners 호출)까지 동기로 실행된다 — 그
    // 대입을 `_run()` 호출 뒤로 미루면, 그 동기 구간에서 리스너가
    // load()/refresh() 를 다시 불렀을 때 _inFlight 가 아직 null이라
    // 가드를 그냥 통과해 요청이 두 번 나간다.
    _inFlight = completer.future;
    _run().whenComplete(() {
      // 성공이든 실패든 반드시 비운다 — 안 비우면 다음 호출이 영원히 이
      // future 를 기다리게 되어 화면이 옛 데이터·옛 오류에 묶인다.
      _inFlight = null;
      completer.complete();
    });
    return completer.future;
  }

  Future<void> _run() async {
    // 유지할 데이터가 있었는지를 실행 시작 시점에 고정한다 — 실패 시
    // status 를 error 로 떨어뜨릴지, ready 로 남겨 기존 데이터를 지킬지의
    // 기준이다.
    final keepDataOnError = _data != null;
    if (_data == null) _status = HomeStatus.loading;
    _error = null;
    _notify();
    try {
      final home = await _repository.fetch();
      // 요청이 나가 있는 동안 화면이 dispose 됐으면(느린 망에서 뒤로
      // 가기) 여기서 멈춘다 — 아무도 듣고 있지 않은 컨트롤러에 상태를
      // 쓰는 건 의미가 없고, 아래 _notify() 가 알리려 해도 dispose 뒤의
      // notifyListeners 호출은 Flutter 가 막는다.
      if (_disposed) return;
      _data = home;
      _loadedAt = _now();
      _error = null;
      _status = HomeStatus.ready;
    } on ApiException catch (e) {
      if (_disposed) return;
      // 서버 문구를 그대로 쓴다 — 앱에서 감싸거나 접두어를 붙이지 마라.
      _error = e.message;
      if (!keepDataOnError) _status = HomeStatus.error;
    } catch (_) {
      if (_disposed) return;
      _error = '연결할 수 없습니다. 잠시 후 다시 시도해 주세요.';
      if (!keepDataOnError) _status = HomeStatus.error;
    }
    _notify();
  }

  /// dispose 뒤에는 [notifyListeners] 를 부르지 않는다 — Flutter 가
  /// "used after being disposed"로 던지는 걸(디버그·위젯 테스트에서만이라도)
  /// 막는다.
  void _notify() {
    if (_disposed) return;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
