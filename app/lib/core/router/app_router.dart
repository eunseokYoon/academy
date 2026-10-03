import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../shared/widgets/full_screen_loader.dart';
import '../../shared/widgets/home_layout.dart';
import '../auth/auth_controller.dart';
import 'auth_redirect.dart';

export 'auth_redirect.dart' show AppRoutes;

/// `refreshListenable`에 `AuthController`를 그대로 넘긴다 —
/// `ChangeNotifier`가 `Listenable`이라 어댑터가 필요 없다. 이것이 A단계에
/// riverpod을 넣지 않은 이유다.
GoRouter buildRouter({
  required AuthController auth,
  required List<RouteBase> routes,
}) {
  return GoRouter(
    initialLocation: AppRoutes.splash,
    refreshListenable: auth,
    redirect: (context, state) =>
        redirectFor(auth.snapshot, state.matchedLocation),
    routes: [
      GoRoute(
        path: AppRoutes.splash,
        // 부팅 복원이 연결 문제로 실패하면 로그인 정보를 남긴 채 여기서 다시 시도한다
        builder: (_, _) => Scaffold(
          body: ListenableBuilder(
            listenable: auth,
            builder: (_, _) => auth.bootError == null
                ? const FullScreenLoader()
                : HomeErrorView(message: auth.bootError, onRetry: auth.bootstrap),
          ),
        ),
      ),
      ...routes,
    ],
  );
}
