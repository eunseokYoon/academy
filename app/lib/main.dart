import 'dart:async';
import 'dart:io';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'core/api/dio_client.dart';
import 'core/api/password_gate_interceptor.dart';
import 'core/api/refresh_interceptor.dart';
import 'core/auth/auth_controller.dart';
import 'core/auth/auth_repository.dart';
import 'core/router/app_router.dart';
import 'core/storage/cookie_store.dart';
import 'core/storage/key_value_store.dart';
import 'core/storage/token_store.dart';
import 'core/theme/app_theme.dart';
import 'features/auth/login_page.dart';
import 'features/auth/password_change_page.dart';
import 'features/auth/signup_page.dart';
import 'features/auth/teacher_notice_page.dart';
import 'features/parent/parent_shell.dart';
import 'features/student/student_shell.dart';

/// 운영은 `--dart-define=API_BASE_URL=https://...`로 넣는다.
///
/// 로컬 기본값이 플랫폼마다 다르다 — **Android 에뮬레이터는 호스트를
/// `10.0.2.2`로 본다.** `localhost`를 쓰면 에뮬레이터 자기 자신을 가리켜
/// 연결이 조용히 실패한다. iOS 시뮬레이터는 호스트와 같은 `localhost`다.
String resolveBaseUrl() {
  const fromDefine = String.fromEnvironment('API_BASE_URL');
  final value = fromDefine.isNotEmpty
      ? fromDefine
      : (Platform.isAndroid ? 'http://10.0.2.2:8080' : 'http://localhost:8080');
  // 끝의 '/'를 남기면 Uri.parse('$baseUrl/api/auth')가 '//api/auth'가 되어
  // 쿠키 path가 요청 path와 영영 안 맞는다 — 리프레시 쿠키가 조용히 안 실려
  // 나가서, 액세스 토큰이 만료될 때마다 전원 재로그인하게 된다.
  return value.replaceAll(RegExp(r'/+$'), '');
}

void main() {
  final baseUrl = resolveBaseUrl();

  final secure = SecureKeyValueStore();
  final tokens = TokenStore(secure);
  final jar = CookieJar();
  final cookies = CookieStore(secure, Uri.parse('$baseUrl/api/auth'));

  // 리프레시와 재시도 전용 Dio. RefreshInterceptor가 붙지 않아야 한다 —
  // 붙으면 리프레시가 401일 때 재귀한다. 쿠키 자는 공유한다.
  // 타임아웃은 DioClient.build와 반드시 같아야 한다 — RefreshInterceptor가
  // QueuedInterceptor라 리프레시 하나가 멈추면 뒤에 줄 선 401 복구가 전부
  // 막힌다. dio의 기본값은 타임아웃이 없다(null)라서 여기서 빠뜨리면
  // 영원히 멈출 수 있다.
  final plain = Dio(
    BaseOptions(
      baseUrl: baseUrl,
      connectTimeout: DioClient.connectTimeout,
      receiveTimeout: DioClient.receiveTimeout,
    ),
  )..interceptors.add(CookieManager(jar));

  // 순환을 끊는다. 클로저가 auth를 나중에 읽는다.
  late final AuthController auth;

  final dio = DioClient.build(
    baseUrl: baseUrl,
    tokens: tokens,
    jar: jar,
    extra: [
      RefreshInterceptor(
        plain: plain,
        tokens: tokens,
        cookies: cookies,
        jar: jar,
        onSessionExpired: () => auth.onSessionExpired(),
      ),
      PasswordGateInterceptor(() => auth.markPasswordChangeRequired()),
    ],
  );

  auth = AuthController(
    repository: AuthRepository(dio),
    tokens: tokens,
    cookies: cookies,
    jar: jar,
  );

  runApp(AcademyApp(auth: auth));
  // 첫 프레임을 막지 않는다. splash가 떠 있는 동안 복원한다.
  unawaited(auth.bootstrap());
}

class AcademyApp extends StatefulWidget {
  const AcademyApp({super.key, required this.auth});

  final AuthController auth;

  @override
  State<AcademyApp> createState() => _AcademyAppState();
}

class _AcademyAppState extends State<AcademyApp> {
  late final GoRouter _router = buildRouter(
    auth: widget.auth,
    routes: [
      GoRoute(
        path: AppRoutes.login,
        builder: (_, _) => LoginPage(onLogin: widget.auth.login),
      ),
      GoRoute(
        path: AppRoutes.signup,
        builder: (_, _) => SignupPage(onSignup: widget.auth.signup),
      ),
      GoRoute(
        path: AppRoutes.password,
        builder: (_, _) => PasswordChangePage(
          onChange: widget.auth.changePassword,
          onLogout: widget.auth.logout,
        ),
      ),
      GoRoute(
        path: AppRoutes.student,
        builder: (_, _) => StudentShell(
          name: widget.auth.snapshot.name ?? '',
          onLogout: widget.auth.logout,
        ),
      ),
      GoRoute(
        path: AppRoutes.parent,
        builder: (_, _) => ParentShell(
          name: widget.auth.snapshot.name ?? '',
          onLogout: widget.auth.logout,
        ),
      ),
      GoRoute(
        path: AppRoutes.teacherNotice,
        builder: (_, _) => TeacherNoticePage(onLogout: widget.auth.logout),
      ),
    ],
  );

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: '학원',
      theme: AppTheme.light(),
      routerConfig: _router,
    );
  }
}
