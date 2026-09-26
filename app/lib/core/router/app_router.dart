import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../shared/widgets/full_screen_loader.dart';
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
        builder: (_, _) => const Scaffold(body: FullScreenLoader()),
      ),
      ...routes,
    ],
  );
}
