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
    _lifecycle.dispose();
    super.dispose();
  }
}
