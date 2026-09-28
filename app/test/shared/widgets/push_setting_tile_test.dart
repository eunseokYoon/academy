import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/api/api_exception.dart';
import 'package:academy_app/shared/widgets/push_setting_tile.dart';

import '../../core/push/fake_push.dart';

Future<FakePushSettingRepository> _pump(
  WidgetTester tester, [
  FakePushSettingRepository? repo,
]) async {
  final r = repo ?? FakePushSettingRepository();
  final c = fakePushSetting(r);
  addTearDown(c.dispose);
  await tester.pumpWidget(
    MaterialApp(
      home: Scaffold(body: PushSettingTile(controller: c)),
    ),
  );
  await tester.pumpAndSettle();
  return r;
}

Switch _switch(WidgetTester tester) =>
    tester.widget<Switch>(find.byKey(const Key('push-setting-switch')));

void main() {
  testWidgets('서버 값을 보여준다', (tester) async {
    await _pump(tester, FakePushSettingRepository(enabled: false));
    expect(find.text('알림 받기'), findsOneWidget);
    expect(_switch(tester).value, isFalse);
  });

  testWidgets('끄면 서버에 보내고, 바꾸는 동안 옛 값으로 튀지 않는다', (tester) async {
    final repo = FakePushSettingRepository()..gate = Completer<void>();
    await _pump(tester, repo);
    expect(_switch(tester).value, isTrue);

    await tester.tap(find.byKey(const Key('push-setting-switch')));
    await tester.pump();
    expect(repo.sent, [false]);
    expect(_switch(tester).value, isFalse);
    // 바꾸는 중에는 다시 못 누른다.
    expect(_switch(tester).onChanged, isNull);

    repo.gate!.complete();
    await tester.pumpAndSettle();
    expect(_switch(tester).value, isFalse);
    expect(_switch(tester).onChanged, isNotNull);
  });

  testWidgets('바꾸기가 실패하면 서버 문구를 띄우고 원래 값으로 돌아간다', (tester) async {
    final repo = FakePushSettingRepository()
      ..changeError = const ApiException(
        code: 'INTERNAL',
        message: '서버 오류입니다.',
        statusCode: 500,
      );
    await _pump(tester, repo);

    await tester.tap(find.byKey(const Key('push-setting-switch')));
    await tester.pumpAndSettle();

    expect(find.text('서버 오류입니다.'), findsOneWidget);
    expect(_switch(tester).value, isTrue);
  });

  testWidgets('설정을 못 받으면 다시 시도가 뜨고, 누르면 받는다', (tester) async {
    final repo = FakePushSettingRepository()
      ..getError = const ApiException(
        code: 'INTERNAL',
        message: '서버 오류입니다.',
        statusCode: 500,
      );
    await _pump(tester, repo);
    expect(find.byKey(const Key('push-setting-switch')), findsNothing);
    expect(find.text('서버 오류입니다.'), findsOneWidget);

    repo.getError = null;
    await tester.tap(find.byKey(const Key('push-setting-retry')));
    await tester.pumpAndSettle();
    expect(_switch(tester).value, isTrue);
  });

  testWidgets('360px 에서 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await _pump(tester);
    expect(tester.takeException(), isNull);
  });
}
