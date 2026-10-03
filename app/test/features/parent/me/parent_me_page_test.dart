import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../../core/push/fake_push.dart';

import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/features/parent/me/parent_me_data.dart';
import 'package:academy_app/features/parent/me/parent_me_page.dart';
import 'package:academy_app/features/parent/selected_child.dart';

class _Repo implements ParentMeRepository {
  _Repo({this.meError});

  Object? meError;
  Object? changeError;
  String phone = '01011112222';
  final List<String> sent = [];
  int meCalls = 0;

  ParentMe _me() => ParentMe(
    name: '김하늘 학부모',
    phone: phone,
    children: const [
      Child(studentId: 1, name: '김하늘'),
      Child(studentId: 2, name: '김바다'),
    ],
  );

  @override
  Future<ParentMe> me() async {
    meCalls++;
    final e = meError;
    if (e != null) throw e;
    return _me();
  }

  @override
  Future<ParentMe> changePhone(String value) async {
    sent.add(value);
    final e = changeError;
    if (e != null) throw e;
    phone = value.replaceAll('-', '');
    return _me();
  }
}

Future<int Function()> _pump(WidgetTester tester, _Repo repo) async {
  tester.view.physicalSize = const Size(360 * 3, 1600 * 3);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  final c = ParentMeController(repository: repo);
  addTearDown(c.dispose);
  final push = fakePushSetting();
  addTearDown(push.dispose);
  var logouts = 0;
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(
        body: ParentMePage(
          controller: c,
          pushSetting: push,
          onLogout: () async => logouts++,
          onDeleteAccount: (_) async {},
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  return () => logouts;
}

EditableText _field(WidgetTester tester) =>
    tester.widget<EditableText>(find.byType(EditableText));

void main() {
  testWidgets('이름·자녀·번호를 보여준다', (tester) async {
    await _pump(tester, _Repo());
    expect(find.text('김하늘 학부모 님'), findsOneWidget);
    expect(find.text('김하늘'), findsOneWidget);
    expect(find.text('김바다'), findsOneWidget);
    expect(_field(tester).controller.text, '010-1111-2222');
    expect(tester.takeException(), isNull);
  });

  testWidgets('연락처를 바꾸면 성공 문구가 뜨고, 고치기 시작하면 내려간다', (tester) async {
    final repo = _Repo();
    await _pump(tester, repo);
    await tester.enterText(find.byType(EditableText), '01033334444');
    expect(_field(tester).controller.text, '010-3333-4444');
    await tester.tap(find.text('변경하기'));
    await tester.pumpAndSettle();

    expect(repo.sent, ['010-3333-4444']);
    expect(find.byKey(const Key('phone-changed')), findsOneWidget);
    expect(_field(tester).controller.text, '010-3333-4444');

    await tester.enterText(find.byType(EditableText), '0103333444');
    await tester.pump();
    expect(find.byKey(const Key('phone-changed')), findsNothing);
  });

  testWidgets('서버가 거절하면 그 문구를 그대로 보여준다', (tester) async {
    final repo = _Repo()
      ..changeError = const ApiException(
        code: 'DUPLICATE_PHONE',
        message: '이미 사용 중인 번호입니다.',
      );
    await _pump(tester, repo);
    await tester.enterText(find.byType(EditableText), '01033334444');
    await tester.tap(find.text('변경하기'));
    await tester.pumpAndSettle();
    expect(find.text('이미 사용 중인 번호입니다.'), findsOneWidget);
    expect(find.byKey(const Key('phone-changed')), findsNothing);
  });

  testWidgets('내 정보를 못 받아도 로그아웃은 있다 (14-7)', (tester) async {
    final logouts = await _pump(
      tester,
      _Repo(
        meError: const ApiException(code: 'INTERNAL', message: '서버 오류입니다.'),
      ),
    );
    expect(find.text('서버 오류입니다.'), findsOneWidget);
    await tester.tap(find.byKey(const Key('parent-logout')));
    await tester.pump();
    expect(logouts(), 1);
  });

  testWidgets('개인정보처리방침 링크가 로그아웃 옆에 있다', (tester) async {
    await _pump(tester, _Repo());
    expect(find.byKey(const Key('privacy-link')), findsOneWidget);
    expect(find.byKey(const Key('parent-logout')), findsOneWidget);
  });
}
