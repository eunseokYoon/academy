import 'package:flutter/widgets.dart';

/// 홈이 **다시 보일 때** [onReappear] 를 부른다. 컨트롤러의 60초 규칙이
/// 뜻을 가지려면 이것이 있어야 한다 — `load()` 가 `initState` 에서만 불리면,
/// 셸이 `indexedStack` 이라 홈의 `State` 가 살아 있어서 탭을 옮겼다 돌아와도,
/// 앱을 내렸다 올려도 영영 다시 안 부른다.
///
/// 부르는 때는 둘이다.
///
/// 1. **다시 보일 때.** `TickerMode` 가 꺼졌다 켜지면. go_router 의
///    `StatefulShellRoute.indexedStack` 은 안 보이는 갈래를
///    `TickerMode(enabled: false)` 로 감싸고, `Navigator` 도 덮인 화면을
///    그렇게 한다 — 그래서 다른 탭에서 돌아올 때와 홈 갈래의 하위 화면
///    (`/student/clinics` 등)에서 돌아올 때가 같이 잡힌다.
/// 2. **앱이 포그라운드로 돌아올 때**(`AppLifecycleListener.onResume`). 단
///    지금 보이는 경우에만 — 안 보이면 돌아올 때 1 이 부른다.
///
/// 3. **알림이 왔을 때**([PushArrivals]). 지금 보이는 경우에만. 세션이 컨트롤러를
///    먼저 낡게 표시하므로(`markStale`) 보고 있던 화면이 60초와 상관없이 다시 받는다 —
///    숙제 탭에서 「채점됐어요」를 받았는데 화면이 그대로면 알림이 거짓말이 된다.
///
/// **60초 판단을 여기서 하지 마라.** [onReappear] 는 컨트롤러의 `load()` 를
/// 부르기만 하고, 60초 안이면 컨트롤러가 무시한다.
mixin ReappearReload<T extends StatefulWidget> on State<T> {
  bool? _active;
  late final AppLifecycleListener _lifecycle;

  /// 컨트롤러의 `load()` 를 부른다. `refresh()` 가 아니다.
  void onReappear();

  @override
  void initState() {
    super.initState();
    _lifecycle = AppLifecycleListener(
      onResume: () {
        if (_active ?? true) onReappear();
      },
    );
    PushArrivals.instance.addListener(_onPush);
  }

  void _onPush() {
    if (_active ?? true) onReappear();
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final active = TickerMode.valuesOf(context).enabled;
    final was = _active;
    _active = active;
    // 처음(was == null)은 initState 가 이미 불렀다.
    if (was == false && active) onReappear();
  }

  @override
  void dispose() {
    PushArrivals.instance.removeListener(_onPush);
    _lifecycle.dispose();
    super.dispose();
  }
}

/// 알림이 도착했다는 신호. 앱이 떠 있을 때 받았거나, 알림을 눌러 들어왔을 때 울린다.
///
/// 앱 수명 하나뿐이다 — 사람에게 딸린 상태가 없고(데이터는 세션의 컨트롤러에 있다),
/// 화면마다 넘기면 [ReappearReload] 를 쓰는 스무 화면의 생성자가 전부 바뀐다.
/// **울리기 전에 세션의 컨트롤러를 `markStale` 해라** — 안 하면 60초 안에서는
/// [ReappearReload.onReappear] 가 불려도 컨트롤러가 무시한다.
class PushArrivals extends ChangeNotifier {
  PushArrivals._();

  static final instance = PushArrivals._();

  void arrived() => notifyListeners();
}
