import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/features/student/home/last_lesson_card.dart';
import 'package:academy_app/features/student/home/student_home_models.dart';

HomeLesson _lesson({
  String? title = '5과 정리',
  String? videoId = 'abc',
  String? embedUrl = 'https://www.youtube.com/embed/abc',
  int videoCount = 2,
  String? content = '본문 해석',
  String? homeworkNote = '단어 3과 외워오기',
  String lessonDate = '2026-09-14', // 월요일
}) => HomeLesson(
  lessonId: 5,
  lessonDate: lessonDate,
  title: title,
  videoId: videoId,
  embedUrl: embedUrl,
  videoCount: videoCount,
  content: content,
  homeworkNote: homeworkNote,
);

void main() {
  Widget host(HomeLesson l, {VoidCallback? onTap}) => MaterialApp(
    home: Scaffold(
      body: SingleChildScrollView(
        child: LastLessonCard(lesson: l, onTap: onTap ?? () {}),
      ),
    ),
  );

  testWidgets('날짜 eyebrow 가 09월 14일 (월) 형식이다', (tester) async {
    await tester.pumpWidget(host(_lesson()));
    expect(find.text('09월 14일 (월)'), findsOneWidget);
  });

  testWidgets('제목이 없으면 지난 수업 이다', (tester) async {
    await tester.pumpWidget(host(_lesson(title: null)));
    expect(find.text('지난 수업'), findsOneWidget);
    expect(find.text('09월 14일 (월)'), findsOneWidget);
  });

  testWidgets('embedUrl 이 null 이면 썸네일 영역이 통째로 없다', (tester) async {
    await tester.pumpWidget(host(_lesson(embedUrl: null, videoId: null)));
    expect(find.byKey(const Key('lesson-thumb')), findsNothing);
  });

  testWidgets('embedUrl 이 있으면 썸네일과 재생 버튼이 있다 (videoId 없어도 남는다)', (
    tester,
  ) async {
    await tester.pumpWidget(host(_lesson(videoId: null)));
    expect(find.byKey(const Key('lesson-thumb')), findsOneWidget);
    // Image.network 는 flutter_test 에서 항상 실패하므로 errorBuilder 가
    // 돈다 — 예외가 새지 않아야 한다. videoId 가 null 이면 애초에
    // Image 위젯 자체가 없다.
    expect(tester.takeException(), isNull);
  });

  testWidgets('embedUrl + videoId 가 있으면 이미지 로드 실패해도 예외가 새지 않는다', (
    tester,
  ) async {
    await tester.pumpWidget(host(_lesson()));
    await tester.pump();
    expect(find.byKey(const Key('lesson-thumb')), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('개수 배지는 videoCount > 1 일 때만 보인다', (tester) async {
    await tester.pumpWidget(host(_lesson(videoCount: 1)));
    expect(find.text('영상 1개'), findsNothing);

    await tester.pumpWidget(host(_lesson(videoCount: 2)));
    expect(find.text('영상 2개'), findsOneWidget);
  });

  testWidgets('썸네일을 누르면 onTap 이 불린다', (tester) async {
    var taps = 0;
    await tester.pumpWidget(host(_lesson(), onTap: () => taps++));
    await tester.tap(find.byKey(const Key('lesson-thumb')));
    await tester.pump();
    expect(taps, 1);
  });

  testWidgets('content 가 null 이면 수업 내용 라벨이 없다', (tester) async {
    await tester.pumpWidget(host(_lesson(content: null)));
    expect(find.text('수업 내용'), findsNothing);
    expect(find.text('수업 숙제'), findsOneWidget);
  });

  testWidgets('homeworkNote 가 null 이면 수업 숙제 라벨이 없다', (tester) async {
    await tester.pumpWidget(host(_lesson(homeworkNote: null)));
    expect(find.text('수업 숙제'), findsNothing);
    expect(find.text('수업 내용'), findsOneWidget);
  });

  testWidgets('줄바꿈이 살아 있고 잘리지 않는다', (tester) async {
    const content = '첫 줄\n둘째 줄\n셋째 줄';
    await tester.pumpWidget(host(_lesson(content: content)));
    final textWidget = tester.widget<Text>(find.text(content));
    expect(textWidget.maxLines, isNull);
  });

  testWidgets('수업 레포트 전체 보기를 누르면 onTap 이 불린다', (tester) async {
    var taps = 0;
    await tester.pumpWidget(host(_lesson(), onTap: () => taps++));
    // 카드가 기본 테스트 뷰포트보다 길어서(썸네일 + 두 블록 + 링크)
    // 스크롤해야 링크가 화면 안에 들어온다.
    await tester.ensureVisible(find.text('수업 레포트 전체 보기'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('수업 레포트 전체 보기'));
    await tester.pump();
    expect(taps, 1);
  });

  testWidgets('360px 에서 긴 제목·긴 본문이 넘치지 않는다', (tester) async {
    tester.view.physicalSize = const Size(360, 800);
    tester.view.devicePixelRatio = 1.0;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(
      host(
        _lesson(
          title: '아주아주아주아주아주아주아주아주아주아주아주아주 긴 제목입니다',
          content: '아주아주아주아주아주아주아주아주아주아주아주아주 긴 본문 해석입니다',
          homeworkNote: '아주아주아주아주아주아주아주아주아주아주 긴 숙제 메모입니다',
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
}
