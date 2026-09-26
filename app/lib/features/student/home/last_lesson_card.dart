import 'package:flutter/material.dart';
import 'package:path_drawing/path_drawing.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/theme/app_theme.dart';
import '../../../shared/widgets/app_card.dart';
import 'student_home_models.dart';

/// 요일 한 글자. `DateTime.weekday`(1=월~7=일)를 그대로 인덱스로 쓴다 — 웹
/// `shared/date.ts`의 `DAY_LABELS`(ISO-8601, 1=월)와 같은 기준이다.
const List<String> _dayLabels = ['월', '화', '수', '목', '금', '토', '일'];

/// `2026-09-14` → `09월 14일 (월)`. 웹 `LastLessonCard.tsx`의
/// `lessonDate.slice(5).replace("-","월 ")+"일 ("+dayLabel(lessonDate)+")"`과 같다.
///
/// **`DateTime.now()`를 쓰지 마라.** `lessonDate` 문자열만으로 결정되는 값이라
/// `DateTime.parse`가 결정적이다 — 금지된 것은 기기 시계로 D-day를 다시 세는 것이다.
/// `lessonDate`는 서버가 늘 `YYYY-MM-DD` 형식으로 내려준다(모델 계약).
String _eyebrowLabel(String lessonDate) {
  final datePart = lessonDate.substring(5).replaceFirst('-', '월 ');
  final weekday = DateTime.parse(lessonDate).weekday;
  return '$datePart일 (${_dayLabels[weekday - 1]})';
}

/// 학생 홈(S-1)의 「지난 수업」 카드. **가장 최근에 뭔가 적힌 지난 수업**이다.
///
/// 웹 정본은 `frontend/src/routes/student/LastLessonCard.tsx`다. 이 파일의 주석이
/// 그 파일의 설계 근거를 그대로 옮긴 것이다.
///
/// **영상을 여기서 재생하지 않는다.** iframe·플레이어를 넣지 마라 — 홈에 심으면
/// 첫 화면이 그만큼 무거워지고, 재생은 이미 수업 상세(S-5)가 하는 일이다. 썸네일은
/// YouTube가 `videoId`만으로 주는 정적 이미지 한 장이라 그 부담이 없다. 누르면
/// 수업 화면으로 갈 뿐이다.
///
/// **영상과 글을 가로로 나란히 두지 않는다.** 360px에서 영상이 좁아지면 재생
/// 버튼인지 알아볼 수 없다 — 영상은 폭을 다 쓰고 글은 그 아래다.
///
/// `content`·`homeworkNote`는 각각 null일 수 있다. **없는 칸은 통째로 숨긴다** —
/// 빈 라벨만 남으면 선생님이 안 쓴 것인지 화면이 깨진 것인지 구분되지 않는다.
///
/// 카드 전체가 아니라 **썸네일과 맨 아래 링크 두 곳만** 누를 수 있다 — 웹도
/// `SectionHead` 없이 이 둘만 `<Link>`다.
class LastLessonCard extends StatelessWidget {
  const LastLessonCard({super.key, required this.lesson, required this.onTap});

  final HomeLesson lesson;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final embedUrl = lesson.embedUrl;
    final content = lesson.content;
    final homeworkNote = lesson.homeworkNote;

    final bodyChildren = <Widget>[
      if (content != null) _Block(label: '수업 내용', text: content),
      if (homeworkNote != null) _Block(label: '수업 숙제', text: homeworkNote),
      _ReportLink(onTap: onTap),
    ];

    return AppCard(
      padding: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  _eyebrowLabel(lesson.lessonDate),
                  style: const TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    letterSpacing: 1.1,
                    color: AppColors.slate400,
                    fontFeatures: kTabularFigures,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  lesson.title ?? '지난 수업',
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.16,
                    color: AppColors.brand900,
                  ),
                ),
              ],
            ),
          ),
          // embedUrl이 null이면 영상이 등록되지 않은 수업이다 — 영역을 통째로
          // 숨긴다. 재생목록 링크는 videoId가 없어 이미지는 못 그리지만, 이
          // 블록(남색 바탕 + 재생 버튼)은 그래도 남는다.
          if (embedUrl != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: _Thumbnail(
                videoId: lesson.videoId,
                videoCount: lesson.videoCount,
                onTap: onTap,
              ),
            ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: _withGaps(bodyChildren, 12),
          ),
        ],
      ),
    );
  }
}

/// 리스트 사이에 `gap` 높이의 빈 칸을 끼운다. 웹의 `space-y-3`와 같다 —
/// 어느 블록이 숨겨지든(둘 다 null이어도) 남은 블록 사이 간격만 12로 맞는다.
Widget _withGaps(List<Widget> children, double gap) {
  final result = <Widget>[];
  for (var i = 0; i < children.length; i++) {
    if (i > 0) result.add(SizedBox(height: gap));
    result.add(children[i]);
  }
  return Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: result,
  );
}

/// 16:9 YouTube 썸네일 + 재생 버튼. 정적 이미지 한 장이고 재생은 없다.
class _Thumbnail extends StatelessWidget {
  const _Thumbnail({
    required this.videoId,
    required this.videoCount,
    required this.onTap,
  });

  final String? videoId;
  final int videoCount;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      key: const Key('lesson-thumb'),
      borderRadius: BorderRadius.circular(AppRadii.xl),
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: Material(
          color: AppColors.brand950,
          type: MaterialType.transparency,
          // 웹의 `<span className="sr-only">수업영상 보기</span>` — 스크린
          // 리더에게 이 탭 영역이 뭔지 말해 준다. `InkWell` 자체는 라벨이 없다.
          child: Semantics(
            container: true,
            button: true,
            label: '수업영상 보기',
            child: InkWell(
              onTap: onTap,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  // videoId가 없으면(재생목록 링크) 이미지를 건너뛴다 — 남색
                  // 바탕이 그대로 남는다.
                  if (videoId != null)
                    Image.network(
                      'https://i.ytimg.com/vi/$videoId/hqdefault.jpg',
                      fit: BoxFit.cover,
                      // 영상이 지워졌거나 비공개면 404다. 그때는 아무것도
                      // 그리지 않아 남색 블록이 그대로 남는다 — 깨진 이미지
                      // 아이콘이 뜨는 것보다 낫다. flutter_test에서는
                      // Image.network가 항상 실패하므로 이 경로가 늘 돈다.
                      errorBuilder: (context, error, stackTrace) =>
                          const SizedBox.shrink(),
                    ),
                  // 썸네일 위 재생 버튼이 묻히지 않게 살짝 어둡게 깐다.
                  Container(color: AppColors.brand950.withValues(alpha: 0.35)),
                  Center(
                    child: Container(
                      key: const Key('lesson-play-button'),
                      width: 56,
                      height: 56,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        color: Colors.black.withValues(alpha: 0.45),
                        border: Border.all(
                          color: Colors.white.withValues(alpha: 0.4),
                        ),
                      ),
                      child: Center(
                        // 광학적으로 가운데 오게 살짝 오른쪽으로 민다.
                        child: Transform.translate(
                          offset: const Offset(2, 0),
                          child: const CustomPaint(
                            size: Size.square(24),
                            painter: _PlayTrianglePainter(),
                          ),
                        ),
                      ),
                    ),
                  ),
                  // 여러 개면 몇 개인지 알려 준다. 상세에서 골라 볼 수 있다는
                  // 신호다. 1개면 배지가 없다 — 재생 버튼 하나로 충분하다.
                  if (videoCount > 1)
                    Positioned(
                      bottom: 8,
                      right: 8,
                      child: Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 6,
                          vertical: 2,
                        ),
                        decoration: BoxDecoration(
                          color: Colors.black.withValues(alpha: 0.65),
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Text(
                          '영상 $videoCount개',
                          style: const TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: Colors.white,
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 재생 삼각형. 웹 `LastLessonCard.tsx`의
/// `<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>`를 그대로 옮긴다.
///
/// `AppIcon`(Task 1)은 선으로 그리는 아이콘이라 채운 삼각형이 없다 — 여기서만
/// 예외로 private `CustomPainter`를 둔다. `Icons.play_arrow`로 대체하지 마라.
class _PlayTrianglePainter extends CustomPainter {
  const _PlayTrianglePainter();

  @override
  void paint(Canvas canvas, Size size) {
    // 웹 viewBox가 0 0 24 24다. 그 좌표계로 그린 뒤 요청된 크기로 맞춘다.
    final s = size.width / 24;
    canvas.save();
    canvas.scale(s);
    canvas.drawPath(
      parseSvgPathData('M8 5v14l11-7z'),
      Paint()
        ..style = PaintingStyle.fill
        ..color = Colors.white,
    );
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _PlayTrianglePainter oldDelegate) => false;
}

/// 라벨 + 본문 한 덩어리. 줄바꿈은 선생님이 쓴 그대로 살린다.
///
/// **`maxLines`를 주지 마라.** 웹은 `whitespace-pre-wrap`으로 전문을 보여준다 —
/// Flutter `Text`는 `maxLines`가 없으면 그게 기본 동작이다.
class _Block extends StatelessWidget {
  const _Block({required this.label, required this.text});

  final String label;
  final String text;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: const TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w700,
            letterSpacing: -0.13,
            color: AppColors.brand900,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          text,
          style: const TextStyle(
            fontSize: 14,
            color: AppColors.slate700,
            height: 1.625,
          ),
        ),
      ],
    );
  }
}

/// 「수업 레포트 전체 보기」. 웹의 `block text-xs font-medium text-brand-600
/// underline`.
///
/// **`Align`로 탭 영역을 명시적으로 왼쪽 정렬·전체 폭으로 만든다.** 웹의
/// `block`이 그 뜻이다 — 지금은 우연히 부모(`_withGaps`)의
/// `CrossAxisAlignment.stretch` 덕에 전체 폭이 눌리지만, 그건 `_Block`의
/// 줄바꿈을 위한 값이라 이 위젯과 상관없다. 그 값이 바뀌면 여기 탭 영역이
/// 글자 폭으로 조용히 줄어든다 — 그래서 의도를 이 위젯 안에 직접 둔다.
class _ReportLink extends StatelessWidget {
  const _ReportLink({required this.onTap});

  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      type: MaterialType.transparency,
      child: InkWell(
        onTap: onTap,
        child: const Align(
          alignment: Alignment.centerLeft,
          child: Text(
            '수업 레포트 전체 보기',
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: AppColors.brand600,
              decoration: TextDecoration.underline,
            ),
          ),
        ),
      ),
    );
  }
}
